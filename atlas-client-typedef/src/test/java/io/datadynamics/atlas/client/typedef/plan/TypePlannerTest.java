package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import io.datadynamics.atlas.client.typedef.plan.TypeChange.Action;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.typedef.AttributeTypes.INT;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.LONG;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.STRING;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.classification;
import static io.datadynamics.atlas.client.typedef.TypeDefs.end;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static io.datadynamics.atlas.client.typedef.TypeDefs.enumType;
import static io.datadynamics.atlas.client.typedef.TypeDefs.relationship;
import static io.datadynamics.atlas.client.typedef.TypeDefs.typesDef;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TypePlanner}의 비교 로직을 검증합니다. Atlas를 호출하지 않고, 서버 응답을 흉내 낸 JSON
 * ({@link #SERVER_TYPES})과 DSL로 만든 원하는 정의를 비교한 plan의 동작, 세부 내용, 오류, 경고, payload를 확인합니다.
 */
class TypePlannerTest {

    /** Atlas가 돌려주는 모습의 정의입니다. my_table에는 서버가 관리하는 필드와 기본값이 채워져 있습니다. */
    private static final String SERVER_TYPES = "{"
            + "\"entityDefs\":["
            + "{\"category\":\"ENTITY\",\"guid\":\"g-dataset\",\"name\":\"DataSet\",\"superTypes\":[\"Asset\"],\"attributeDefs\":[]},"
            + "{\"category\":\"ENTITY\",\"guid\":\"g-table\",\"name\":\"my_table\",\"createdBy\":\"admin\","
            + "\"createTime\":1700000000000,\"version\":3,\"typeVersion\":\"1.0\",\"description\":\"old\","
            + "\"superTypes\":[\"DataSet\"],\"subTypes\":[],\"relationshipAttributeDefs\":[{\"name\":\"columns\"}],"
            + "\"attributeDefs\":["
            + "{\"name\":\"rowCount\",\"typeName\":\"long\",\"isOptional\":true,\"cardinality\":\"SINGLE\","
            + "\"valuesMinCount\":0,\"valuesMaxCount\":1,\"isUnique\":false,\"isIndexable\":false,\"includeInNotification\":false,"
            + "\"searchWeight\":-1},"
            + "{\"name\":\"legacy\",\"typeName\":\"string\",\"isOptional\":true,\"cardinality\":\"SINGLE\"}]}],"
            + "\"enumDefs\":[{\"category\":\"ENUM\",\"name\":\"tier\",\"elementDefs\":["
            + "{\"value\":\"BRONZE\",\"ordinal\":0},{\"value\":\"SILVER\",\"ordinal\":1}]}],"
            + "\"relationshipDefs\":[{\"category\":\"RELATIONSHIP\",\"name\":\"table_owner\",\"relationshipCategory\":\"ASSOCIATION\","
            + "\"propagateTags\":\"NONE\",\"endDef1\":{\"type\":\"my_table\",\"name\":\"ownerRef\",\"cardinality\":\"SINGLE\",\"isContainer\":false},"
            + "\"endDef2\":{\"type\":\"DataSet\",\"name\":\"ownedTables\",\"cardinality\":\"SET\",\"isContainer\":false}}],"
            + "\"classificationDefs\":[{\"category\":\"CLASSIFICATION\",\"name\":\"PII\",\"attributeDefs\":[]}]"
            + "}";

    /** 테스트마다 새로 파싱한 서버 정의를 돌려줍니다(테스트끼리 객체를 공유하지 않게 합니다). */
    private static AtlasTypesDef server() {
        return AtlasJson.fromJson(SERVER_TYPES, AtlasTypesDef.class);
    }

    /**
     * 서버에 없는 타입은 CREATE가 되고, 세부 내용에 추가될 속성이 나오는지 확인합니다.
     */
    @Test
    void newTypeIsCreated() {
        TypePlan plan = TypePlanner.plan(typesDef(entity("new_table").dataSet().attribute("x", INT)), server());

        TypeChange change = plan.getChanges().get(0);
        assertThat(change.action()).isEqualTo(Action.CREATE);
        assertThat(change.details()).contains("+ attribute 'x' (int)");
        assertThat(plan.hasErrors()).isFalse();
    }

    /**
     * 서버 정의에 guid, 타임스탬프, 속성 기본값 등이 채워져 있어도, 원하는 정의에 설정한 필드가 같으면
     * UNCHANGED이고 문제도 없는지 확인합니다.
     */
    @Test
    void identicalDefinitionIsUnchangedDespiteServerDefaults() {
        TypePlan plan = TypePlanner.plan(typesDef(
                entity("my_table").dataSet().description("old")
                        .attribute(attr("rowCount", LONG))
                        .attribute(attr("legacy", STRING)),
                classification("PII")), server());

        assertThat(plan.getChanges()).extracting(TypeChange::action).containsOnly(Action.UNCHANGED);
        assertThat(plan.getIssues()).isEmpty();
        assertThat(plan.isEmpty()).isTrue();
    }

    /**
     * 설명 변경, 속성 설정 변경, 선택 속성 추가가 UPDATE로 잡히는지 확인합니다. 정의에 없는 서버 속성 legacy는
     * 경고와 함께 유지되고, payload는 서버 guid를 갖고 읽기 전용 필드는 빠지며, 서버에만 있는 속성 값도 남아야 합니다.
     */
    @Test
    void addingOptionalAttributeUpdatesAndKeepsExistingOnes() {
        TypePlan plan = TypePlanner.plan(typesDef(
                entity("my_table").dataSet().description("new")
                        .attribute(attr("rowCount", LONG).indexable())
                        .attribute(attr("owner2", STRING))), server());

        TypeChange change = plan.getChanges().get(0);
        assertThat(change.action()).isEqualTo(Action.UPDATE);
        assertThat(change.details()).containsExactlyInAnyOrder(
                "~ description: \"old\" -> \"new\"",
                "~ attribute 'rowCount' isIndexable: false -> true",
                "+ attribute 'owner2' (string)");
        assertThat(plan.getWarnings()).extracting(TypeIssue::message)
                .anyMatch(m -> m.contains("'legacy' exists in Atlas but not in the definition"));

        AtlasEntityDef payload = (AtlasEntityDef) change.payload();
        assertThat(payload.getGuid()).isEqualTo("g-table");
        assertThat(payload.getDescription()).isEqualTo("new");
        assertThat(payload.getRelationshipAttributeDefs()).isNull();
        assertThat(payload.getAttributeDefs()).extracting(a -> a.getName())
                .containsExactly("rowCount", "legacy", "owner2");
        assertThat(payload.findAttributeDef("rowCount").orElseThrow().getIndexable()).isTrue();
        // 서버에만 있던 속성 값도 병합 후 그대로 남아야 합니다.
        assertThat(payload.findAttributeDef("rowCount").orElseThrow().getSearchWeight()).isEqualTo(-1);
    }

    /**
     * 기존 타입에 필수 속성을 추가하면 오류가 되는지 확인합니다.
     */
    @Test
    void addingMandatoryAttributeIsAnError() {
        TypePlan plan = TypePlanner.plan(typesDef(
                entity("my_table").dataSet().description("old")
                        .attribute(attr("rowCount", LONG)).attribute(attr("legacy", STRING))
                        .attribute(attr("mandatory", STRING).required())), server());

        assertThat(errors(plan)).containsExactly(
                "new attribute 'mandatory' must be optional: Atlas rejects adding mandatory attributes to an existing type");
    }

    /**
     * 기존 속성의 타입을 바꾸면(long에서 int로) 오류가 되는지 확인합니다.
     */
    @Test
    void changingAttributeTypeIsAnError() {
        TypePlan plan = TypePlanner.plan(typesDef(
                entity("my_table").dataSet().attribute(attr("rowCount", INT)).attribute(attr("legacy", STRING))),
                server());

        assertThat(errors(plan)).containsExactly("attribute 'rowCount': type cannot be changed (\"long\" -> \"int\")");
    }

    /**
     * ENTITY로 존재하는 이름을 classification으로 다시 정의하면 오류가 되는지 확인합니다.
     */
    @Test
    void redefiningUnderAnotherCategoryIsAnError() {
        TypePlan plan = TypePlanner.plan(typesDef(classification("my_table")), server());

        assertThat(errors(plan)).containsExactly("already exists as ENTITY, cannot be defined as CLASSIFICATION");
    }

    /**
     * relationship 끝점의 속성 이름(name)을 바꾸면 오류가 되는지 확인합니다.
     */
    @Test
    void relationshipEndChangesAreErrors() {
        TypePlan plan = TypePlanner.plan(typesDef(relationship("table_owner")
                .end1(end("my_table", "owner"))
                .end2(end("DataSet", "ownedTables").set())), server());

        assertThat(errors(plan)).containsExactly("endDef1 name cannot be changed (\"ownerRef\" -> \"owner\")");
    }

    /**
     * relationship 끝점의 cardinality를 바꾸면(SET에서 SINGLE로) 오류가 되는지 확인합니다.
     */
    @Test
    void relationshipEndCardinalityChangeIsAnError() {
        TypePlan plan = TypePlanner.plan(typesDef(relationship("table_owner")
                .end1(end("my_table", "ownerRef"))
                .end2(end("DataSet", "ownedTables"))), server());

        assertThat(errors(plan)).containsExactly("endDef2 cardinality cannot be changed (\"SET\" -> \"SINGLE\")");
    }

    /**
     * propagateTags 변경은 허용되는 수정이므로 오류 없이 세부 내용 하나로 잡히는지 확인합니다.
     */
    @Test
    void relationshipPropagationChangeIsAnUpdate() {
        TypePlan plan = TypePlanner.plan(typesDef(relationship("table_owner")
                .end1(end("my_table", "ownerRef"))
                .end2(end("DataSet", "ownedTables").set())
                .propagateTags(io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.PropagateTags.ONE_TO_TWO)), server());

        assertThat(plan.hasErrors()).isFalse();
        assertThat(plan.getChanges().get(0).details()).containsExactly("~ propagateTags: \"NONE\" -> \"ONE_TO_TWO\"");
    }

    /**
     * enum 값 추가와 삭제가 세부 내용에 나오고, 삭제에 대한 경고가 하나 생기는지 확인합니다.
     */
    @Test
    void enumValuesAddedAndRemoved() {
        TypePlan plan = TypePlanner.plan(typesDef(enumType("tier").values("BRONZE", "GOLD")), server());

        assertThat(plan.getChanges().get(0).details()).containsExactly("+ value 'GOLD'", "- value 'SILVER'");
        assertThat(plan.getWarnings()).hasSize(1);
    }

    /**
     * 존재하지 않는 상위 타입과, 컨테이너 타입 안에서 참조한 존재하지 않는 타입이 모두 오류가 되는지 확인합니다.
     */
    @Test
    void unknownReferencesAreErrors() {
        TypePlan plan = TypePlanner.plan(typesDef(
                entity("t2").superTypes("Missing").attribute("cols", "array<missing_column>")), server());

        assertThat(errors(plan)).containsExactlyInAnyOrder(
                "superType refers to unknown type 'Missing'",
                "attribute 'cols' refers to unknown type 'missing_column'");
    }

    /**
     * {@link TypePlan#render()} 출력에 변경 줄, 세부 내용, 요약 줄이 정해진 형식으로 들어가는지 확인합니다.
     */
    @Test
    void renderShowsChangesAndSummary() {
        TypePlan plan = TypePlanner.plan(typesDef(
                entity("new_table").dataSet().attribute("x", INT),
                classification("PII")), server());

        String rendered = plan.render();
        assertThat(rendered)
                .contains("+ create    ENTITY            new_table")
                .contains("+ attribute 'x' (int)")
                .contains("= unchanged CLASSIFICATION    PII")
                .contains("Plan: 1 to create, 0 to update, 0 to delete, 1 unchanged; 0 errors, 0 warnings");
    }

    /** plan의 오류 메시지만 뽑아 비교하기 쉽게 합니다. */
    private static List<String> errors(TypePlan plan) {
        return plan.getErrors().stream().map(TypeIssue::message).collect(Collectors.toList());
    }
}
