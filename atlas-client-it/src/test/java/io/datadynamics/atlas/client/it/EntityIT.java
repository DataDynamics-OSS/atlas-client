package io.datadynamics.atlas.client.it;

import io.datadynamics.atlas.client.exception.AtlasServerException;
import io.datadynamics.atlas.client.model.instance.AtlasClassification;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityWithExtInfo;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import io.datadynamics.atlas.client.model.instance.EntityStatus;
import io.datadynamics.atlas.client.model.search.FilterCriteria;
import io.datadynamics.atlas.client.model.search.FilterCriteria.Operator;
import io.datadynamics.atlas.client.model.search.SearchParameters;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.typedef.AttributeTypes.LONG;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.classification;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Atlas를 상대로 엔티티 작업(생성·수정, 부분 수정, classification과 레이블, 검색, 삭제)을 검증합니다.
 * 엔티티는 주로 qualifiedName으로 지정합니다.
 */
class EntityIT extends AtlasIT {

    /** 테스트용 데이터셋 entity 타입 이름입니다. */
    private static String table;
    /** 테스트용 classification 타입 이름입니다. */
    private static String sensitive;

    /**
     * 검색 필터에 쓸 수 있도록 색인(indexable)된 {@code rowCount} 속성을 가진 데이터셋 타입과,
     * classification 타입 하나를 만듭니다.
     */
    @BeforeAll
    static void createTypes() {
        table = name("table");
        sensitive = name("sensitive");
        cleanupTypes(sensitive, table);
        new TypeDefManager(atlas).sync(
                entity(table).dataSet().attribute(attr("rowCount", LONG).indexable()),
                classification(sensitive));
    }

    /** qualifiedName과 name이 같은 테스트 테이블 엔티티를 만듭니다(아직 Atlas에 보내지 않음). */
    private static AtlasEntity table(String qualifiedName) {
        return new AtlasEntity(table, Map.of("qualifiedName", qualifiedName, "name", qualifiedName));
    }

    /**
     * 같은 qualifiedName으로 두 번 createOrUpdate하면 처음에는 생성, 두 번째에는 같은 guid의 엔티티가 수정되어야
     * 합니다. 수정한 rowCount(long 42)는 조회 시 JSON 숫자로 읽혀 Integer 42와 같아야 합니다.
     */
    @Test
    void upsertByQualifiedName() {
        String qualifiedName = name("orders@it");
        EntityMutationResponse created = atlas.entities().createOrUpdate(table(qualifiedName));
        String guid = created.findGuid(table, qualifiedName).orElseThrow();
        cleanupEntity(guid);
        assertThat(created.created()).hasSize(1);

        AtlasEntity again = table(qualifiedName);
        again.setAttribute("rowCount", 42L);
        EntityMutationResponse updated = atlas.entities().createOrUpdate(again);
        assertThat(updated.created()).isEmpty();
        assertThat(updated.firstGuid()).contains(guid);

        assertThat(atlas.entities().findGuid(table, qualifiedName)).contains(guid);
        assertThat(atlas.entities().getByQualifiedName(table, qualifiedName).getEntity().getAttribute("rowCount"))
                .isEqualTo(42);
    }

    /**
     * qualifiedName 기반 부분 수정과 guid 기반 단일 속성 수정을 한 뒤, 수정한 속성은 바뀌고 건드리지 않은
     * description은 그대로 남아 있어야 합니다.
     */
    @Test
    void partialUpdatesLeaveOtherAttributesAlone() {
        String qualifiedName = name("customers@it");
        AtlasEntity entity = table(qualifiedName);
        entity.setAttribute("description", "customers");
        String guid = atlas.entities().createOrUpdate(entity).firstGuid().orElseThrow();
        cleanupEntity(guid);

        AtlasEntity patch = new AtlasEntity(table);
        patch.setAttribute("rowCount", 7L);
        atlas.entities().partialUpdateByQualifiedName(table, qualifiedName, patch);
        atlas.entities().updateAttribute(guid, "owner", "it-team");

        AtlasEntity stored = atlas.entities().get(guid).getEntity();
        assertThat(stored.getAttribute("rowCount")).isEqualTo(7);
        assertThat(stored.getAttribute("owner")).isEqualTo("it-team");
        assertThat(stored.getAttribute("description")).isEqualTo("customers");
    }

