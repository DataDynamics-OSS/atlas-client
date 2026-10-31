package io.datadynamics.atlas.client.json;

import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import io.datadynamics.atlas.client.model.instance.EntityStatus;
import io.datadynamics.atlas.client.model.search.FilterCriteria;
import io.datadynamics.atlas.client.model.search.FilterCriteria.Operator;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.model.typedef.Cardinality;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AtlasJson}의 Jackson 설정이 Atlas JSON 형식과 맞는지 검증합니다.
 * <p>
 * boolean 속성 이름({@code isOptional} 등), 모르는 필드 보존, 모르는 enum 값 처리, 필터 연산자 기호,
 * 시간 값의 밀리초 표현, 변경 응답 도우미, 엔티티 참조 직렬화를 확인합니다.
 */
class AtlasJsonTest {

    /**
     * 속성 정의의 boolean 필드가 Atlas 이름({@code isOptional}, {@code isUnique}, {@code isIndexable})으로
     * 직렬화되는지 확인합니다.
     * <p>
     * 준비: 몇몇 boolean 값만 설정한 속성 정의 → 실행: JSON으로 직렬화 → 기대: {@code is...} 이름으로 쓰이고,
     * Java 필드 이름({@code optional})이나 설정하지 않은 {@code null} 필드({@code description})는 나오지 않습니다.
     */
    @Test
    void attributeDefUsesAtlasBooleanNames() {
        AtlasAttributeDef def = new AtlasAttributeDef("owner", "string");
        def.setOptional(true);
        def.setUnique(false);
        def.setIndexable(true);
        def.setCardinality(Cardinality.SINGLE);

        String json = AtlasJson.toJson(def);

        assertThat(json)
                .contains("\"isOptional\":true")
                .contains("\"isUnique\":false")
                .contains("\"isIndexable\":true")
                .doesNotContain("\"optional\"")
                .doesNotContain("\"description\"");
    }

    /**
     * 이 클라이언트가 모르는 필드가 읽기 → 쓰기 과정에서 사라지지 않는지 확인합니다.
     * <p>
     * 준비: 모르는 필드({@code futureField}, {@code newFlag})가 섞인 typedef JSON → 실행: 읽은 뒤 다시 직렬화 →
     * 기대: 아는 필드는 올바르게 읽히고(시간은 epoch 밀리초), 다시 쓴 JSON에 모르는 필드와 원래 시간 값이 그대로
     * 남아 있습니다.
     */
    @Test
    void typedefRoundTripKeepsUnknownFields() {
        String json = "{\"entityDefs\":[{\"category\":\"ENTITY\",\"name\":\"my_table\",\"superTypes\":[\"DataSet\"],"
                + "\"createTime\":1700000000123,\"futureField\":{\"a\":1},"
                + "\"attributeDefs\":[{\"name\":\"owner\",\"typeName\":\"string\",\"isOptional\":true,\"newFlag\":true}]}]}";

        AtlasTypesDef typesDef = AtlasJson.fromJson(json, AtlasTypesDef.class);
        AtlasEntityDef entityDef = typesDef.getEntityDefs().get(0);

        assertThat(entityDef.getName()).isEqualTo("my_table");
        assertThat(entityDef.getSuperTypes()).containsExactly("DataSet");
        assertThat(entityDef.getCreateTime()).isEqualTo(Instant.ofEpochMilli(1700000000123L));
        assertThat(entityDef.getAttributeDefs().get(0).getOptional()).isTrue();

        String written = AtlasJson.toJson(typesDef);
        assertThat(written)
                .contains("\"futureField\":{\"a\":1}")
                .contains("\"newFlag\":true")
                .contains("\"createTime\":1700000000123");
    }

    /**
     * 모르는 enum 값이 와도 역직렬화가 실패하지 않는지 확인합니다.
     * <p>
     * 준비·실행: 모르는 {@code status} 값과 아는 값으로 각각 엔티티를 읽음 → 기대: 모르는 값은 {@code null},
     * 아는 값은 해당 enum 상수가 됩니다.
     */
    @Test
    void unknownEnumValuesDoNotFail() {
        AtlasEntity entity = AtlasJson.fromJson("{\"typeName\":\"t\",\"status\":\"SOMETHING_NEW\"}", AtlasEntity.class);
        assertThat(entity.getStatus()).isNull();

        AtlasEntity active = AtlasJson.fromJson("{\"typeName\":\"t\",\"status\":\"ACTIVE\"}", AtlasEntity.class);
        assertThat(active.getStatus()).isEqualTo(EntityStatus.ACTIVE);
    }

