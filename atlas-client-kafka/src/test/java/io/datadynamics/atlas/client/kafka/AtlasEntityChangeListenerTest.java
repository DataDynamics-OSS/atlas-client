package io.datadynamics.atlas.client.kafka;

import io.datadynamics.atlas.client.kafka.message.EntityChangeEvent;
import io.datadynamics.atlas.client.kafka.message.OperationType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AtlasEntityChangeListener}의 알림 디코딩, 필터, 오류 처리, 오프셋 커밋, 백그라운드 처리를
 * Kafka {@link MockConsumer}로 검증합니다.
 */
class AtlasEntityChangeListenerTest {

    private static final TopicPartition PARTITION = new TopicPartition("ATLAS_ENTITIES", 0);

    private final MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    // 다음에 넣을 레코드의 오프셋입니다.
    private long offset;

    /** Atlas 2.3이 쓰는 형식 그대로의 엔티티 알림을 만듭니다. */
    static String notification(String typeName, String qualifiedName, String operation) {
        return "{\"version\":{\"version\":\"1.0.0\",\"versionParts\":[1]},\"msgCompressionKind\":\"NONE\","
                + "\"msgSplitIdx\":1,\"msgSplitCount\":1,\"msgSourceIP\":\"172.17.0.2\",\"msgCreatedBy\":\"\","
                + "\"msgCreationTime\":1700000000000,\"spooled\":false,\"message\":{\"type\":\"ENTITY_NOTIFICATION_V2\","
                + "\"entity\":{\"typeName\":\"" + typeName + "\",\"attributes\":{\"qualifiedName\":\"" + qualifiedName
                + "\",\"name\":\"orders\"},\"guid\":\"g-" + qualifiedName + "\",\"status\":\"ACTIVE\","
                + "\"displayText\":\"orders\",\"classificationNames\":[\"PII\"],\"isIncomplete\":false},"
                + "\"operationType\":\"" + operation + "\",\"eventTime\":1700000000123}}";
    }

    /**
     * 다음 poll 때 레코드들이 도착하도록 예약합니다. 아직 파티션이 배정되지 않았으면 먼저 리밸런스로 배정하고
     * 시작 오프셋을 0으로 정합니다.
     */
    private void deliver(String... values) {
        consumer.schedulePollTask(() -> {
            if (consumer.assignment().isEmpty()) {
                consumer.rebalance(List.of(PARTITION));
                consumer.updateBeginningOffsets(Map.of(PARTITION, 0L));
            }
            for (String value : values) {
                consumer.addRecord(new ConsumerRecord<>(PARTITION.topic(), 0, offset++, null, value));
            }
        });
    }

    /**
     * Atlas 2.3 형식의 알림 하나를 {@code poll}로 받습니다.
     * 기대 결과: 기본 토픽을 구독하고, 이벤트의 작업 종류·타입·qualifiedName·guid·classification·시각이 올바르게
     * 풀리며, {@code commit()} 후 오프셋 1이 커밋됩니다.
     */
    @Test
    void pollDecodesAtlasNotifications() {
        deliver(notification("hive_table", "db.orders@prod", "ENTITY_CREATE"));
        AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder().consumer(consumer).build();

        List<EntityChangeEvent> events = listener.poll(Duration.ofMillis(10));

        assertThat(consumer.subscription()).containsExactly("ATLAS_ENTITIES");
        assertThat(events).hasSize(1);
        EntityChangeEvent event = events.get(0);
        assertThat(event.getOperationType()).isEqualTo(OperationType.ENTITY_CREATE);
        assertThat(event.typeName()).isEqualTo("hive_table");
        assertThat(event.qualifiedName()).isEqualTo("db.orders@prod");
        assertThat(event.guid()).isEqualTo("g-db.orders@prod");
        assertThat(event.getEntity().getClassificationNames()).containsExactly("PII");
        assertThat(event.time().toEpochMilli()).isEqualTo(1700000000123L);

        listener.commit();
        assertThat(consumer.committed(java.util.Set.of(PARTITION))).containsEntry(PARTITION, new OffsetAndMetadata(1));
    }