    /**
     * classification을 추가·조회·제거하고, 레이블을 설정·추가합니다. 제거 후에는 classification이 없어야 하고,
     * 레이블은 설정한 것과 추가한 것이 모두 있어야 합니다.
     */
    @Test
    void classificationsAndLabels() {
        String qualifiedName = name("payments@it");
        String guid = atlas.entities().createOrUpdate(table(qualifiedName)).firstGuid().orElseThrow();
        cleanupEntity(guid);

        atlas.entities().addClassification(guid, new AtlasClassification(sensitive));
        assertThat(atlas.entities().getClassifications(guid))
                .extracting(AtlasClassification::getTypeName).containsExactly(sensitive);
        // Atlas 2.x는 classification 전파를 백그라운드 작업으로 처리하며, 그 작업이 도는 동안 들어온 삭제 요청에는
        // 406(ATLAS-406-00-001)으로 응답합니다. 작업이 끝날 때까지 다시 시도합니다. 406이 아닌 오류는 바로 실패입니다.
        boolean removed = awaitValue(() -> {
            try {
                atlas.entities().removeClassification(guid, sensitive);
                return true;
            } catch (AtlasServerException e) {
                assertThat(e.getStatusCode()).isEqualTo(406);
                return false;
            }
        }, done -> done, Duration.ofSeconds(30));
        assertThat(removed).isTrue();
        assertThat(atlas.entities().getClassifications(guid)).isEmpty();

        atlas.entities().setLabels(guid, Set.of("gold", "finance"));
        atlas.entities().addLabels(guid, Set.of("audited"));
        assertThat(atlas.entities().get(guid).getEntity().getLabels())
                .containsExactlyInAnyOrder("gold", "finance", "audited");
    }

    /**
     * 엔티티 25개를 만들고 rowCount 필터로 basic search를 페이지 크기 10으로 스트리밍하면, 여러 페이지에 걸친
     * 결과 25개가 중복 없이 모두 나와야 합니다.
     */
    @Test
    void searchStreamsAllPages() {
        List<AtlasEntity> entities = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            AtlasEntity entity = table(name("paged_" + i + "@it"));
            entity.setAttribute("rowCount", 1000L + i);
            entities.add(entity);
        }
        EntityMutationResponse response = atlas.entities().createOrUpdate(entities);
        response.created().forEach(h -> cleanupEntity(h.getGuid()));

        SearchParameters params = SearchParameters.forType(table);
        params.setEntityFilters(FilterCriteria.of("rowCount", Operator.GTE, 1000));

        // 검색 색인은 비동기로 갱신되므로, 모든 엔티티가 검색될 때까지 기다립니다.
        List<String> found = awaitValue(() -> atlas.search().basicStream(params, 10)
                .map(h -> h.qualifiedName())
                .collect(Collectors.toList()), list -> list.size() == 25, Duration.ofSeconds(60));
        assertThat(found).hasSize(25).doesNotHaveDuplicates();
    }

    /**
     * qualifiedName으로 삭제하면 엔티티는 DELETED 상태로 남고(soft delete), purge한 뒤에는 guid로도 찾을 수
     * 없어야 합니다.
     */
    @Test
    void softDeleteThenPurge() {
        String qualifiedName = name("tmp@it");
        String guid = atlas.entities().createOrUpdate(table(qualifiedName)).firstGuid().orElseThrow();

        atlas.entities().deleteByQualifiedName(table, qualifiedName);
        AtlasEntityWithExtInfo deleted = atlas.entities().get(guid);
        assertThat(deleted.getEntity().getStatus()).isEqualTo(EntityStatus.DELETED);

        atlas.admin().purge(List.of(guid));
        assertThat(atlas.entities().find(guid)).isEmpty();
    }
}
