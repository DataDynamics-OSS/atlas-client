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

class TypePlannerTest {

    /** What Atlas returns for my_table: server-managed fields and defaults filled in. */
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

    private static AtlasTypesDef server() {
        return AtlasJson.fromJson(SERVER_TYPES, AtlasTypesDef.class);
    }

    @Test
    void newTypeIsCreated() {
        TypePlan plan = TypePlanner.plan(typesDef(entity("new_table").dataSet().attribute("x", INT)), server());

        TypeChange change = plan.getChanges().get(0);
        assertThat(change.action()).isEqualTo(Action.CREATE);
        assertThat(change.details()).contains("+ attribute 'x' (int)");
        assertThat(plan.hasErrors()).isFalse();
    }

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
        // Server-only attribute properties survive the merge.
        assertThat(payload.findAttributeDef("rowCount").orElseThrow().getSearchWeight()).isEqualTo(-1);
    }

    @Test
    void addingMandatoryAttributeIsAnError() {
        TypePlan plan = TypePlanner.plan(typesDef(
                entity("my_table").dataSet().description("old")
                        .attribute(attr("rowCount", LONG)).attribute(attr("legacy", STRING))
                        .attribute(attr("mandatory", STRING).required())), server());

        assertThat(errors(plan)).containsExactly(
                "new attribute 'mandatory' must be optional: Atlas rejects adding mandatory attributes to an existing type");
    }

    @Test
    void changingAttributeTypeIsAnError() {
        TypePlan plan = TypePlanner.plan(typesDef(
                entity("my_table").dataSet().attribute(attr("rowCount", INT)).attribute(attr("legacy", STRING))),
                server());

        assertThat(errors(plan)).containsExactly("attribute 'rowCount': type cannot be changed (\"long\" -> \"int\")");
    }

    @Test
    void redefiningUnderAnotherCategoryIsAnError() {
        TypePlan plan = TypePlanner.plan(typesDef(classification("my_table")), server());

        assertThat(errors(plan)).containsExactly("already exists as ENTITY, cannot be defined as CLASSIFICATION");
    }

    @Test
    void relationshipEndChangesAreErrors() {
        TypePlan plan = TypePlanner.plan(typesDef(relationship("table_owner")
                .end1(end("my_table", "owner"))
                .end2(end("DataSet", "ownedTables").set())), server());

        assertThat(errors(plan)).containsExactly("endDef1 name cannot be changed (\"ownerRef\" -> \"owner\")");
    }

    @Test
    void relationshipEndCardinalityChangeIsAnError() {
        TypePlan plan = TypePlanner.plan(typesDef(relationship("table_owner")
                .end1(end("my_table", "ownerRef"))
                .end2(end("DataSet", "ownedTables"))), server());

        assertThat(errors(plan)).containsExactly("endDef2 cardinality cannot be changed (\"SET\" -> \"SINGLE\")");
    }

    @Test
    void relationshipPropagationChangeIsAnUpdate() {
        TypePlan plan = TypePlanner.plan(typesDef(relationship("table_owner")
                .end1(end("my_table", "ownerRef"))
                .end2(end("DataSet", "ownedTables").set())
                .propagateTags(io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.PropagateTags.ONE_TO_TWO)), server());

        assertThat(plan.hasErrors()).isFalse();
        assertThat(plan.getChanges().get(0).details()).containsExactly("~ propagateTags: \"NONE\" -> \"ONE_TO_TWO\"");
    }

    @Test
    void enumValuesAddedAndRemoved() {
        TypePlan plan = TypePlanner.plan(typesDef(enumType("tier").values("BRONZE", "GOLD")), server());

        assertThat(plan.getChanges().get(0).details()).containsExactly("+ value 'GOLD'", "- value 'SILVER'");
        assertThat(plan.getWarnings()).hasSize(1);
    }

    @Test
    void unknownReferencesAreErrors() {
        TypePlan plan = TypePlanner.plan(typesDef(
                entity("t2").superTypes("Missing").attribute("cols", "array<missing_column>")), server());

        assertThat(errors(plan)).containsExactlyInAnyOrder(
                "superType refers to unknown type 'Missing'",
                "attribute 'cols' refers to unknown type 'missing_column'");
    }

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

    private static List<String> errors(TypePlan plan) {
        return plan.getErrors().stream().map(TypeIssue::message).collect(Collectors.toList());
    }
}
