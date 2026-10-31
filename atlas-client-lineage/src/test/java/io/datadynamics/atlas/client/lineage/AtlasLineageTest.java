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

/**
 * {@link AtlasLineage}의 리니지 등록과 조회를 가짜 Atlas 서버({@link FakeAtlasServer})로 검증합니다.
 * 등록 요청 본문, 없는 엔티티 처리 정책, 컬럼 리니지, 영향 분석, soft-delete된 relationship 제거를 다룹니다.
 */
class AtlasLineageTest {

    private static final String BULK = "/api/atlas/v2/entity/bulk";

    private final FakeAtlasServer server = new FakeAtlasServer();
    private final AtlasClient atlas = AtlasClient.builder().serverUrl(server.url()).build();
    private final AtlasLineage lineage = new AtlasLineage(atlas, "etl_job"::equals);

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** 엔티티 조회 요청의 qualifiedName에 "missing"이 들어 있으면 404를, 아니면 엔티티가 있다고 응답하게 합니다. */
    private void entitiesExistExceptMissing(String typeName) {
        server.on(GET, "/api/atlas/v2/entity/uniqueAttribute/type/" + typeName, req -> req.rawQuery.contains("missing")
                ? FakeAtlasServer.respond(404, "{\"errorCode\":\"ATLAS-404-00-009\"}")
                : FakeAtlasServer.respond(200, "{\"entity\":{\"typeName\":\"" + typeName + "\",\"guid\":\"existing\"}}"));
    }

    /**
     * bulk 생성 요청을 받으면 보낸 엔티티의 임시 guid마다 {@code "real" + 임시 guid}를 실제 guid로 배정한
     * guidAssignments를 응답하게 합니다.
     */
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

    /** 마지막 요청 본문의 {@code entities} 배열을 돌려줍니다. */
    private JsonNode sentEntities() {
        return AtlasJson.fromJson(server.lastRequest().body, JsonNode.class).get("entities");
    }

    /**
     * 참조한 테이블·컬럼이 모두 있는 상태에서 컬럼 리니지가 있는 프로세스를 등록합니다.
     * 기대 결과: 프로세스와 컬럼 리니지 엔티티 두 개가 bulk 요청 한 번에 담기고, 컬럼 리니지의 {@code query}는
     * 프로세스의 임시 guid를 가리키며, 응답의 guidAssignments로 실제 guid를 찾을 수 있습니다.
     */
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
        // 컬럼 리니지는 같은 요청 안의 프로세스를 임시 guid('-'로 시작)로 가리킵니다.
        assertThat(column.at("/attributes/query/guid").asText()).isEqualTo(process.get("guid").asText());
        assertThat(process.get("guid").asText()).startsWith("-");

        assertThat(result.guidOf("etl_job", "daily_sales_agg@prod")).contains("real" + process.get("guid").asText());
        assertThat(result.createdEntities()).isEmpty();
    }

    /**
     * 기본 정책(FAIL)에서 입력·출력 중 일부가 없으면 {@link LineageException}이 나고 없는 엔티티가 순서대로
     * 담기며, 존재 확인용 GET 외에는 아무 요청도 보내지 않는지 확인합니다.
     */
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

    /**
     * CREATE 정책에서 없는 입력을 지정한 속성과 함께 스텁으로 만들고, 프로세스가 그 스텁을 임시 guid로,
     * 이미 있는 출력은 uniqueAttributes로 가리키는지 확인합니다.
     */
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

    /** NO_CHECK 정책으로 프로세스 두 개를 등록하면 요청이 bulk 한 번뿐이고 엔티티 두 개가 담기는지 확인합니다. */
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

    /** 입력도 출력도 없는 프로세스를 등록하면 요청 전에 {@link IllegalStateException}이 나는지 확인합니다. */
    @Test
    void processWithoutDatasetsIsRejected() {
        assertThatThrownBy(() -> lineage.process("etl_job", "empty@prod").register())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("neither inputs nor outputs");
    }

    /**
     * 컬럼 리니지 타입과 이름 규칙을 바꾸고 부모 속성을 {@code null}로 두면, 지정한 타입·이름·추가 속성이 쓰이고
     * {@code query} 속성은 들어가지 않는지 확인합니다.
     */
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

    /**
     * 기본 생성자로 만든 {@link AtlasLineage}의 영향 분석을 검증합니다.
     * 준비: 기준 엔티티를 {@code sales}로 바꾼 리니지 응답과, {@code etl_job → my_base_process → Process} 상속 관계의
     * 타입 정의를 등록합니다.
     * 기대 결과: OUTPUT 방향과 지정한 depth로 리니지를 요청하고, 거리와 함께 영향 데이터셋을 돌려주며,
     * {@code etl_job} 타입 정의는 캐시 덕분에 한 번만 조회합니다.
     */
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

    /**
     * soft-delete된 relationship이 기본적으로 그래프에서 빠지는지 확인합니다.
     * 준비: {@code customer → p_agg} 관계에 relationshipId {@code rel-old}를 붙이고, 프로세스 bulk 조회 응답에서
     * 그 relationship을 DELETED로 표시합니다.
     * 기대 결과: 기본 설정에서는 {@code customer}가 그래프에서 사라지고, bulk 조회는 프로세스 guid만 minExtInfo로
     * 요청합니다. {@code includeDeletedRelationships(true)}이면 {@code customer}가 남습니다.
     */
    @Test
    void softDeletedRelationshipsAreRemovedFromGraphs() {
        // p_agg는 원래 "customer"를 읽었지만, 다시 등록하면서 그 relationship(rel-old)이 soft-delete되었습니다.
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

    /**
     * 리니지가 없어 응답의 guidEntityMap이 빈 엔티티를 조회하면, 요청한 typeName과 qualifiedName으로 기준 노드를
     * 채우는지 확인합니다.
     */
    @Test
    void graphOfEntityWithoutLineage() {
        server.on(GET, "/api/atlas/v2/lineage/uniqueAttribute/type/hive_table", 200,
                "{\"baseEntityGuid\":\"g1\",\"guidEntityMap\":{},\"relations\":[]}");

        LineageGraph graph = lineage.graph(EntityRef.of("hive_table", "db.lonely@prod"));

        assertThat(graph.base().qualifiedName()).isEqualTo("db.lonely@prod");
        assertThat(graph.base().typeName()).isEqualTo("hive_table");
    }
}
