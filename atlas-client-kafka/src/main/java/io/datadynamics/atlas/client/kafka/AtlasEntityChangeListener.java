package io.datadynamics.atlas.client.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.kafka.message.EntityChangeEvent;
import io.datadynamics.atlas.client.kafka.message.OperationType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Receives the entity change notifications Atlas publishes to the {@code ATLAS_ENTITIES} topic.
 * <pre>{@code
 * AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder()
 *     .bootstrapServers("kafka:9092")
 *     .groupId("catalog-sync")
 *     .typeNames("hive_table")
 *     .operations(OperationType.ENTITY_CREATE, OperationType.ENTITY_DELETE)
 *     .handler(event -> System.out.println(event.getOperationType() + " " + event.qualifiedName()))
 *     .build()
 *     .start();
 * ...
 * listener.close();
 * }</pre>
 * {@link #start()} handles events on a background thread and commits offsets after each batch,
 * so every event is handled at least once. Alternatively call {@link #poll(Duration)} and
 * {@link #commit()} from your own loop. Events whose handler fails are passed to the
 * {@link ErrorHandler} (by default logged and skipped).
 * <p>
 * Atlas 2.3 publishes {@code ENTITY_UPDATE} only for full updates ({@code POST /v2/entity},
 * {@code /v2/entity/bulk}); partial updates ({@code PUT /v2/entity/guid/{guid}?name=...},
 * {@code PUT /v2/entity/uniqueAttribute/...}) change the entity without any notification.
 */
public final class AtlasEntityChangeListener implements AutoCloseable {

    public static final String DEFAULT_TOPIC = "ATLAS_ENTITIES";

    private static final Logger LOG = LoggerFactory.getLogger(AtlasEntityChangeListener.class);

    /** Called for messages that cannot be decoded ({@code event} is {@code null}) and handler failures. */
    @FunctionalInterface
    public interface ErrorHandler {
        void onError(ConsumerRecord<String, String> record, EntityChangeEvent event, Exception error);
    }

    private record Received(ConsumerRecord<String, String> record, EntityChangeEvent event) {
    }

    private final Consumer<String, String> consumer;
    private final String topic;
    private final Predicate<EntityChangeEvent> filter;
    private final java.util.function.Consumer<EntityChangeEvent> handler;
    private final ErrorHandler errorHandler;
    private final Duration pollTimeout;
    private final NotificationCodec codec = new NotificationCodec();
    private final Object lifecycle = new Object();
    private boolean subscribed;
    private int lastPollCount;
    private Thread thread;
    private volatile boolean running;
    private volatile boolean closed;
    private volatile Throwable failure;

    private AtlasEntityChangeListener(Builder builder, Consumer<String, String> consumer) {
        this.consumer = consumer;
        this.topic = builder.topic;
        this.filter = builder.filter;
        this.handler = builder.handler;
        this.errorHandler = builder.errorHandler;
        this.pollTimeout = builder.pollTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------------- manual consumption

    /**
     * Fetches the next events that pass the filters. Not thread-safe, and not to be mixed with
     * {@link #start()}. Offsets are only committed by {@link #commit()}.
     */
    public List<EntityChangeEvent> poll(Duration timeout) {
        ensureNotStarted();
        return receive(timeout).stream().map(Received::event).collect(Collectors.toList());
    }

    /** Commits the offsets of everything returned by {@link #poll(Duration)} so far. */
    public void commit() {
        ensureNotStarted();
        consumer.commitSync();
    }

    private void ensureNotStarted() {
        if (thread != null) {
            throw new IllegalStateException("The listener runs in the background; poll() is not available");
        }
    }

    private List<Received> receive(Duration timeout) {
        if (!subscribed) {
            consumer.subscribe(List.of(topic));
            subscribed = true;
        }
        List<Received> received = new ArrayList<>();
        var records = consumer.poll(timeout);
        lastPollCount = records.count();
        for (ConsumerRecord<String, String> record : records) {
            EntityChangeEvent event;
            try {
                Optional<JsonNode> message = codec.decode(record.value());
                if (message.isEmpty()) {
                    continue;
                }
                if (!EntityChangeEvent.TYPE_V2.equals(message.get().path("type").asText())) {
                    LOG.debug("Skipping {} notification at {}-{}@{}", message.get().path("type").asText(),
                            record.topic(), record.partition(), record.offset());
                    continue;
                }
                event = AtlasJson.mapper().treeToValue(message.get(), EntityChangeEvent.class);
            } catch (Exception e) {
                errorHandler.onError(record, null, e);
                continue;
            }
            if (filter.test(event)) {
                received.add(new Received(record, event));
            }
        }
        return received;
    }

    // ---------------------------------------------------------------- background consumption

    /**
     * Starts handling events on a background thread.
     *
     * @throws IllegalStateException if no handler was configured or the listener was started before
     */
    public AtlasEntityChangeListener start() {
        if (handler == null) {
            throw new IllegalStateException("start() needs a handler; use poll() otherwise");
        }
        synchronized (lifecycle) {
            if (thread != null || closed) {
                throw new IllegalStateException("Listener already started or closed");
            }
            running = true;
            thread = new Thread(this::run, "atlas-entity-listener-" + topic);
            thread.setDaemon(true);
            thread.start();
        }
        return this;
    }

    private void run() {
        try {
            while (running) {
                List<Received> batch = receive(pollTimeout);
                for (Received received : batch) {
                    try {
                        handler.accept(received.event());
                    } catch (Exception e) {
                        errorHandler.onError(received.record(), received.event(), e);
                    }
                }
                if (lastPollCount > 0) {
                    consumer.commitSync();
                }
            }
        } catch (WakeupException e) {
            if (running) {
                failure = e;
            }
        } catch (Throwable e) {
            failure = e;
            LOG.error("Atlas entity listener on {} stopped", topic, e);
        } finally {
            running = false;
            consumer.close();
        }
    }

    public boolean isRunning() {
        return running;
    }

    /** Why the background thread stopped, if it stopped because of an error. */
    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    /** Stops the background thread, if any, and closes the Kafka consumer. */
    @Override
    public void close() {
        Thread worker;
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            worker = thread;
        }
        if (worker == null) {
            consumer.close();
            return;
        }
        running = false;
        consumer.wakeup();
        try {
            worker.join(Duration.ofSeconds(30).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- builder

    public static final class Builder {

        private final Map<String, Object> properties = new LinkedHashMap<>();
        private String topic = DEFAULT_TOPIC;
        private Predicate<EntityChangeEvent> filter = event -> true;
        private java.util.function.Consumer<EntityChangeEvent> handler;
        private ErrorHandler errorHandler = (record, event, error) -> LOG.warn(
                "Skipping Atlas notification at {}-{}@{}: {}", record.topic(), record.partition(), record.offset(),
                error.toString(), error);
        private Duration pollTimeout = Duration.ofSeconds(1);
        private Consumer<String, String> consumer;

        private Builder() {
        }

        public Builder bootstrapServers(String bootstrapServers) {
            return property(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        }

        /** Consumer group; listeners sharing a group split the events between them. */
        public Builder groupId(String groupId) {
            return property(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }

        /**
         * Makes a new consumer group start at the oldest retained event instead of only receiving
         * events published after it first connects.
         */
        public Builder startFromEarliest() {
            return property(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        }

        /** Any Kafka consumer property, e.g. {@code security.protocol} or {@code sasl.jaas.config}. */
        public Builder property(String name, Object value) {
            properties.put(name, value);
            return this;
        }

        public Builder properties(Map<String, ?> properties) {
            this.properties.putAll(properties);
            return this;
        }

        public Builder topic(String topic) {
            this.topic = Objects.requireNonNull(topic, "topic");
            return this;
        }

        /** Only events about these entity (or relationship) types; sub-types are not included. */
        public Builder typeNames(String... typeNames) {
            Set<String> names = Set.of(typeNames);
            return filter(event -> names.contains(event.typeName()));
        }

        public Builder operations(OperationType... operations) {
            Set<OperationType> set = EnumSet.copyOf(Arrays.asList(operations));
            return filter(event -> set.contains(event.getOperationType()));
        }

        /** Additional condition; all conditions must hold. */
        public Builder filter(Predicate<EntityChangeEvent> filter) {
            this.filter = this.filter.and(Objects.requireNonNull(filter, "filter"));
            return this;
        }

        /** Called for every event that passes the filters, needed by {@link #start()}. */
        public Builder handler(java.util.function.Consumer<EntityChangeEvent> handler) {
            this.handler = handler;
            return this;
        }

        /**
         * Called when a message cannot be decoded or the handler throws. The default logs and skips
         * the event; throwing from here stops the background thread without committing the batch.
         */
        public Builder errorHandler(ErrorHandler errorHandler) {
            this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
            return this;
        }

        public Builder pollTimeout(Duration pollTimeout) {
            this.pollTimeout = Objects.requireNonNull(pollTimeout, "pollTimeout");
            return this;
        }

        /** Uses an existing consumer instead of creating one; it is closed with the listener. */
        public Builder consumer(Consumer<String, String> consumer) {
            this.consumer = consumer;
            return this;
        }

        public AtlasEntityChangeListener build() {
            if (consumer != null) {
                return new AtlasEntityChangeListener(this, consumer);
            }
            if (!properties.containsKey(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)) {
                throw new IllegalStateException("bootstrapServers is required");
            }
            if (!properties.containsKey(ConsumerConfig.GROUP_ID_CONFIG)) {
                throw new IllegalStateException("groupId is required");
            }
            Map<String, Object> config = new LinkedHashMap<>();
            config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            config.put(ConsumerConfig.CLIENT_ID_CONFIG, "atlas-entity-listener");
            config.putAll(properties);
            return new AtlasEntityChangeListener(this,
                    new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer()));
        }
    }
}
