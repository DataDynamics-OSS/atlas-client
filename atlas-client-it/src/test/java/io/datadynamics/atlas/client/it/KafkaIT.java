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
 * 실제 Atlas가 쓰는 Kafka를 통해 hook 메시지 발행과 변경 알림 수신을 검증합니다.
 * <p>
 * Atlas는 hook 메시지를 비동기로 처리하므로, 모든 확인은 변경이 보일 때까지 반복 조회하며 기다립니다.
 * 테스트 서버의 Kafka 주소를 모르면 클래스 전체를 건너뜁니다.
 */
class KafkaIT extends AtlasIT {

    /** hook 메시지가 반영되거나 알림이 도착하기를 기다리는 최대 시간입니다. */
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private static String bootstrapServers;
    /** 테스트용 타입 이름입니다. */
    private static String table;
    private static String job;
    /** 클래스 전체에서 공유하는 hook 발행기입니다. */
    private static AtlasHookPublisher hook;

    /**
     * Kafka 주소를 확인하고(없으면 건너뜀), rowCount 속성을 가진 데이터셋 타입과 프로세스 타입을 만든 뒤,
     * 사용자 {@code it}로 메시지를 보내는 hook 발행기를 만듭니다.
     */
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

    /** hook 발행기를 닫습니다. 테스트를 건너뛰어 만들지 않았으면 아무것도 하지 않습니다. */
    @AfterAll
    static void closeHook() {
        if (hook != null) {
            hook.close();
        }
    }

    /** qualifiedName과 name이 같은 테스트 테이블 엔티티를 만듭니다. */
    private static AtlasEntity table(String qualifiedName) {
        return new AtlasEntity(table, Map.of("qualifiedName", qualifiedName, "name", qualifiedName));
    }

    /**
     * hook으로 보낸 엔티티가 Atlas에 생길 때까지 기다린 뒤 guid를 돌려주고, 정리 대상으로 등록합니다.
     * 제한 시간 안에 생기지 않으면 테스트가 실패합니다.
     */
    private static String awaitGuid(String typeName, String qualifiedName) {
        Optional<String> guid = awaitValue(() -> atlas.entities().findGuid(typeName, qualifiedName),
                Optional::isPresent, TIMEOUT);
        assertThat(guid).as("%s %s created through the hook", typeName, qualifiedName).isPresent();
        cleanupEntity(guid.get());
        return guid.get();
    }

    /**
     * hook으로 생성, qualifiedName 기반 부분 수정, 삭제를 차례로 보냅니다. 메시지는 ATLAS_HOOK 토픽으로 가야 하고,
     * 각 단계가 Atlas에 반영되어야 합니다. 부분 수정 후에도 건드리지 않은 name은 그대로여야 합니다.
     */
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

    /**
     * 최대 메시지 크기를 2000바이트로 줄인 발행기로 큰 메시지(설명 400자짜리 엔티티 20개)를 보내면, 압축을 켠 경우와
     * 끈 경우 모두 여러 조각으로 나뉘어야 합니다. Atlas가 조각을 다시 합쳐 첫 엔티티와 마지막 엔티티를 모두
     * 만들어야 합니다.
     */
    @Test
    void largeMessagesAreSplitAndReassembledByAtlas() {
        // 압축해도 크기가 충분히 크게 남도록 무작위 문자열을 쓰며, 실행마다 같은 결과가 나오도록 시드를 고정합니다.
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

    /**
     * {@link AtlasLineage}로 만든 프로세스와 데이터셋 엔티티를 REST 대신 hook으로 보내도 리니지가 등록되어야 하고,
     * 원천 데이터셋의 영향도에 대상 데이터셋이 나와야 합니다.
     */
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

    /**
     * 변경 알림 listener가 생성, 수정, 삭제 이벤트를 순서대로 받아야 합니다. qualifiedName 필터로 이 테스트의
     * 엔티티 이벤트만 모으며, 첫 이벤트는 생성한 엔티티의 guid와 시간을 담고 있어야 하고 처리 실패가 없어야 합니다.
     */
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
            // Atlas 2.3은 부분 수정에 대해서는 알림을 발행하지 않고...
            atlas.entities().updateAttribute(guid, "rowCount", 9L);
            // ...전체 수정에 대해서만 발행합니다. 그래서 수정 이벤트는 하나만 와야 합니다.
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
