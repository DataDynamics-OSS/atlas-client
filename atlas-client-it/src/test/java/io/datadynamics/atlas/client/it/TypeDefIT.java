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
 * Type management against a real Atlas: the plan must report no changes right after applying
 * (no false diffs from server defaults), and the changes the planner flags as errors must
 * really be rejected by Atlas.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TypeDefIT extends AtlasIT {

    private static TypeDefManager manager;
    private static String tier, table, column, tableColumns, pii, address, quality;

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

    @Test
    @Order(3)
    void atlasRejectsAddingMandatoryAttribute() {
        TypePlan plan = manager.plan(typesDef(entity(table).dataSet()
                .attribute(attr("mandatory2", STRING).required())));
        assertThat(plan.getErrors()).extracting(TypeIssue::message)
                .containsExactly("new attribute 'mandatory2' must be optional: "
                        + "Atlas rejects adding mandatory attributes to an existing type");

        // Confirm the rule against Atlas itself by sending the update without the planner.
        AtlasEntityDef raw = atlas.types().get(table, AtlasEntityDef.class);
        AtlasAttributeDef mandatory = new AtlasAttributeDef("mandatory2", STRING);
        mandatory.setOptional(false);
        raw.addAttributeDef(mandatory);
        assertThatThrownBy(() -> atlas.types().update(AtlasTypesDef.of(raw)))
                .isInstanceOf(AtlasBadRequestException.class);
    }

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

    @Test
    @Order(6)
    void exportRoundTripsToNoChanges() {
        AtlasTypesDef exported = manager.exportByServiceType("atlas_client_it");
        assertThat(names(exported)).containsExactlyInAnyOrder(table, column, tableColumns);

        TypePlan plan = manager.plan(exported);
        assertThat(plan.getChanges()).as(plan.render())
                .extracting(TypeChange::action).containsOnly(TypeChange.Action.UNCHANGED);
    }

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
