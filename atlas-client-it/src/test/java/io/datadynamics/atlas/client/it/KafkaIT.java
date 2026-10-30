package io.datadynamics.atlas.client.it;

import io.datadynamics.atlas.client.kafka.AtlasEntityChangeListener;
import io.datadynamics.atlas.client.kafka.AtlasHookPublisher;
import io.datadynamics.atlas.client.kafka.PublishResult;
import io.datadynamics.atlas.client.kafka.message.EntityChangeEvent;
import io.datadynamics.atlas.client.kafka.message.OperationType;
import io.datadynamics.atlas.client.lineage.AtlasLineage;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.MissingEntityPolicy;
import io.datadynamics.atlas.client.lineage.graph.Impact;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.EntityStatus;
import io.datadynamics.atlas.client.test.AtlasTestServer;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.typedef.AttributeTypes.LONG;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hook messages and change notifications through the Kafka a real Atlas uses.
 * Atlas applies hook messages asynchronously, so every check polls until the change shows up.
 */
class KafkaIT extends AtlasIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private static String bootstrapServers;
    private static String table;
    private static String job;
    private static AtlasHookPublisher hook;

    @BeforeAll
    static void setUp() {
        bootstrapServers = AtlasTestServer.shared().kafkaBootstrapServers().orElse(null);
        Assumptions.assumeTrue(bootstrapServers != null, "Atlas server without known Kafka");
        table = name("ktable");
        job = name("kjob");
        cleanupTypes(job, table);
        new TypeDefManager(atlas).sync(
                entity(table).dataSet().attribute(attr("rowCount", LONG)),
                entity(job).process());
        hook = AtlasHookPublisher.builder().bootstrapServers(bootstrapServers).user("it").build();
    }

    @AfterAll
    static void closeHook() {
        if (hook != null) {
            hook.close();
        }
    }

    private static AtlasEntity table(String qualifiedName) {
        return new AtlasEntity(table, Map.of("qualifiedName", qualifiedName, "name", qualifiedName));
    }

    private static String awaitGuid(String typeName, String qualifiedName) {
        Optional<String> guid = awaitValue(() -> atlas.entities().findGuid(typeName, qualifiedName),
                Optional::isPresent, TIMEOUT);
        assertThat(guid).as("%s %s created through the hook", typeName, qualifiedName).isPresent();
        cleanupEntity(guid.get());
        return guid.get();
    }

    @Test
    void createPartialUpdateAndDeleteThroughTheHook() {
        String qualifiedName = name("orders@kafka");
        PublishResult result = hook.createOrUpdate(table(qualifiedName));
        assertThat(result.topic()).isEqualTo("ATLAS_HOOK");
        String guid = awaitGuid(table, qualifiedName);

        AtlasEntity patch = new AtlasEntity();
        patch.setAttribute("rowCount", 5L);
        hook.partialUpdateByQualifiedName(table, qualifiedName, patch);
        Object rowCount = awaitValue(() -> atlas.entities().get(guid).getEntity().getAttribute("rowCount"),
                value -> Objects.equals(value, 5), TIMEOUT);
        assertThat(rowCount).isEqualTo(5);
        assertThat(atlas.entities().get(guid).getEntity().getAttribute("name")).isEqualTo(qualifiedName);

        hook.deleteByQualifiedName(table, qualifiedName);
        EntityStatus status = awaitValue(() -> atlas.entities().get(guid).getEntity().getStatus(),
                s -> s == EntityStatus.DELETED, TIMEOUT);
        assertThat(status).isEqualTo(EntityStatus.DELETED);
    }

    @Test
    void largeMessagesAreSplitAndReassembledByAtlas() {
        Random random = new Random(7);
        for (boolean compression : List.of(true, false)) {
            List<AtlasEntity> entities = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                AtlasEntity entity = table(name("big_" + compression + "_" + i + "@kafka"));
                entity.setAttribute("description", random.ints(400, 'a', 'z' + 1)
                        .mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining()));
                entities.add(entity);
            }
            try (AtlasHookPublisher small = AtlasHookPublisher.builder().bootstrapServers(bootstrapServers)
                    .maxMessageBytes(2000).compression(compression).build()) {
                PublishResult result = small.createOrUpdate(entities);
                assertThat(result.parts()).as("split with compression=%s", compression).isGreaterThan(1);
            }
            awaitGuid(table, name("big_" + compression + "_0@kafka"));
            awaitGuid(table, name("big_" + compression + "_19@kafka"));
        }
    }

    @Test
    void lineageCanBeSentThroughTheHook() {
        String source = name("src@kafka");
        String target = name("dst@kafka");
        AtlasLineage lineage = new AtlasLineage(atlas).missingEntities(MissingEntityPolicy.CREATE);
        hook.createOrUpdate(lineage.process(job, name("load@kafka"))
                .inputs(EntityRef.of(table, source))
                .outputs(EntityRef.of(table, target))
                .toEntities());

        awaitGuid(job, name("load@kafka"));
        awaitGuid(table, source);
        awaitGuid(table, target);
        List<Impact> impact = lineage.impact(EntityRef.of(table, source), 3);
        assertThat(impact).extracting(i -> i.dataset().qualifiedName()).containsExactly(target);
    }

    @Test
    void listenerReceivesChangeEvents() {
        String qualifiedName = name("evt@kafka");
        List<EntityChangeEvent> events = new CopyOnWriteArrayList<>();
        try (AtlasEntityChangeListener listener = AtlasEntityChangeListener.builder()
                .bootstrapServers(bootstrapServers)
                .groupId(name("listener"))
                .startFromEarliest()
                .typeNames(table)
                .filter(event -> qualifiedName.equals(event.qualifiedName()))
                .handler(events::add)
                .build()
                .start()) {

            String guid = atlas.entities().createOrUpdate(table(qualifiedName)).firstGuid().orElseThrow();
            cleanupEntity(guid);
            // Atlas 2.3 publishes no notification for partial updates...
            atlas.entities().updateAttribute(guid, "rowCount", 9L);
            // ...only for full updates.
            AtlasEntity full = table(qualifiedName);
            full.setAttribute("rowCount", 10L);
            atlas.entities().createOrUpdate(full);
            atlas.entities().delete(guid);

            List<OperationType> operations = awaitValue(
                    () -> events.stream().map(EntityChangeEvent::getOperationType).collect(Collectors.toList()),
                    ops -> ops.size() >= 3, TIMEOUT);
            assertThat(operations).containsExactly(OperationType.ENTITY_CREATE, OperationType.ENTITY_UPDATE,
                    OperationType.ENTITY_DELETE);
            assertThat(events.get(0).guid()).isEqualTo(guid);
            assertThat(events.get(0).time()).isNotNull();
            assertThat(listener.failure()).isEmpty();
        }
    }
}
