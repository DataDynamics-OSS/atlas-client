package io.datadynamics.atlas.client.typedef.plan;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.test.FakeAtlasServer;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.search.SearchParameters;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.http.AtlasTransport.DELETE;
import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static io.datadynamics.atlas.client.http.AtlasTransport.PUT;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.STRING;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.classification;
import static io.datadynamics.atlas.client.typedef.TypeDefs.end;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static io.datadynamics.atlas.client.typedef.TypeDefs.relationship;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeDefManagerTest {

    private static final String TYPEDEFS = "/api/atlas/v2/types/typedefs";
    private static final String BASIC_SEARCH = "/api/atlas/v2/search/basic";

    private static final String SERVER_TYPES = "{"
            + "\"entityDefs\":["
            + "{\"category\":\"ENTITY\",\"name\":\"DataSet\",\"attributeDefs\":[]},"
            + "{\"category\":\"ENTITY\",\"name\":\"my_table\",\"superTypes\":[\"DataSet\"],\"serviceType\":\"mine\","
            + "\"guid\":\"g1\",\"attributeDefs\":[{\"name\":\"owner\",\"typeName\":\"string\",\"isOptional\":true}]},"
            + "{\"category\":\"ENTITY\",\"name\":\"my_column\",\"superTypes\":[\"DataSet\"],\"serviceType\":\"mine\",\"attributeDefs\":[]}],"
            + "\"relationshipDefs\":[{\"category\":\"RELATIONSHIP\",\"name\":\"my_table_columns\",\"serviceType\":\"mine\","
            + "\"relationshipCategory\":\"COMPOSITION\","
            + "\"endDef1\":{\"type\":\"my_table\",\"name\":\"columns\",\"cardinality\":\"SET\",\"isContainer\":true},"
            + "\"endDef2\":{\"type\":\"my_column\",\"name\":\"table\",\"cardinality\":\"SINGLE\"}}],"
            + "\"classificationDefs\":[{\"category\":\"CLASSIFICATION\",\"name\":\"PII\",\"attributeDefs\":[]}]"
            + "}";

    private final FakeAtlasServer server = new FakeAtlasServer()
            .on(GET, TYPEDEFS, 200, SERVER_TYPES);
    private final TypeDefManager manager = new TypeDefManager(AtlasClient.builder().serverUrl(server.url()).build());

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void syncCreatesThenUpdates() {
        server.on(POST, TYPEDEFS, req -> FakeAtlasServer.respond(200, req.body));
        server.on(PUT, TYPEDEFS, req -> FakeAtlasServer.respond(200, req.body));

        TypeApplyResult result = manager.sync(
                entity("my_table").dataSet().attribute(attr("owner", STRING)).attribute(attr("comment", STRING)),
                entity("my_view").dataSet(),
                classification("PII"));

        assertThat(result.created()).containsExactly("my_view");
        assertThat(result.updated()).containsExactly("my_table");
        assertThat(result.unchanged()).containsExactly("PII");

        List<String> methods = server.requests().stream().map(r -> r.method).collect(Collectors.toList());
        assertThat(methods).containsExactly(GET, POST, PUT);
        FakeAtlasServer.Request put = server.requests().get(2);
        AtlasTypesDef updated = AtlasJson.fromJson(put.body, AtlasTypesDef.class);
        assertThat(updated.getEntityDefs().get(0).getGuid()).isEqualTo("g1");
        assertThat(updated.getEntityDefs().get(0).getAttributeDefs()).extracting(a -> a.getName())
                .containsExactly("owner", "comment");
        assertThat(server.requests().get(1).body).contains("my_view").doesNotContain("my_table");
    }

    @Test
    void nothingIsSentWhenUpToDate() {
        TypeApplyResult result = manager.sync(entity("my_table").dataSet().attribute(attr("owner", STRING)));

        assertThat(result.hasChanges()).isFalse();
        assertThat(server.requests()).hasSize(1);
    }

    @Test
    void planWithErrorsIsNotApplied() {
        TypePlan plan = manager.plan(entity("my_table").dataSet()
                .attribute(attr("owner", STRING))
                .attribute(attr("mandatory", STRING).required()));

        assertThatThrownBy(() -> manager.apply(plan))
                .isInstanceOf(TypePlanException.class)
                .hasMessageContaining("new attribute 'mandatory' must be optional");
        assertThat(server.requests()).extracting(r -> r.method).containsOnly(GET);
    }

    @Test
    void deletingReferencedTypeIsBlocked() {
        server.on(POST, BASIC_SEARCH, 200, "{\"entities\":[]}");

        TypePlan plan = manager.planDelete(List.of("my_column"));

        assertThat(plan.getErrors()).extracting(TypeIssue::message).containsExactly(
                "still referenced by RELATIONSHIP my_table_columns (endDef2); delete it too or remove the reference");
    }

    @Test
    void deletingTypeWithEntitiesIsBlocked() {
        server.on(POST, BASIC_SEARCH, req -> {
            SearchParameters params = AtlasJson.fromJson(req.body, SearchParameters.class);
            boolean hasEntities = "my_table".equals(params.getTypeName());
            return FakeAtlasServer.respond(200, hasEntities
                    ? "{\"entities\":[{\"typeName\":\"my_table\",\"guid\":\"e1\",\"status\":\"DELETED\"}]}"
                    : "{\"entities\":[]}");
        });

        TypePlan plan = manager.planDelete(List.of("my_table", "my_column", "my_table_columns", "missing"));

        assertThat(plan.getErrors()).extracting(TypeIssue::message).containsExactly(
                "entities of this type exist (soft-deleted ones count too); delete and purge them first");
        assertThat(plan.getWarnings()).extracting(TypeIssue::typeName).contains("missing", "my_table_columns");
        SearchParameters sent = AtlasJson.fromJson(server.requests().get(1).body, SearchParameters.class);
        assertThat(sent.getExcludeDeletedEntities()).isFalse();
        assertThat(sent.getIncludeSubTypes()).isFalse();
    }

    @Test
    void deleteSendsServerDefinitions() {
        server.on(POST, BASIC_SEARCH, 200, "{\"entities\":[]}");
        server.on(DELETE, TYPEDEFS, 204, "");

        TypeApplyResult result = manager.delete(List.of("my_table", "my_column", "my_table_columns"));

        assertThat(result.deleted()).containsExactly("my_table", "my_column", "my_table_columns");
        AtlasTypesDef sent = AtlasJson.fromJson(server.lastRequest().body, AtlasTypesDef.class);
        assertThat(sent.getEntityDefs()).hasSize(2);
        assertThat(sent.getRelationshipDefs()).hasSize(1);
    }

    @Test
    void exportByServiceTypeStripsServerFields() {
        AtlasTypesDef exported = manager.exportByServiceType("mine");

        assertThat(exported.stream().map(d -> d.getName()))
                .containsExactly("my_table", "my_column", "my_table_columns");
        assertThat(exported.getEntityDefs().get(0).getGuid()).isNull();
    }

    @Test
    void relationshipDefinedWithDsl() {
        TypePlan plan = manager.plan(relationship("my_table_columns").composition()
                .end1(end("my_table", "columns").set().container())
                .end2(end("my_column", "table")));

        assertThat(plan.getChanges()).extracting(TypeChange::action).containsExactly(TypeChange.Action.UNCHANGED);
    }
}
