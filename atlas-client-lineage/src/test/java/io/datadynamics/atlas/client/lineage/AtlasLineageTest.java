package io.datadynamics.atlas.client.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.test.FakeAtlasServer;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.lineage.graph.Impact;
import io.datadynamics.atlas.client.lineage.graph.LineageGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtlasLineageTest {

    private static final String BULK = "/api/atlas/v2/entity/bulk";

    private final FakeAtlasServer server = new FakeAtlasServer();
    private final AtlasClient atlas = AtlasClient.builder().serverUrl(server.url()).build();
    private final AtlasLineage lineage = new AtlasLineage(atlas, "etl_job"::equals);

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** Entities exist unless their qualified name contains "missing". */
    private void entitiesExistExceptMissing(String typeName) {
        server.on(GET, "/api/atlas/v2/entity/uniqueAttribute/type/" + typeName, req -> req.rawQuery.contains("missing")
                ? FakeAtlasServer.respond(404, "{\"errorCode\":\"ATLAS-404-00-009\"}")
                : FakeAtlasServer.respond(200, "{\"entity\":{\"typeName\":\"" + typeName + "\",\"guid\":\"existing\"}}"));
    }

    private void bulkEchoesGuidAssignments() {
        server.on(POST, BULK, req -> {
            JsonNode body = AtlasJson.fromJson(req.body, JsonNode.class);
            StringBuilder assignments = new StringBuilder();
            body.get("entities").forEach(e -> {
                String placeholder = e.get("guid").asText();
                assignments.append(assignments.length() == 0 ? "" : ",")
                        .append('"').append(placeholder).append("\":\"real").append(placeholder).append('"');
            });
            return FakeAtlasServer.respond(200, "{\"guidAssignments\":{" + assignments + "}}");
        });
    }

    private JsonNode sentEntities() {
        return AtlasJson.fromJson(server.lastRequest().body, JsonNode.class).get("entities");
    }

    @Test
    void registersProcessWithColumnLineageInOneCall() {
        entitiesExistExceptMissing("hive_table");
        entitiesExistExceptMissing("hive_column");
        bulkEchoesGuidAssignments();

        LineageRegistration result = lineage.process("etl_job", "daily_sales_agg@prod")
                .owner("data-team")
                .attribute("queryText", "INSERT INTO ...")
                .inputs(EntityRef.of("hive_table", "raw.sales@prod"))
                .outputs(EntityRef.of("hive_table", "mart.daily_sales@prod"))
                .columnLineage(EntityRef.of("hive_column", "mart.daily_sales.amount@prod"),
                        EntityRef.of("hive_column", "raw.sales.price@prod"), EntityRef.of("hive_column", "raw.sales.qty@prod"))
                .register();

        JsonNode entities = sentEntities();
        assertThat(entities).hasSize(2);

        JsonNode process = entities.get(0);
        assertThat(process.get("typeName").asText()).isEqualTo("etl_job");
        assertThat(process.at("/attributes/qualifiedName").asText()).isEqualTo("daily_sales_agg@prod");
        assertThat(process.at("/attributes/name").asText()).isEqualTo("daily_sales_agg");
        assertThat(process.at("/attributes/owner").asText()).isEqualTo("data-team");
        assertThat(process.at("/attributes/inputs/0/uniqueAttributes/qualifiedName").asText()).isEqualTo("raw.sales@prod");
        assertThat(process.at("/attributes/outputs/0/typeName").asText()).isEqualTo("hive_table");

        JsonNode column = entities.get(1);
        assertThat(column.get("typeName").asText()).isEqualTo("hive_column_lineage");
        assertThat(column.at("/attributes/qualifiedName").asText())
                .isEqualTo("daily_sales_agg@prod:mart.daily_sales.amount@prod");
        assertThat(column.at("/attributes/inputs")).hasSize(2);
        // The column lineage points at the process through its placeholder guid.
        assertThat(column.at("/attributes/query/guid").asText()).isEqualTo(process.get("guid").asText());
        assertThat(process.get("guid").asText()).startsWith("-");

        assertThat(result.guidOf("etl_job", "daily_sales_agg@prod")).contains("real" + process.get("guid").asText());
        assertThat(result.createdEntities()).isEmpty();
    }

    @Test
    void missingDatasetsFailBeforeAnythingIsSent() {
        entitiesExistExceptMissing("hive_table");

        assertThatThrownBy(() -> lineage.process("etl_job", "job@prod")
                .inputs(EntityRef.of("hive_table", "raw.missing_a@prod"), EntityRef.of("hive_table", "raw.ok@prod"))
                .outputs(EntityRef.of("hive_table", "mart.missing_b@prod"))
                .register())
                .isInstanceOfSatisfying(LineageException.class, e -> assertThat(e.getMissingEntities())
                        .extracting(EntityRef::qualifiedName)
                        .containsExactly("raw.missing_a@prod", "mart.missing_b@prod"));

        assertThat(server.requests()).extracting(r -> r.method).containsOnly(GET);
    }

    @Test
    void missingDatasetsCanBeCreated() {
        entitiesExistExceptMissing("s3_object");
        bulkEchoesGuidAssignments();

        LineageRegistration result = lineage.missingEntities(MissingEntityPolicy.CREATE)
                .process("etl_job", "ingest@prod")
                .inputs(EntityRef.of("s3_object", "s3://bucket/missing.csv@prod").withAttribute("format", "csv"))
                .outputs(EntityRef.of("s3_object", "s3://bucket/out.parquet@prod"))
                .register();

        JsonNode entities = sentEntities();
        assertThat(entities).hasSize(2);
        JsonNode stub = entities.get(0);
        assertThat(stub.at("/attributes/qualifiedName").asText()).isEqualTo("s3://bucket/missing.csv@prod");
        assertThat(stub.at("/attributes/format").asText()).isEqualTo("csv");
        assertThat(entities.get(1).at("/attributes/inputs/0/guid").asText()).isEqualTo(stub.get("guid").asText());
        assertThat(entities.get(1).at("/attributes/outputs/0/uniqueAttributes/qualifiedName").asText())
                .isEqualTo("s3://bucket/out.parquet@prod");
        assertThat(result.createdEntities()).extracting(EntityRef::qualifiedName)
                .containsExactly("s3://bucket/missing.csv@prod");
    }

    @Test
    void severalProcessesInOneTransaction() {
        bulkEchoesGuidAssignments();
        lineage.missingEntities(MissingEntityPolicy.NO_CHECK);

        lineage.register(List.of(
                lineage.process("etl_job", "a@prod").input("t", "x@prod").output("t", "y@prod"),
                lineage.process("etl_job", "b@prod").input("t", "y@prod").output("t", "z@prod")));

        assertThat(server.requests()).hasSize(1);
        assertThat(sentEntities()).hasSize(2);
    }

    @Test
    void processWithoutDatasetsIsRejected() {
        assertThatThrownBy(() -> lineage.process("etl_job", "empty@prod").register())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("neither inputs nor outputs");
    }

    @Test
    void customColumnLineageType() {
        bulkEchoesGuidAssignments();
        lineage.missingEntities(MissingEntityPolicy.NO_CHECK)
                .columnLineageType("my_column_lineage", null)
                .columnLineageQualifiedName((process, column) -> column + "<-" + process);

        lineage.process("etl_job", "p@prod")
                .input("t", "a@prod").output("t", "b@prod")
                .columnLineage(EntityRef.of("c", "b.x@prod"), List.of(EntityRef.of("c", "a.x@prod")),
                        Map.of("expression", "upper(x)"))
                .register();

        JsonNode column = sentEntities().get(1);
        assertThat(column.get("typeName").asText()).isEqualTo("my_column_lineage");
        assertThat(column.at("/attributes/qualifiedName").asText()).isEqualTo("b.x@prod<-p@prod");
        assertThat(column.at("/attributes/expression").asText()).isEqualTo("upper(x)");
        assertThat(column.at("/attributes").has("query")).isFalse();
    }

    @Test
    void impactUsesOutputLineageAndResolvesProcessTypes() {
        server.on(GET, "/api/atlas/v2/lineage/uniqueAttribute/type/hive_table", req -> FakeAtlasServer.respond(200,
                LineageFixtures.LINEAGE.replace("\"baseEntityGuid\":\"daily\"",
                        "\"baseEntityGuid\":\"sales\"")));
        server.on(GET, "/api/atlas/v2/types/entitydef/name/etl_job", 200,
                "{\"category\":\"ENTITY\",\"name\":\"etl_job\",\"superTypes\":[\"my_base_process\"]}");
        server.on(GET, "/api/atlas/v2/types/entitydef/name/my_base_process", 200,
                "{\"category\":\"ENTITY\",\"name\":\"my_base_process\",\"superTypes\":[\"Process\"]}");
        server.on(GET, "/api/atlas/v2/types/entitydef/name/hive_table", 200,
                "{\"category\":\"ENTITY\",\"name\":\"hive_table\",\"superTypes\":[\"DataSet\"]}");
        server.on(GET, "/api/atlas/v2/types/entitydef/name/DataSet", 200,
                "{\"category\":\"ENTITY\",\"name\":\"DataSet\",\"superTypes\":[\"Asset\"]}");

        server.on(GET, "/api/atlas/v2/entity/bulk", 200, "{\"entities\":[]}");

        AtlasLineage resolving = new AtlasLineage(atlas);
        List<Impact> impact = resolving.impact(EntityRef.of("hive_table", "raw.sales@prod"), 5);

        assertThat(impact).extracting(i -> i.dataset().qualifiedName() + "@" + i.distance())
                .containsExactly("mart.daily@prod@1", "dash@prod@2");
        assertThat(server.requests().get(0).rawQuery)
                .isEqualTo("attr%3AqualifiedName=raw.sales%40prod&direction=OUTPUT&depth=5");
        long etlJobLookups = server.requests().stream().filter(r -> r.path.endsWith("/etl_job")).count();
        assertThat(etlJobLookups).as("type resolution is cached").isEqualTo(1);
    }

    @Test
    void softDeletedRelationshipsAreRemovedFromGraphs() {
        // p_agg used to read "customer"; that relationship (rel-old) was soft-deleted on re-registration.
        String lineageJson = LineageFixtures.LINEAGE
                .replace("{\"fromEntityId\":\"customer\",\"toEntityId\":\"p_agg\"}",
                        "{\"fromEntityId\":\"customer\",\"toEntityId\":\"p_agg\",\"relationshipId\":\"rel-old\"}");
        server.on(GET, "/api/atlas/v2/lineage/uniqueAttribute/type/hive_table", 200, lineageJson);
        server.on(GET, "/api/atlas/v2/entity/bulk", 200, "{\"entities\":[{\"typeName\":\"etl_job\",\"guid\":\"p_agg\","
                + "\"relationshipAttributes\":{\"inputs\":["
                + "{\"guid\":\"sales\",\"relationshipGuid\":\"rel-new\",\"relationshipStatus\":\"ACTIVE\"},"
                + "{\"guid\":\"customer\",\"relationshipGuid\":\"rel-old\",\"relationshipStatus\":\"DELETED\"}],"
                + "\"outputs\":[{\"guid\":\"daily\",\"relationshipGuid\":\"rel-out\",\"relationshipStatus\":\"ACTIVE\"}]}}]}");

        LineageGraph graph = lineage.graph(EntityRef.of("hive_table", "mart.daily@prod"));

        assertThat(graph.upstreamDatasets(graph.base())).extracting(n -> n.guid()).containsExactly("sales");
        assertThat(graph.node("customer")).as("disconnected node is dropped").isEmpty();
        FakeAtlasServer.Request bulk = server.requests(GET, "/api/atlas/v2/entity/bulk").get(0);
        assertThat(bulk.queryParams().get("guid")).containsExactly("p_agg", "p_fix", "p_report");
        assertThat(bulk.queryParam("minExtInfo")).isEqualTo("true");

        LineageGraph history = new AtlasLineage(atlas, "etl_job"::equals).includeDeletedRelationships(true)
                .graph(EntityRef.of("hive_table", "mart.daily@prod"));
        assertThat(history.upstreamDatasets(history.base())).extracting(n -> n.guid()).contains("customer");
    }

    @Test
    void graphOfEntityWithoutLineage() {
        server.on(GET, "/api/atlas/v2/lineage/uniqueAttribute/type/hive_table", 200,
                "{\"baseEntityGuid\":\"g1\",\"guidEntityMap\":{},\"relations\":[]}");

        LineageGraph graph = lineage.graph(EntityRef.of("hive_table", "db.lonely@prod"));

        assertThat(graph.base().qualifiedName()).isEqualTo("db.lonely@prod");
        assertThat(graph.base().typeName()).isEqualTo("hive_table");
    }
}
