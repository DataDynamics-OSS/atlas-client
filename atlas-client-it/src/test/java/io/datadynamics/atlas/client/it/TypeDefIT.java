package io.datadynamics.atlas.client.it;

import io.datadynamics.atlas.client.exception.AtlasBadRequestException;
import io.datadynamics.atlas.client.exception.AtlasServerException;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import io.datadynamics.atlas.client.typedef.plan.TypeApplyResult;
import io.datadynamics.atlas.client.typedef.plan.TypeChange;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import io.datadynamics.atlas.client.typedef.plan.TypePlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static io.datadynamics.atlas.client.typedef.AttributeTypes.INT;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.LONG;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.STRING;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.array;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.businessMetadata;
import static io.datadynamics.atlas.client.typedef.TypeDefs.classification;
import static io.datadynamics.atlas.client.typedef.TypeDefs.end;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static io.datadynamics.atlas.client.typedef.TypeDefs.enumType;
import static io.datadynamics.atlas.client.typedef.TypeDefs.relationship;
import static io.datadynamics.atlas.client.typedef.TypeDefs.struct;
import static io.datadynamics.atlas.client.typedef.TypeDefs.typesDef;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 Atlas를 상대로 타입 관리 기능을 검증합니다.
 * <p>
 * apply 직후 다시 plan하면 변경이 없어야 합니다(서버가 채우는 기본값 때문에 생기는 가짜 차이가 없어야 함).
 * 또한 planner가 오류로 표시하는 변경은 Atlas도 실제로 거부해야 합니다.
 * <p>
 * 테스트는 앞 테스트가 만든 타입을 이어서 쓰므로 {@link Order} 순서대로 실행됩니다.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TypeDefIT extends AtlasIT {

    private static TypeDefManager manager;
    /** 테스트용 타입 이름입니다. 실행마다 고유한 접두어가 붙습니다. */
    private static String tier, table, column, tableColumns, pii, address, quality;

    /** 타입 이름을 정하고 정리 대상으로 등록합니다. 타입은 첫 테스트에서 만듭니다. */
    @BeforeAll
    static void setUp() {
        manager = new TypeDefManager(atlas);
        tier = name("tier");
        table = name("table");
        column = name("column");
        tableColumns = name("table_columns");
        pii = name("pii");
        address = name("address");
        quality = name("quality");
        cleanupTypes(tableColumns, quality, pii, column, table, address, tier);
    }

    /**
     * 기준이 되는 타입 모델 7개를 만듭니다: enum, struct, 테이블과 컬럼 entity, 둘을 잇는 composition
     * relationship, 컬럼에 붙이는 classification, 테이블에 붙이는 business metadata입니다.
     * 테이블·컬럼·relationship에만 serviceType {@code atlas_client_it}를 지정합니다(export 테스트에서 씁니다).
     */
    private static AtlasTypesDef model() {
        return typesDef(
                enumType(tier).values("BRONZE", "SILVER", "GOLD").defaultValue("BRONZE"),
                struct(address).attribute(attr("city", STRING)).attribute(attr("zip", STRING)),
                entity(table).dataSet().description("IT table").serviceType("atlas_client_it")
                        .attribute(attr("tier", tier).indexable())
                        .attribute(attr("rowCount", LONG))
                        .attribute(attr("tags", array(STRING)))
                        .attribute(attr("location", address))
                        .attribute(attr("format", STRING).required().defaultValue("parquet")),
                entity(column).dataSet().serviceType("atlas_client_it")
                        .attribute(attr("dataType", STRING).required())
                        .attribute(attr("position", INT)),
                relationship(tableColumns).composition().serviceType("atlas_client_it")
                        .end1(end(table, "columns").set().container())
                        .end2(end(column, "table")),
                classification(pii).entityTypes(column).attribute(attr("level", INT)),
                businessMetadata(quality).applicableTo(table)
                        .attribute(attr("score", INT))
                        .attribute(attr("steward", STRING)));
    }

    /** 처음 plan하면 모두 CREATE여야 하고, apply로 7개를 만든 직후 다시 plan하면 모두 UNCHANGED이고 이슈가 없어야 합니다. */
    @Test
    @Order(1)
    void createsAllTypesThenReportsNoChanges() {
        TypePlan first = manager.plan(model());
        assertThat(first.getErrors()).isEmpty();
        assertThat(first.getChanges()).extracting(TypeChange::action).containsOnly(TypeChange.Action.CREATE);

        TypeApplyResult result = manager.apply(first);
        assertThat(result.created()).hasSize(7);

        TypePlan second = manager.plan(model());
        assertThat(second.getChanges())
                .as("re-planning right after apply must not find differences:\n%s", second.render())
                .extracting(TypeChange::action).containsOnly(TypeChange.Action.UNCHANGED);
        assertThat(second.getIssues()).as(second.render()).isEmpty();
    }

    /**
     * 선택 속성 하나만 추가하는 정의로 plan하면 추가가 보이고, 정의에 없는 기존 속성에 대해서는 경고만 나와야
     * 합니다. apply 후에도 기존 속성과 설명은 그대로 남아 있어야 합니다(속성을 지우지 않음).
     */
    @Test
    @Order(2)
    void addingOptionalAttributeKeepsExistingOnes() {
        AtlasTypesDef desired = typesDef(entity(table).dataSet()
                .attribute(attr("tier", tier).indexable())
                .attribute(attr("comment", STRING)));

        TypePlan plan = manager.plan(desired);
        assertThat(plan.getErrors()).isEmpty();
        assertThat(plan.getChanges().get(0).details()).contains("+ attribute 'comment' (string)");
        assertThat(plan.getWarnings()).extracting(TypeIssue::message)
                .anyMatch(m -> m.contains("'rowCount' exists in Atlas but not in the definition"));
        manager.apply(plan);

        AtlasEntityDef stored = atlas.types().get(table, AtlasEntityDef.class);
        assertThat(stored.getAttributeDefs()).extracting(AtlasAttributeDef::getName)
                .contains("tier", "rowCount", "tags", "location", "format", "comment");
        assertThat(stored.getDescription()).isEqualTo("IT table");
    }

    /** 기존 타입에 필수 속성을 추가하면 planner가 오류로 보고하고, planner 없이 보내면 Atlas도 400으로 거부해야 합니다. */
    @Test
    @Order(3)
    void atlasRejectsAddingMandatoryAttribute() {
        TypePlan plan = manager.plan(typesDef(entity(table).dataSet()
                .attribute(attr("mandatory2", STRING).required())));
        assertThat(plan.getErrors()).extracting(TypeIssue::message)
                .containsExactly("new attribute 'mandatory2' must be optional: "
                        + "Atlas rejects adding mandatory attributes to an existing type");

        // planner를 거치지 않고 수정 요청을 보내 Atlas 자체도 같은 규칙을 적용하는지 확인합니다.
        AtlasEntityDef raw = atlas.types().get(table, AtlasEntityDef.class);
        AtlasAttributeDef mandatory = new AtlasAttributeDef("mandatory2", STRING);
        mandatory.setOptional(false);
        raw.addAttributeDef(mandatory);
        assertThatThrownBy(() -> atlas.types().update(AtlasTypesDef.of(raw)))
                .isInstanceOf(AtlasBadRequestException.class);
    }

    /** 속성 타입을 바꾸면(long → int) planner가 오류로 보고하고, planner 없이 보내면 Atlas도 거부해야 합니다. */
    @Test
    @Order(4)
    void atlasRejectsChangingAttributeType() {
        TypePlan plan = manager.plan(typesDef(entity(table).dataSet().attribute(attr("rowCount", INT))));
        assertThat(plan.getErrors()).extracting(TypeIssue::message)
                .containsExactly("attribute 'rowCount': type cannot be changed (\"long\" -> \"int\")");

        AtlasEntityDef raw = atlas.types().get(table, AtlasEntityDef.class);
        raw.findAttributeDef("rowCount").orElseThrow().setTypeName(INT);
        assertThatThrownBy(() -> atlas.types().update(AtlasTypesDef.of(raw)))
                .isInstanceOf(AtlasServerException.class);
    }

    /** relationship end의 이름을 바꾸면 planner가 오류로 보고하고, planner 없이 보내면 Atlas도 거부해야 합니다. */
    @Test
    @Order(5)
    void atlasRejectsRenamingRelationshipEnd() {
        TypePlan plan = manager.plan(typesDef(relationship(tableColumns).composition()
                .end1(end(table, "cols").set().container())
                .end2(end(column, "table"))));
        assertThat(plan.getErrors()).extracting(TypeIssue::message)
                .containsExactly("endDef1 name cannot be changed (\"columns\" -> \"cols\")");

        AtlasRelationshipDef raw = atlas.types().get(tableColumns, AtlasRelationshipDef.class);
        raw.getEndDef1().setName("cols");
        assertThatThrownBy(() -> atlas.types().update(AtlasTypesDef.of(raw)))
                .isInstanceOf(AtlasServerException.class);
    }

    /** serviceType으로 export한 정의(테이블, 컬럼, relationship)를 그대로 plan하면 변경이 없어야 합니다. */
    @Test
    @Order(6)
    void exportRoundTripsToNoChanges() {
        AtlasTypesDef exported = manager.exportByServiceType("atlas_client_it");
        assertThat(names(exported)).containsExactlyInAnyOrder(table, column, tableColumns);

        TypePlan plan = manager.plan(exported);
        assertThat(plan.getChanges()).as(plan.render())
                .extracting(TypeChange::action).containsOnly(TypeChange.Action.UNCHANGED);
    }

    /**
     * 다른 타입이 참조하는 컬럼 타입만 삭제하려 하면 relationship과 classification의 참조가 오류로 보고되어야 합니다.
     * 참조하는 타입까지 모두 함께 삭제하면 7개가 모두 지워져야 합니다.
     */
    @Test
    @Order(7)
    void deletePlanBlocksReferencedTypesThenDeletesAll() {
        TypePlan blocked = manager.planDelete(List.of(column));
        assertThat(blocked.getErrors()).extracting(TypeIssue::message)
                .anyMatch(m -> m.contains("still referenced by RELATIONSHIP " + tableColumns))
                .anyMatch(m -> m.contains("still referenced by CLASSIFICATION " + pii));

        TypeApplyResult deleted = manager.delete(List.of(tableColumns, quality, pii, column, table, address, tier));
        assertThat(deleted.deleted()).hasSize(7);
        assertThat(atlas.types().exists(table)).isFalse();
    }
}
