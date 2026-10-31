package io.datadynamics.atlas.client.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.kafka.message.EntityChangeEvent;
import io.datadynamics.atlas.client.kafka.message.OperationType;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Atlas가 {@code ATLAS_ENTITIES} 토픽에 발행하는 엔티티 변경 알림을 받습니다.
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
 * {@link #start()}는 백그라운드 스레드에서 이벤트를 처리하고 배치마다 오프셋을 커밋하므로, 모든 이벤트가
 * 적어도 한 번은 처리됩니다(at-least-once). 처리 도중 멈추면 같은 이벤트를 다시 받을 수 있습니다.
 * 직접 루프를 돌리고 싶다면 {@link #poll(Duration)}과 {@link #commit()}을 호출해도 됩니다. 핸들러에서 실패한
 * 이벤트는 {@link ErrorHandler}에 넘깁니다. 기본 동작은 로그를 남기고 건너뛰는 것입니다.
 * <p>
 * {@code ENTITY_NOTIFICATION_V2}가 아닌 알림은 조용히 건너뜁니다. 나뉜 알림은 모든 조각을 받은 뒤 한 번에 처리합니다.
 * <p>
 * Atlas 2.3은 전체 갱신({@code POST /v2/entity}, {@code /v2/entity/bulk})에 대해서만 {@code ENTITY_UPDATE}를
 * 발행합니다. 부분 갱신({@code PUT /v2/entity/guid/{guid}?name=...}, {@code PUT /v2/entity/uniqueAttribute/...})은
 * 알림 없이 엔티티를 바꿉니다.
 */
public final class AtlasEntityChangeListener implements AutoCloseable {

    /** Atlas가 엔티티 변경 알림을 발행하는 기본 토픽 이름입니다. */
    public static final String DEFAULT_TOPIC = "ATLAS_ENTITIES";

    private static final Logger LOG = LoggerFactory.getLogger(AtlasEntityChangeListener.class);

    /**
     * 디코딩할 수 없는 메시지({@code event}가 {@code null})와 핸들러 실패 때 호출됩니다.
     */
    @FunctionalInterface
    public interface ErrorHandler {
        /**
         * 오류를 처리합니다. 여기서 예외를 던지면 백그라운드 스레드가 해당 배치를 커밋하지 않고 멈춥니다.
         *
         * @param record 문제가 된 Kafka 레코드
         * @param event  디코딩된 이벤트. 디코딩에 실패했으면 {@code null}
         * @param error  발생한 예외
         */
        void onError(ConsumerRecord<String, String> record, EntityChangeEvent event, Exception error);
    }

    /** 필터를 통과한 이벤트와 그 원본 레코드입니다. 핸들러가 실패했을 때 레코드를 오류 핸들러에 넘기려고 함께 둡니다. */
    private record Received(ConsumerRecord<String, String> record, EntityChangeEvent event) {
    }

    private final Consumer<String, String> consumer;
    private final String topic;
    private final Predicate<EntityChangeEvent> filter;
    private final java.util.function.Consumer<EntityChangeEvent> handler;
    private final ErrorHandler errorHandler;
    private final Duration pollTimeout;
    private final NotificationCodec codec = new NotificationCodec();
    // start()와 close()가 thread·closed를 함께 확인하고 바꾸는 구간을 보호하는 잠금입니다.
    private final Object lifecycle = new Object();
    // 처음 poll할 때 한 번만 subscribe하기 위한 표시입니다. consumer를 쓰는 스레드에서만 접근합니다.
    private boolean subscribed;
    // 마지막 poll에서 받은 레코드 수(필터로 건너뛴 것 포함)입니다. 0이면 커밋할 것이 없으므로 커밋을 생략합니다.
    private int lastPollCount;
    // 마지막 poll에서 받은 모든 레코드(필터로 건너뛴 것 포함)를 받은 순서대로, 그리고 그중 결과로 돌려준 이벤트입니다.
    // commitThrough()가 어디까지 처리했는지 계산할 때 씁니다.
    private List<ConsumerRecord<String, String>> lastRecords = List.of();
    private List<Received> lastReceived = List.of();
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

    /**
     * 새 빌더를 만듭니다.
     *
     * @return 기본 설정의 빌더
     */
    public static Builder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------------- 직접 소비

    /**
     * 필터를 통과한 다음 이벤트들을 가져옵니다. 스레드 안전하지 않으며 {@link #start()}와 함께 쓰면 안 됩니다.
     * 오프셋은 {@link #commit()}이나 {@link #commitThrough(EntityChangeEvent)}를 호출해야만 커밋됩니다. 처음 호출할 때
     * 토픽을 구독합니다.
     * 디코딩할 수 없는 메시지는 오류 핸들러에 넘기고 결과에서 뺍니다.
     *
     * @param timeout Kafka에서 레코드를 기다리는 최대 시간
     * @return 필터를 통과한 이벤트. 없으면 빈 목록
     * @throws IllegalStateException {@link #start()}로 백그라운드 처리를 시작한 경우
     */
    public List<EntityChangeEvent> poll(Duration timeout) {
        ensureNotStarted();
        return receive(timeout).stream().map(Received::event).collect(Collectors.toList());
    }

    /**
     * 지금까지 {@link #poll(Duration)}이 가져온 모든 레코드(필터로 건너뛴 것 포함)의 오프셋을 동기로 커밋합니다.
     *
     * @throws IllegalStateException {@link #start()}로 백그라운드 처리를 시작한 경우
     */
    public void commit() {
        ensureNotStarted();
        consumer.commitSync();
    }

    /**
     * 마지막 {@link #poll(Duration)} 결과 중 {@code event}까지만 처리한 것으로 보고 그 위치까지 커밋합니다.
     * <p>
     * 결과 목록을 끝까지 처리하지 못하고 멈출 때 씁니다(예: 이벤트 수 제한). {@link #commit()}은 poll로 받은 레코드 전체를
     * 커밋하므로, 처리하지 않은 이벤트까지 읽은 것으로 표시됩니다. 이 메서드는 {@code event}와 그 앞에 받은 레코드(필터로
     * 건너뛴 것 포함)만 커밋하고, 뒤쪽 레코드는 다음 poll에서 다시 받도록 consumer 위치를 되돌립니다.
     *
     * @param event 마지막 poll이 돌려준 이벤트 중 마지막으로 처리한 것
     * @throws IllegalArgumentException {@code event}가 마지막 poll의 결과가 아닌 경우
     * @throws IllegalStateException    {@link #start()}로 백그라운드 처리를 시작한 경우
     */
    public void commitThrough(EntityChangeEvent event) {
        ensureNotStarted();
        ConsumerRecord<String, String> through = lastReceived.stream()
                .filter(received -> received.event() == event)
                .map(Received::record)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The event is not from the last poll()"));
        // 레코드는 받은 순서대로 처리되므로, through까지는 파티션별 마지막 오프셋 + 1을 커밋하고
        // 그 뒤 레코드는 파티션별 첫 오프셋으로 되돌립니다.
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        Map<TopicPartition, Long> rewind = new HashMap<>();
        boolean handled = true;
        for (ConsumerRecord<String, String> record : lastRecords) {
            TopicPartition partition = new TopicPartition(record.topic(), record.partition());
            if (handled) {
                offsets.put(partition, new OffsetAndMetadata(record.offset() + 1));
            } else {
                rewind.putIfAbsent(partition, record.offset());
            }
            if (record == through) {
                handled = false;
            }
        }
        consumer.commitSync(offsets);
        rewind.forEach(consumer::seek);
    }

    private void ensureNotStarted() {
        if (thread != null) {
            throw new IllegalStateException("The listener runs in the background; poll() is not available");
        }
    }

    /**
     * 레코드를 한 번 poll해서 디코딩하고 필터를 적용합니다. 나뉜 메시지의 앞쪽 조각, {@code ENTITY_NOTIFICATION_V2}가
     * 아닌 알림, 필터를 통과하지 못한 이벤트는 결과에서 빠집니다. 디코딩에 실패한 레코드는 오류 핸들러에 넘깁니다.
     */
    private List<Received> receive(Duration timeout) {
        if (!subscribed) {
            consumer.subscribe(List.of(topic));
            subscribed = true;
        }
        List<Received> received = new ArrayList<>();
        var records = consumer.poll(timeout);
        lastPollCount = records.count();
        lastRecords = new ArrayList<>(lastPollCount);
        lastReceived = received;
        for (ConsumerRecord<String, String> record : records) {
            lastRecords.add(record);
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

    // ---------------------------------------------------------------- 백그라운드 소비

    /**
     * 백그라운드 데몬 스레드에서 이벤트 처리를 시작합니다. 스레드 이름은 {@code atlas-entity-listener-<토픽>}입니다.
     *
     * @return 메서드 체이닝을 위한 이 리스너
     * @throws IllegalStateException 핸들러를 설정하지 않았거나, 이미 시작했거나, 이미 닫은 경우
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

    /**
     * 백그라운드 스레드의 본문입니다. poll → 이벤트마다 핸들러 호출 → 받은 레코드가 있으면 커밋을 반복합니다.
     * {@link #close()}가 {@code running}을 내리고 {@code consumer.wakeup()}을 호출하면 {@link WakeupException}으로
     * 빠져나옵니다. 이때 {@code running}이 아직 {@code true}라면 close가 아닌 다른 곳에서 깨운 것이므로 실패로
     * 기록합니다. 어떤 이유로 끝나든 consumer는 이 스레드에서 닫습니다(KafkaConsumer는 다른 스레드에서 쓰면 안 되므로).
     */
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

    /**
     * 백그라운드 스레드가 실행 중인지 알려 줍니다.
     *
     * @return 실행 중이면 {@code true}. 시작하지 않았거나, 멈췄거나, 닫았으면 {@code false}
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 백그라운드 스레드가 오류로 멈췄다면 그 원인을 돌려줍니다.
     *
     * @return 멈춘 원인. 오류 없이 실행 중이거나 정상적으로 닫혔으면 빈 값
     */
    public Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    /**
     * 백그라운드 스레드가 있으면 멈추고 Kafka consumer를 닫습니다. 여러 번 호출해도 안전합니다.
     * <p>
     * 백그라운드 스레드가 있으면 consumer를 깨운 뒤 스레드가 끝나기를 최대 30초 기다리며, consumer는 그 스레드가
     * 닫습니다. 기다리는 중에 인터럽트되면 인터럽트 상태를 다시 설정하고 바로 돌아옵니다.
     */
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

    // ---------------------------------------------------------------- 빌더

    /**
     * {@link AtlasEntityChangeListener}를 설정해 만드는 빌더입니다. 스레드 안전하지 않습니다.
     */
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

        /**
         * Kafka 브로커 주소({@code bootstrap.servers})를 정합니다. {@link #consumer(Consumer)}를 쓰지 않으면 필수입니다.
         *
         * @param bootstrapServers 쉼표로 구분한 {@code host:port} 목록
         * @return 이 빌더
         */
        public Builder bootstrapServers(String bootstrapServers) {
            return property(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        }

        /**
         * consumer group을 정합니다. 같은 그룹의 리스너들은 이벤트를 나누어 받습니다.
         * {@link #consumer(Consumer)}를 쓰지 않으면 필수입니다.
         *
         * @param groupId consumer group ID
         * @return 이 빌더
         */
        public Builder groupId(String groupId) {
            return property(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }

        /**
         * 새 consumer group이 처음 연결된 뒤 발행된 이벤트만 받는 대신, 보관 중인 가장 오래된 이벤트부터 받게 합니다
         * ({@code auto.offset.reset=earliest}). 이미 커밋한 오프셋이 있는 그룹에는 영향이 없습니다.
         *
         * @return 이 빌더
         */
        public Builder startFromEarliest() {
            return property(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        }

        /**
         * Kafka consumer 속성을 하나 정합니다. 예: {@code security.protocol}, {@code sasl.jaas.config}.
         * 여기서 정한 값은 이 클래스의 기본값({@code enable.auto.commit=false}, {@code client.id=atlas-entity-listener})보다
         * 우선합니다.
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
         * Kafka consumer 속성을 한꺼번에 추가합니다. 같은 이름의 기존 값은 덮어씁니다.
         *
         * @param properties 추가할 속성
         * @return 이 빌더
         */
        public Builder properties(Map<String, ?> properties) {
            this.properties.putAll(properties);
            return this;
        }

        /**
         * 구독할 토픽을 정합니다. 기본값은 {@value AtlasEntityChangeListener#DEFAULT_TOPIC}입니다.
         *
         * @param topic 토픽 이름
         * @return 이 빌더
         */
        public Builder topic(String topic) {
            this.topic = Objects.requireNonNull(topic, "topic");
            return this;
        }

        /**
         * 지정한 엔티티(또는 relationship) 타입의 이벤트만 받습니다. 하위 타입은 포함하지 않습니다.
         * 다른 필터와 AND로 결합됩니다.
         *
         * @param typeNames 받을 타입 이름
         * @return 이 빌더
         */
        public Builder typeNames(String... typeNames) {
            Set<String> names = Set.of(typeNames);
            return filter(event -> names.contains(event.typeName()));
        }

        /**
         * 지정한 작업 종류의 이벤트만 받습니다. 다른 필터와 AND로 결합됩니다.
         *
         * @param operations 받을 작업 종류(하나 이상)
         * @return 이 빌더
         * @throws IllegalArgumentException 작업 종류를 하나도 주지 않은 경우
         */
        public Builder operations(OperationType... operations) {
            Set<OperationType> set = EnumSet.copyOf(Arrays.asList(operations));
            return filter(event -> set.contains(event.getOperationType()));
        }

        /**
         * 조건을 추가합니다. 모든 조건을 만족하는 이벤트만 받습니다.
         *
         * @param filter 추가할 조건
         * @return 이 빌더
         */
        public Builder filter(Predicate<EntityChangeEvent> filter) {
            this.filter = this.filter.and(Objects.requireNonNull(filter, "filter"));
            return this;
        }

        /**
         * 필터를 통과한 이벤트마다 호출할 핸들러를 정합니다. {@link AtlasEntityChangeListener#start()}에 필요합니다.
         *
         * @param handler 이벤트 핸들러
         * @return 이 빌더
         */
        public Builder handler(java.util.function.Consumer<EntityChangeEvent> handler) {
            this.handler = handler;
            return this;
        }

        /**
         * 메시지를 디코딩할 수 없거나 핸들러가 예외를 던졌을 때 호출할 오류 핸들러를 정합니다. 기본 동작은 경고
         * 로그를 남기고 이벤트를 건너뛰는 것입니다. 여기서 예외를 던지면 백그라운드 스레드가 그 배치를 커밋하지
         * 않고 멈춥니다.
         *
         * @param errorHandler 오류 핸들러
         * @return 이 빌더
         */
        public Builder errorHandler(ErrorHandler errorHandler) {
            this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
            return this;
        }

        /**
         * 백그라운드 스레드가 한 번 poll할 때 기다리는 최대 시간을 정합니다(기본값 1초).
         *
         * @param pollTimeout poll 대기 시간
         * @return 이 빌더
         */
        public Builder pollTimeout(Duration pollTimeout) {
            this.pollTimeout = Objects.requireNonNull(pollTimeout, "pollTimeout");
            return this;
        }

        /**
         * consumer를 새로 만들지 않고 기존 consumer를 씁니다. 이 경우 {@code bootstrapServers}, {@code groupId}와
         * consumer 속성은 무시되며, 이 consumer는 리스너와 함께 닫힙니다.
         *
         * @param consumer 문자열 키·값을 읽는 Kafka consumer
         * @return 이 빌더
         */
        public Builder consumer(Consumer<String, String> consumer) {
            this.consumer = consumer;
            return this;
        }

        /**
         * 설정대로 리스너를 만듭니다. 아직 구독하거나 시작하지는 않습니다. consumer를 지정하지 않았으면
         * {@code enable.auto.commit=false}와 {@code client.id=atlas-entity-listener}를 기본값으로 하는
         * {@link KafkaConsumer}를 새로 만듭니다. 오프셋은 리스너가 직접 커밋하므로 자동 커밋을 끕니다.
         *
         * @return 새 리스너
         * @throws IllegalStateException consumer를 지정하지 않았는데 {@code bootstrapServers}나 {@code groupId}가 없는 경우
         */
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
