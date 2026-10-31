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
 * Atlas hook과 같은 방식으로, 엔티티 변경을 {@code ATLAS_HOOK} Kafka 토픽을 통해 Atlas에 보냅니다.
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
 * REST 호출과 달리, Kafka에 전달되었다는 것은 Atlas가 그 변경을 <em>앞으로</em> 처리한다는 뜻일 뿐입니다.
 * 처리는 비동기로, 순서대로 이루어지며, 실패(예: 알 수 없는 타입)는 Atlas 서버 로그에만 남습니다.
 * 동기 메서드는 Kafka가 메시지를 확인(acknowledge)할 때까지 기다리고, {@link #sendAsync(HookMessage)}는
 * 기다리지 않습니다.
 * <p>
 * {@code atlas-client-lineage}로 만든 리니지도 이 클래스로 보낼 수 있습니다:
 * {@code hook.createOrUpdate(lineage.toEntities(processes))}.
 * <p>
 * Kafka {@link Producer}는 스레드 안전하고 이 클래스의 필드는 생성 후 바뀌지 않으므로, 인스턴스 하나를 여러
 * 스레드에서 함께 써도 됩니다. 다만 메시지에 사용자가 없으면 기본 사용자를 메시지 객체에 직접 써 넣으므로,
 * 같은 {@link HookMessage} 객체를 여러 스레드에서 동시에 보내지는 마십시오.
 */
public final class AtlasHookPublisher implements AutoCloseable {

    /** Atlas가 hook 메시지를 읽는 기본 토픽 이름입니다. */
    public static final String DEFAULT_TOPIC = "ATLAS_HOOK";

    private static final Logger LOG = LoggerFactory.getLogger(AtlasHookPublisher.class);

    private final Producer<String, String> producer;
    // 이 클래스가 producer를 직접 만들었으면 true입니다. 이때만 close()에서 producer를 닫습니다.
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

    /**
     * 새 빌더를 만듭니다.
     *
     * @return 기본 설정의 빌더
     */
    public static Builder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------------- 엔티티 작업

    /**
     * {@code ENTITY_CREATE_V2} 메시지를 보냅니다. 엔티티를 만들고, 이미 있는 엔티티는 갱신합니다.
     * Kafka가 확인할 때까지 기다립니다.
     *
     * @param entities 만들거나 갱신할 엔티티
     * @return 메시지가 기록된 위치
     * @throws AtlasClientException 전송에 실패했거나 제한 시간 안에 확인받지 못한 경우
     */
    public PublishResult createOrUpdate(AtlasEntity... entities) {
        return createOrUpdate(Arrays.asList(entities));
    }

    /**
     * 엔티티 컬렉션으로 {@code ENTITY_CREATE_V2} 메시지를 보냅니다. {@link #createOrUpdate(AtlasEntity...)}와 같습니다.
     *
     * @param entities 만들거나 갱신할 엔티티
     * @return 메시지가 기록된 위치
     * @throws AtlasClientException 전송에 실패했거나 제한 시간 안에 확인받지 못한 경우
     */
    public PublishResult createOrUpdate(Collection<AtlasEntity> entities) {
        return createOrUpdate(new AtlasEntitiesWithExtInfo(entities));
    }

    /**
     * 참조 엔티티(referredEntities)를 포함한 엔티티 묶음으로 {@code ENTITY_CREATE_V2} 메시지를 보냅니다.
     * {@code AtlasLineage.toEntities(...)}의 결과를 그대로 넘길 수 있습니다.
     *
     * @param entities 만들거나 갱신할 엔티티 묶음
     * @return 메시지가 기록된 위치
     * @throws AtlasClientException 전송에 실패했거나 제한 시간 안에 확인받지 못한 경우
     */
    public PublishResult createOrUpdate(AtlasEntitiesWithExtInfo entities) {
        return send(new EntityCreateRequest(entities));
    }

    /**
     * {@code ENTITY_FULL_UPDATE_V2} 메시지를 보냅니다. 엔티티를 주어진 정의로 교체합니다.
     *
     * @param entities 새 정의
     * @return 메시지가 기록된 위치
     * @throws AtlasClientException 전송에 실패했거나 제한 시간 안에 확인받지 못한 경우
     */
    public PublishResult fullUpdate(AtlasEntitiesWithExtInfo entities) {
        return send(new EntityFullUpdateRequest(entities));
    }

    /**
     * {@code ENTITY_PARTIAL_UPDATE_V2} 메시지를 보냅니다. {@code patch}에 설정한 속성만 갱신합니다.
     *
     * @param entityId 갱신할 엔티티(typeName과 uniqueAttributes로 지정)
     * @param patch    갱신할 속성만 담은 엔티티
     * @return 메시지가 기록된 위치
     * @throws AtlasClientException 전송에 실패했거나 제한 시간 안에 확인받지 못한 경우
     */
    public PublishResult partialUpdate(AtlasObjectId entityId, AtlasEntity patch) {
        return send(new EntityPartialUpdateRequest(entityId, patch));
    }

    /**
     * typeName과 qualifiedName으로 지정한 엔티티를 부분 갱신합니다.
     * {@code patch}에 타입이 없으면 {@code typeName}을 {@code patch}에 직접 설정합니다.
     *
     * @param typeName      엔티티 타입
     * @param qualifiedName 엔티티 qualifiedName
     * @param patch         갱신할 속성만 담은 엔티티
     * @return 메시지가 기록된 위치
     * @throws AtlasClientException 전송에 실패했거나 제한 시간 안에 확인받지 못한 경우
     */
    public PublishResult partialUpdateByQualifiedName(String typeName, String qualifiedName, AtlasEntity patch) {
        if (patch.getTypeName() == null) {
            patch.setTypeName(typeName);
        }
        return partialUpdate(AtlasObjectId.byQualifiedName(typeName, qualifiedName), patch);
    }

    /**
     * {@code ENTITY_DELETE_V2} 메시지를 보냅니다. typeName과 uniqueAttributes로 지정한 엔티티를 삭제합니다.
     *
     * @param entities 삭제할 엔티티
     * @return 메시지가 기록된 위치
     * @throws AtlasClientException 전송에 실패했거나 제한 시간 안에 확인받지 못한 경우
     */
    public PublishResult delete(AtlasObjectId... entities) {
        return delete(Arrays.asList(entities));
    }

    /**
     * 엔티티 컬렉션으로 {@code ENTITY_DELETE_V2} 메시지를 보냅니다.
     *
     * @param entities 삭제할 엔티티
     * @return 메시지가 기록된 위치
     * @throws AtlasClientException 전송에 실패했거나 제한 시간 안에 확인받지 못한 경우
     */
    public PublishResult delete(Collection<AtlasObjectId> entities) {
        return send(new EntityDeleteRequest(entities));
    }

    /**
     * typeName과 qualifiedName으로 지정한 엔티티 하나를 삭제하는 메시지를 보냅니다.
     *
     * @param typeName      엔티티 타입
     * @param qualifiedName 엔티티 qualifiedName
     * @return 메시지가 기록된 위치
     * @throws AtlasClientException 전송에 실패했거나 제한 시간 안에 확인받지 못한 경우
     */
    public PublishResult deleteByQualifiedName(String typeName, String qualifiedName) {
        return delete(AtlasObjectId.byQualifiedName(typeName, qualifiedName));
    }

    // ---------------------------------------------------------------- 전송

    /**
     * 메시지를 보내고 Kafka가 확인할 때까지 기다립니다. 최대 대기 시간은 {@link Builder#sendTimeout(Duration)}입니다.
     * 기다리는 중에 인터럽트되면 인터럽트 상태를 다시 설정한 뒤 예외를 던집니다.
     *
     * @param message 보낼 hook 메시지
     * @return 메시지가 기록된 위치
     * @throws IllegalArgumentException Atlas가 처리할 수 없는 메시지인 경우
     * @throws AtlasClientException     전송에 실패했거나, 인터럽트되었거나, 제한 시간 안에 Kafka가 확인하지 않은 경우
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
     * 기다리지 않고 메시지를 보냅니다. 반환된 future는 Kafka가 메시지의 모든 조각을 확인하면 완료되고,
     * 한 조각이라도 실패하면 그 예외로 실패합니다.
     * <p>
     * 메시지에 사용자가 없으면 빌더에서 정한 사용자를 메시지에 설정합니다. 메시지가 크면
     * {@link NotificationCodec}이 압축하거나 여러 Kafka 메시지로 나눕니다.
     *
     * @param message 보낼 hook 메시지
     * @return 기록 위치로 완료되는 future
     * @throws IllegalArgumentException Atlas가 처리할 수 없는 메시지인 경우(호출 즉시 던집니다)
     */
    public CompletableFuture<PublishResult> sendAsync(HookMessage message) {
        Objects.requireNonNull(message, "message");
        message.validate();
        if (message.getUser() == null) {
            message.setUser(user);
        }
        List<String> parts = codec.encode(message, message.getUser());
        // 나뉜 메시지의 조각들은 순서를 지켜야 하므로 같은 키를 주어 같은 파티션에 들어가게 합니다.
        // 나뉘지 않은 메시지는 키 없이 보내 producer의 기본 파티션 배정에 맡깁니다.
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
        // 모든 조각이 확인된 뒤 마지막 조각의 위치를 결과로 씁니다. 이 시점에는 모든 future가 끝났으므로 join은 막히지 않습니다.
        return CompletableFuture.allOf(sent.toArray(new CompletableFuture<?>[0])).thenApply(ignored -> {
            RecordMetadata last = sent.get(sent.size() - 1).join();
            return new PublishResult(message.getType(), last.topic(), last.partition(), last.offset(), parts.size());
        });
    }

    /** 지금까지 보낸 모든 메시지가 확인될 때까지 기다립니다. */
    public void flush() {
        producer.flush();
    }

    /**
     * 퍼블리셔를 닫습니다. 직접 만든 producer는 닫고, {@link Builder#producer(Producer)}로 받은 producer는
     * 다른 곳에서도 쓸 수 있으므로 닫지 않고 flush만 합니다.
     */
    @Override
    public void close() {
        if (ownsProducer) {
            producer.close();
        } else {
            producer.flush();
        }
    }

    // ---------------------------------------------------------------- 빌더

    /**
     * {@link AtlasHookPublisher}를 설정해 만드는 빌더입니다. 스레드 안전하지 않습니다.
     */
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

        /**
         * Kafka 브로커 주소({@code bootstrap.servers})를 정합니다. {@link #producer(Producer)}를 쓰지 않으면 필수입니다.
         *
         * @param bootstrapServers 쉼표로 구분한 {@code host:port} 목록
         * @return 이 빌더
         */
        public Builder bootstrapServers(String bootstrapServers) {
            return property(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        }

        /**
         * Kafka producer 속성을 하나 정합니다. 예: {@code security.protocol}, {@code sasl.jaas.config}.
         * 여기서 정한 값은 이 클래스의 기본값({@code acks=all}, {@code client.id=atlas-hook-publisher})보다 우선합니다.
         *
         * @param name  속성 이름
         * @param value 속성 값
         * @return 이 빌더
         */
        public Builder property(String name, Object value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Kafka producer 속성을 한꺼번에 추가합니다. 같은 이름의 기존 값은 덮어씁니다.
         *
         * @param properties 추가할 속성
         * @return 이 빌더
         */
        public Builder properties(Map<String, ?> properties) {
            this.properties.putAll(properties);
            return this;
        }

        /**
         * hook 토픽을 정합니다. Atlas 설정을 바꾸지 않았다면 {@value AtlasHookPublisher#DEFAULT_TOPIC}입니다.
         *
         * @param topic 토픽 이름
         * @return 이 빌더
         */
        public Builder topic(String topic) {
            this.topic = Objects.requireNonNull(topic, "topic");
            return this;
        }

        /**
         * 사용자를 지정하지 않은 메시지에 기록할 사용자를 정합니다. 기본값은 OS 사용자({@code user.name})입니다.
         *
         * @param user 사용자 이름
         * @return 이 빌더
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * 이 크기(바이트)를 넘는 메시지는 압축하고 나눕니다. Atlas의
         * {@code atlas.notification.message.max.length.bytes}와 토픽의 최대 메시지 크기를 넘으면 안 됩니다.
         * 기본값은 {@value NotificationCodec#DEFAULT_MAX_MESSAGE_BYTES}입니다.
         *
         * @param maxMessageBytes 최대 메시지 크기(1 이상, 아니면 {@link #build()}에서 예외)
         * @return 이 빌더
         */
        public Builder maxMessageBytes(int maxMessageBytes) {
            this.maxMessageBytes = maxMessageBytes;
            return this;
        }

        /**
         * 큰 메시지를 나누기 전에 GZIP으로 압축할지 정합니다(기본값 {@code true}).
         *
         * @param compression 압축하려면 {@code true}
         * @return 이 빌더
         */
        public Builder compression(boolean compression) {
            this.compression = compression;
            return this;
        }

        /**
         * 동기 메서드가 Kafka의 확인을 기다리는 최대 시간을 정합니다(기본값 30초).
         *
         * @param sendTimeout 대기 시간
         * @return 이 빌더
         */
        public Builder sendTimeout(Duration sendTimeout) {
            this.sendTimeout = Objects.requireNonNull(sendTimeout, "sendTimeout");
            return this;
        }

        /**
         * producer를 새로 만들지 않고 기존 producer를 씁니다. 이 경우 {@code bootstrapServers}와 producer 속성은
         * 무시되며, {@link AtlasHookPublisher#close()}는 이 producer를 닫지 않고 flush만 합니다.
         *
         * @param producer 문자열 키·값을 쓰는 Kafka producer
         * @return 이 빌더
         */
        public Builder producer(Producer<String, String> producer) {
            this.producer = producer;
            return this;
        }

        /**
         * 설정대로 퍼블리셔를 만듭니다. producer를 지정하지 않았으면 {@code acks=all}과
         * {@code client.id=atlas-hook-publisher}를 기본값으로 하는 {@link KafkaProducer}를 새로 만듭니다.
         *
         * @return 새 퍼블리셔
         * @throws IllegalStateException    producer도 {@code bootstrapServers}도 지정하지 않은 경우
         * @throws IllegalArgumentException {@code maxMessageBytes}가 1보다 작은 경우
         */
        public AtlasHookPublisher build() {
            if (producer != null) {
                return new AtlasHookPublisher(this, producer, false);
            }
            if (!properties.containsKey(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)) {
                throw new IllegalStateException("bootstrapServers is required");
            }
            // 기본값을 먼저 넣고 사용자 속성을 나중에 넣어, 사용자가 같은 속성을 지정하면 그 값이 쓰이게 합니다.
            Map<String, Object> config = new LinkedHashMap<>();
            config.put(ProducerConfig.ACKS_CONFIG, "all");
            config.put(ProducerConfig.CLIENT_ID_CONFIG, "atlas-hook-publisher");
            config.putAll(properties);
            return new AtlasHookPublisher(this,
                    new KafkaProducer<>(config, new StringSerializer(), new StringSerializer()), true);
        }
    }
}