    /**
     * 세 이벤트를 받은 뒤 첫 번째까지만 처리하고 {@code commitThrough}를 호출합니다.
     * 기대 결과: 오프셋 1만 커밋되고(나머지 두 개는 읽지 않은 것으로 남음), 다음 poll이 오프셋 1부터 읽도록 위치가
     * 되돌아갑니다. 마지막 poll의 결과가 아닌 이벤트를 주면 거부합니다.
     */
    @Test
    void commitThroughCommitsOnlyTheHandledEvents() {
        deliver(notification("hive_table", "db.a@prod", "ENTITY_CREATE"),
                notification("hive_table", "db.b@prod", "ENTITY_CREATE"),
                notification("hive_table", "db.c@prod", "ENTITY_CREATE"));
        AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder().consumer(consumer).build();

        List<EntityChangeEvent> events = listener.poll(Duration.ofMillis(10));
        assertThat(events).hasSize(3);
        listener.commitThrough(events.get(0));

        assertThat(consumer.committed(java.util.Set.of(PARTITION))).containsEntry(PARTITION, new OffsetAndMetadata(1));
        assertThat(consumer.position(PARTITION)).isEqualTo(1);

        EntityChangeEvent other = new EntityChangeEvent();
        assertThatThrownBy(() -> listener.commitThrough(other)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 타입({@code hive_table})과 작업 종류(ENTITY_CREATE, UNKNOWN) 필터를 함께 걸면 둘 다 만족하는 이벤트만 남고,
     * 모르는 작업 종류는 UNKNOWN으로 매칭되는지 확인합니다.
     */
    @Test
    void filtersByTypeAndOperation() {
        deliver(notification("hive_table", "a", "ENTITY_CREATE"),
                notification("hive_table", "b", "ENTITY_DELETE"),
                notification("hive_column", "c", "ENTITY_CREATE"),
                notification("hive_table", "d", "SOMETHING_NEW"));
        AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder().consumer(consumer)
                .typeNames("hive_table").operations(OperationType.ENTITY_CREATE, OperationType.UNKNOWN).build();

        assertThat(listener.poll(Duration.ofMillis(10))).extracting(EntityChangeEvent::qualifiedName)
                .containsExactly("a", "d");
    }

    /**
     * JSON이 아닌 메시지는 이벤트 없이({@code null}) 오류 핸들러로 가고, 뒤따르는 정상 메시지는 그대로 받는지
     * 확인합니다.
     */
    @Test
    void undecodableMessagesGoToTheErrorHandler() {
        deliver("garbage", notification("hive_table", "a", "ENTITY_UPDATE"));
        List<String> errors = new CopyOnWriteArrayList<>();
        AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder().consumer(consumer)
                .errorHandler((record, event, error) -> errors.add(record.offset() + ":" + event)).build();

        assertThat(listener.poll(Duration.ofMillis(10))).hasSize(1);
        assertThat(errors).containsExactly("0:null");
    }

    /**
     * 백그라운드 처리를 검증합니다.
     * 준비: 세 이벤트 중 {@code boom}에서 핸들러가 예외를 던지게 합니다.
     * 기대 결과: 나머지 두 이벤트는 처리되고 실패한 이벤트는 오류 핸들러로 가며, 스레드는 계속 실행됩니다.
     * 실행 중에는 {@code poll}을 쓸 수 없고, {@code close()} 후에는 멈춰 있고 실패가 없으며 consumer가 닫혀 있습니다.
     */
    @Test
    void backgroundListenerHandlesEventsAndCommits() throws Exception {
        deliver(notification("hive_table", "a", "ENTITY_CREATE"), notification("hive_table", "boom", "ENTITY_CREATE"),
                notification("hive_table", "c", "ENTITY_CREATE"));
        List<String> handled = new CopyOnWriteArrayList<>();
        List<String> failed = new CopyOnWriteArrayList<>();
        AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder().consumer(consumer)
                .pollTimeout(Duration.ofMillis(10))
                .handler(event -> {
                    if (event.qualifiedName().equals("boom")) {
                        throw new IllegalStateException("handler failed");
                    }
                    handled.add(event.qualifiedName());
                })
                .errorHandler((record, event, error) -> failed.add(event.qualifiedName() + ":" + error.getMessage()))
                .build()
                .start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (handled.size() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(handled).containsExactly("a", "c");
        assertThat(failed).containsExactly("boom:handler failed");
        assertThat(listener.isRunning()).isTrue();
        assertThatThrownBy(() -> listener.poll(Duration.ZERO)).isInstanceOf(IllegalStateException.class);

        listener.close();
        assertThat(listener.isRunning()).isFalse();
        assertThat(listener.failure()).isEmpty();
        assertThat(consumer.closed()).isTrue();
    }

    /** 핸들러 없이 {@code start()}를 호출하면 {@link IllegalStateException}이 나는지 확인합니다. */
    @Test
    void startNeedsAHandler() {
        AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder().consumer(consumer).build();
        assertThatThrownBy(listener::start).isInstanceOf(IllegalStateException.class);
    }
}