    /**
     * 필터 연산자가 Atlas 기호로 직렬화되고, 다른 기호로도 읽히는지 확인합니다.
     * <p>
     * 준비: EQ와 STARTS_WITH 조건을 AND로 묶음 → 실행: 직렬화, 그리고 {@code lt} 기호를 담은 JSON 역직렬화 →
     * 기대: {@code =}, {@code startsWith}, {@code AND}로 쓰이고, {@code lt}는 {@link Operator#LT}로 읽힙니다.
     */
    @Test
    void filterOperatorsUseAtlasSymbols() {
        FilterCriteria criteria = FilterCriteria.and(
                FilterCriteria.of("owner", Operator.EQ, "kim"),
                FilterCriteria.of("name", Operator.STARTS_WITH, "sales_"));

        String json = AtlasJson.toJson(criteria);
        assertThat(json).contains("\"operator\":\"=\"").contains("\"operator\":\"startsWith\"")
                .contains("\"condition\":\"AND\"");

        FilterCriteria parsed = AtlasJson.fromJson("{\"attributeName\":\"x\",\"operator\":\"lt\",\"attributeValue\":\"1\"}",
                FilterCriteria.class);
        assertThat(parsed.getOperator()).isEqualTo(Operator.LT);
    }

    /**
     * {@link EntityMutationResponse}의 도우미 메서드를 확인합니다.
     * <p>
     * 준비: CREATE 1건, UPDATE 1건과 guid 대응을 담은 응답 JSON → 실행: 읽은 뒤 도우미 호출 → 기대: 작업별 목록,
     * qualifiedName으로 guid 찾기, guid 대응, 요약이 올바르고, 도우미 메서드가 JSON 속성(예: {@code created})으로
     * 직렬화되지 않습니다.
     */
    @Test
    void mutationResponseHelpers() {
        String json = "{\"mutatedEntities\":{"
                + "\"CREATE\":[{\"typeName\":\"hive_table\",\"guid\":\"g1\",\"attributes\":{\"qualifiedName\":\"db.t1@c\"}}],"
                + "\"UPDATE\":[{\"typeName\":\"hive_db\",\"guid\":\"g2\",\"attributes\":{\"qualifiedName\":\"db@c\"}}]},"
                + "\"guidAssignments\":{\"-100\":\"g1\"}}";

        EntityMutationResponse response = AtlasJson.fromJson(json, EntityMutationResponse.class);

        assertThat(response.created()).hasSize(1);
        assertThat(response.updated()).hasSize(1);
        assertThat(response.deleted()).isEmpty();
        assertThat(response.findGuid("hive_db", "db@c")).contains("g2");
        assertThat(response.getGuidAssignments()).containsEntry("-100", "g1");
        assertThat(response.summary()).containsEntry(EntityMutationResponse.EntityOperation.CREATE, 1);
        assertThat(AtlasJson.toJson(response)).doesNotContain("\"created\"");
    }

    /**
     * 엔티티 참조가 guid 유무에 따라 올바르게 만들어지는지 확인합니다.
     * <p>
     * 준비: guid 없는 엔티티 → 실행: 참조 생성, 이어서 임시 guid 부여 후 다시 참조 생성 → 기대: 처음에는
     * typeName과 uniqueAttributes(qualifiedName)만 담긴 참조가 되고, 임시 guid는 {@code "-"}로 시작하며 Atlas가
     * 부여한 guid로 보지 않습니다.
     */
    @Test
    void entityReferencesByQualifiedName() {
        AtlasEntity table = new AtlasEntity("hive_table", Map.of("qualifiedName", "db.t@c", "name", "t"));

        assertThat(AtlasJson.toJson(table.toObjectId()))
                .isEqualTo("{\"typeName\":\"hive_table\",\"uniqueAttributes\":{\"qualifiedName\":\"db.t@c\"}}");

        table.assignPlaceholderGuid();
        assertThat(table.getGuid()).startsWith("-");
        assertThat(table.toObjectId().hasAssignedGuid()).isFalse();
    }
}
