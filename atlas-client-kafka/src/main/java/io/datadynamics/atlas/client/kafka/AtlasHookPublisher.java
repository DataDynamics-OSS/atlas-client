package io.datadynamics.atlas.client.kafka;

import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.kafka.message.EntityCreateRequest;
import io.datadynamics.atlas.client.kafka.message.EntityDeleteRequest;
import io.datadynamics.atlas.client.kafka.message.EntityFullUpdateRequest;
import io.datadynamics.atlas.client.kafka.message.EntityPartialUpdateRequest;
import io.datadynamics.atlas.client.kafka.message.HookMessage;
import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends entity changes to Atlas through the {@code ATLAS_HOOK} Kafka topic, the way Atlas hooks do.
 * <pre>{@code
 * try (AtlasHookPublisher hook = AtlasHookPublisher.builder()
 *         .bootstrapServers("kafka:9092")
 *         .user("etl")
 *         .build()) {
 *     hook.createOrUpdate(table);
 *     hook.partialUpdateByQualifiedName("hive_table", "db.orders@prod", patch);
 *     hook.deleteByQualifiedName("hive_table", "db.tmp@prod");
 * }
 * }</pre>
 * Unlike REST calls, Kafka delivery only means Atlas <em>will</em> process the change: it happens
 * asynchronously, in order, and failures (e.g. an unknown type) only appear in the Atlas server log.
 * The synchronous methods wait for Kafka to acknowledge the message; {@link #sendAsync(HookMessage)}
 * does not.
 * <p>
 * Lineage built with {@code atlas-client-lineage} can be sent here too:
 * {@code hook.createOrUpdate(lineage.toEntities(processes))}.
 */
public final class AtlasHookPublisher implements AutoCloseable {

    public static final String DEFAULT_TOPIC = "ATLAS_HOOK";

    private static final Logger LOG = LoggerFactory.getLogger(AtlasHookPublisher.class);

    private final Producer<String, String> producer;
    private final boolean ownsProducer;
    private final String topic;
    private final String user;
    private final NotificationCodec codec;
    private final Duration sendTimeout;

    private AtlasHookPublisher(Builder builder, Producer<String, String> producer, boolean ownsProducer) {
        this.producer = producer;
        this.ownsProducer = ownsProducer;
        this.topic = builder.topic;
        this.user = builder.user;
        this.codec = new NotificationCodec(builder.maxMessageBytes, builder.compression);
        this.sendTimeout = builder.sendTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------------- entity operations

    /** {@code ENTITY_CREATE_V2}: creates the entities, or updates those that exist. */
    public PublishResult createOrUpdate(AtlasEntity... entities) {
        return createOrUpdate(Arrays.asList(entities));
    }

    public PublishResult createOrUpdate(Collection<AtlasEntity> entities) {
        return createOrUpdate(new AtlasEntitiesWithExtInfo(entities));
    }

    public PublishResult createOrUpdate(AtlasEntitiesWithExtInfo entities) {
        return send(new EntityCreateRequest(entities));
    }

    /** {@code ENTITY_FULL_UPDATE_V2}: replaces the entities with the given definitions. */
    public PublishResult fullUpdate(AtlasEntitiesWithExtInfo entities) {
        return send(new EntityFullUpdateRequest(entities));
    }

    /** {@code ENTITY_PARTIAL_UPDATE_V2}: updates only the attributes set in {@code patch}. */
    public PublishResult partialUpdate(AtlasObjectId entityId, AtlasEntity patch) {
        return send(new EntityPartialUpdateRequest(entityId, patch));
    }

    public PublishResult partialUpdateByQualifiedName(String typeName, String qualifiedName, AtlasEntity patch) {
        if (patch.getTypeName() == null) {
            patch.setTypeName(typeName);
        }
        return partialUpdate(AtlasObjectId.byQualifiedName(typeName, qualifiedName), patch);
    }

    /** {@code ENTITY_DELETE_V2}: deletes entities identified by type name and unique attributes. */
    public PublishResult delete(AtlasObjectId... entities) {
        return delete(Arrays.asList(entities));
    }

    public PublishResult delete(Collection<AtlasObjectId> entities) {
        return send(new EntityDeleteRequest(entities));
    }

    public PublishResult deleteByQualifiedName(String typeName, String qualifiedName) {
        return delete(AtlasObjectId.byQualifiedName(typeName, qualifiedName));
    }

    // ---------------------------------------------------------------- sending

    /**
     * Sends the message and waits until Kafka has acknowledged it.
     *
     * @throws IllegalArgumentException if Atlas would not be able to process the message
     * @throws AtlasClientException     if Kafka does not acknowledge it within the send timeout
     */
    public PublishResult send(HookMessage message) {
        CompletableFuture<PublishResult> future = sendAsync(message);
        try {
            return future.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AtlasClientException("Interrupted while sending " + message.getType(), e);
        } catch (ExecutionException e) {
            throw new AtlasClientException("Failed to send " + message.getType() + " to " + topic
                    + ": " + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            throw new AtlasClientException("Kafka did not acknowledge " + message.getType() + " within " + sendTimeout, e);
        }
    }

    /**
     * Sends the message without waiting. The future completes once Kafka has acknowledged every
     * part of it.
     *
     * @throws IllegalArgumentException if Atlas would not be able to process the message
     */
    public CompletableFuture<PublishResult> sendAsync(HookMessage message) {
        Objects.requireNonNull(message, "message");
        message.validate();
        if (message.getUser() == null) {
            message.setUser(user);
        }
        List<String> parts = codec.encode(message, message.getUser());
        // Parts of a split message must stay in order, so they share a key (and thus a partition).
        String key = parts.size() > 1 ? UUID.randomUUID().toString() : null;
        if (parts.size() > 1) {
            LOG.debug("{} split into {} Kafka messages", message.getType(), parts.size());
        }

        List<CompletableFuture<RecordMetadata>> sent = new ArrayList<>(parts.size());
        for (String part : parts) {
            CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
            producer.send(new ProducerRecord<>(topic, key, part), (metadata, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                } else {
                    future.complete(metadata);
                }
            });
            sent.add(future);
        }
        return CompletableFuture.allOf(sent.toArray(new CompletableFuture<?>[0])).thenApply(ignored -> {
            RecordMetadata last = sent.get(sent.size() - 1).join();
            return new PublishResult(message.getType(), last.topic(), last.partition(), last.offset(), parts.size());
        });
    }

    /** Waits until every message sent so far has been acknowledged. */
    public void flush() {
        producer.flush();
    }

    @Override
    public void close() {
        if (ownsProducer) {
            producer.close();
        } else {
            producer.flush();
        }
    }

    // ---------------------------------------------------------------- builder

    public static final class Builder {

        private final Map<String, Object> properties = new LinkedHashMap<>();
        private String topic = DEFAULT_TOPIC;
        private String user = System.getProperty("user.name");
        private int maxMessageBytes = NotificationCodec.DEFAULT_MAX_MESSAGE_BYTES;
        private boolean compression = true;
        private Duration sendTimeout = Duration.ofSeconds(30);
        private Producer<String, String> producer;

        private Builder() {
        }

        public Builder bootstrapServers(String bootstrapServers) {
            return property(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        }

        /** Any Kafka producer property, e.g. {@code security.protocol} or {@code sasl.jaas.config}. */
        public Builder property(String name, Object value) {
            properties.put(name, value);
            return this;
        }

        public Builder properties(Map<String, ?> properties) {
            this.properties.putAll(properties);
            return this;
        }

        /** Hook topic; {@value AtlasHookPublisher#DEFAULT_TOPIC} unless Atlas is configured otherwise. */
        public Builder topic(String topic) {
            this.topic = Objects.requireNonNull(topic, "topic");
            return this;
        }

        /** User recorded for messages that do not set one; defaults to the OS user. */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Size above which messages are compressed and split. Must not exceed Atlas'
         * {@code atlas.notification.message.max.length.bytes} nor the topic's maximum message size.
         */
        public Builder maxMessageBytes(int maxMessageBytes) {
            this.maxMessageBytes = maxMessageBytes;
            return this;
        }

        /** Whether large messages are GZIP-compressed before being split (default {@code true}). */
        public Builder compression(boolean compression) {
            this.compression = compression;
            return this;
        }

        /** How long the synchronous methods wait for Kafka (default 30 seconds). */
        public Builder sendTimeout(Duration sendTimeout) {
            this.sendTimeout = Objects.requireNonNull(sendTimeout, "sendTimeout");
            return this;
        }

        /** Uses an existing producer instead of creating one; it is flushed but not closed on {@link #close()}. */
        public Builder producer(Producer<String, String> producer) {
            this.producer = producer;
            return this;
        }

        public AtlasHookPublisher build() {
            if (producer != null) {
                return new AtlasHookPublisher(this, producer, false);
            }
            if (!properties.containsKey(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)) {
                throw new IllegalStateException("bootstrapServers is required");
            }
            Map<String, Object> config = new LinkedHashMap<>();
            config.put(ProducerConfig.ACKS_CONFIG, "all");
            config.put(ProducerConfig.CLIENT_ID_CONFIG, "atlas-hook-publisher");
            config.putAll(properties);
            return new AtlasHookPublisher(this,
                    new KafkaProducer<>(config, new StringSerializer(), new StringSerializer()), true);
        }
    }
}
