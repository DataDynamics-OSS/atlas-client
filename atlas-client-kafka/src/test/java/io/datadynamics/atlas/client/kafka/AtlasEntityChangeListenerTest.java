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

class AtlasEntityChangeListenerTest {

    private static final TopicPartition PARTITION = new TopicPartition("ATLAS_ENTITIES", 0);

    private final MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    private long offset;

    /** An entity notification as Atlas 2.3 writes it. */
    static String notification(String typeName, String qualifiedName, String operation) {
        return "{\"version\":{\"version\":\"1.0.0\",\"versionParts\":[1]},\"msgCompressionKind\":\"NONE\","
                + "\"msgSplitIdx\":1,\"msgSplitCount\":1,\"msgSourceIP\":\"172.17.0.2\",\"msgCreatedBy\":\"\","
                + "\"msgCreationTime\":1700000000000,\"spooled\":false,\"message\":{\"type\":\"ENTITY_NOTIFICATION_V2\","
                + "\"entity\":{\"typeName\":\"" + typeName + "\",\"attributes\":{\"qualifiedName\":\"" + qualifiedName
                + "\",\"name\":\"orders\"},\"guid\":\"g-" + qualifiedName + "\",\"status\":\"ACTIVE\","
                + "\"displayText\":\"orders\",\"classificationNames\":[\"PII\"],\"isIncomplete\":false},"
                + "\"operationType\":\"" + operation + "\",\"eventTime\":1700000000123}}";
    }

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

    @Test
    void undecodableMessagesGoToTheErrorHandler() {
        deliver("garbage", notification("hive_table", "a", "ENTITY_UPDATE"));
        List<String> errors = new CopyOnWriteArrayList<>();
        AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder().consumer(consumer)
                .errorHandler((record, event, error) -> errors.add(record.offset() + ":" + event)).build();

        assertThat(listener.poll(Duration.ofMillis(10))).hasSize(1);
        assertThat(errors).containsExactly("0:null");
    }

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

    @Test
    void startNeedsAHandler() {
        AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder().consumer(consumer).build();
        assertThatThrownBy(listener::start).isInstanceOf(IllegalStateException.class);
    }
}
