package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.model.search.SearchParameters;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.test.FakeAtlasServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code atlas} CLI의 각 명령을 {@link FakeAtlasServer}를 상대로 실행해 검증합니다.
 * <p>
 * 종료 코드, 출력 내용, 가짜 서버가 받은 요청을 확인합니다. 마지막 몇 개 테스트는 CSV 파서, glob 변환,
 * 배치 내 참조 연결 같은 보조 로직을 직접 검증합니다.
 */
class AtlasCliTest {

    private static final String TYPEDEFS = "/api/atlas/v2/types/typedefs";
    private static final String BULK = "/api/atlas/v2/entity/bulk";
    /** 가짜 서버가 돌려줄 기존 타입: DataSet과, owner2 속성을 가진 my_table입니다. */
    private static final String SERVER_TYPES = "{\"entityDefs\":["
            + "{\"category\":\"ENTITY\",\"name\":\"DataSet\",\"attributeDefs\":[]},"
            + "{\"category\":\"ENTITY\",\"name\":\"my_table\",\"superTypes\":[\"DataSet\"],\"guid\":\"g1\","
            + "\"attributeDefs\":[{\"name\":\"owner2\",\"typeName\":\"string\",\"isOptional\":true}]}]}";

    /** 모든 테스트에서 typedef 조회에는 {@link #SERVER_TYPES}를 돌려주는 가짜 서버입니다. */
    private final FakeAtlasServer server = new FakeAtlasServer().on(GET, TYPEDEFS, 200, SERVER_TYPES);
    private final CliRunner cli = new CliRunner(server.url());

    @TempDir
    Path dir;

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** 버전과 ACTIVE 상태를 응답하는 서버에 {@code status}를 실행하면 버전과 상태를 출력하고 0으로 끝나야 합니다. */
    @Test
    void statusShowsVersionAndHaState() {
        server.on(GET, "/api/atlas/admin/version", 200, "{\"Version\":\"2.3.0\",\"Name\":\"apache-atlas\"}")
                .on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"ACTIVE\"}");

        CliRunner.Result result = cli.run("status");

        assertThat(result.exitCode()).isZero();
        assertThat(result.out()).isEqualTo(String.format("Atlas 2.3.0 (ACTIVE)%n"));
    }

    /**
     * 접속 설정의 우선순위를 검증합니다.
     * <p>
     * properties 파일에 url, user, password를 두고 {@code --user bob}을 함께 주면 user는 옵션 값이, password는
     * 파일 값이 쓰여야 합니다. URL이 어디에도 없으면 {@link AtlasCli#EXIT_FAILURE}와 "No Atlas URL" 오류가 나야 합니다.
     */
    @Test
    void connectionSettingsComeFromOptionsEnvironmentOrFile() throws IOException {
        server.on(GET, "/api/atlas/admin/version", 200, "{\"Version\":\"2.3.0\"}")
                .on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"ACTIVE\"}");
        Path config = dir.resolve("client.properties");
        Files.writeString(config, "url=" + server.url() + "\nuser=admin\npassword=secret\n");

        CliRunner.Result result = new CliRunner("").run("status", "--config", config.toString(), "--user", "bob");

        assertThat(result.exitCode()).isZero();
        // --user가 파일보다 우선하고, 비밀번호는 여전히 파일에서 가져옵니다("bob:secret"의 Base64).
        assertThat(server.lastRequest().header("Authorization")).isEqualTo("Basic Ym9iOnNlY3JldA==");

        CliRunner.Result noUrl = new CliRunner("").run("status");
        assertThat(noUrl.exitCode()).isEqualTo(AtlasCli.EXIT_FAILURE);
        assertThat(noUrl.err()).contains("No Atlas URL");
    }

    /** 하위 명령 없이 실행하거나 필수 인자(PATH)를 빠뜨리면 종료 코드 2({@link AtlasCli#EXIT_USAGE})여야 합니다. */
    @Test
    void usageErrorsExitWith2() {
        assertThat(cli.run().exitCode()).isEqualTo(AtlasCli.EXIT_USAGE);
        assertThat(cli.run("types", "plan").exitCode()).isEqualTo(AtlasCli.EXIT_USAGE);
    }

    /**
     * {@code types plan}을 검증합니다.
     * <p>
     * 기존 my_table에 속성을 추가하고 new_table을 새로 정의한 디렉터리로 plan하면 생성 1개, 수정 1개가 출력되고
     * 0으로 끝나야 합니다. 존재하지 않는 상위 타입을 참조하는 파일을 추가하면 오류가 출력되고
     * {@link AtlasCli#EXIT_FAILURE}로 끝나야 합니다.
     */
    @Test
    void typesPlanRendersChangesAndFailsOnErrors() throws IOException {
        Path types = dir.resolve("types");
        Files.createDirectories(types);
        Files.writeString(types.resolve("tables.yaml"), String.join("\n",
                "entityDefs:",
                "  - name: my_table",
                "    superTypes: [DataSet]",
                "    attributeDefs:",
                "      - {name: owner2, typeName: string, isOptional: true}",
                "      - {name: rows, typeName: long, isOptional: true}",
                "  - name: new_table",
                "    superTypes: [DataSet]"));

        CliRunner.Result plan = cli.run("types", "plan", types.toString());
        assertThat(plan.exitCode()).isZero();
        assertThat(plan.out()).contains("+ create    ENTITY            new_table")
                .contains("~ update    ENTITY            my_table")
                .contains("+ attribute 'rows' (long)")
                .contains("Plan: 1 to create, 1 to update");

        Files.writeString(types.resolve("bad.yaml"), String.join("\n",
                "entityDefs:",
                "  - name: other",
                "    superTypes: [Missing]"));
        CliRunner.Result bad = cli.run("types", "plan", types.toString());
        assertThat(bad.exitCode()).isEqualTo(AtlasCli.EXIT_FAILURE);
        assertThat(bad.out()).contains("refers to unknown type 'Missing'");
    }

    /**
     * {@code types apply}의 확인 질문을 검증합니다.
     * <p>
     * 표준 입력으로 "no"를 주면 취소되어 POST 요청이 하나도 없어야 하고, "yes"를 주면 new_table만 담은
     * typedef 생성 요청이 가야 합니다.
     */
    @Test
    void typesApplyAsksForConfirmation() throws IOException {
        server.on(POST, TYPEDEFS, req -> FakeAtlasServer.respond(200, req.body));
        Path file = dir.resolve("types.json");
        Files.writeString(file, "{\"entityDefs\":[{\"name\":\"new_table\",\"superTypes\":[\"DataSet\"]}]}");

        CliRunner.Result declined = cli.stdin("no\n").run("types", "apply", file.toString());
        assertThat(declined.exitCode()).isEqualTo(AtlasCli.EXIT_FAILURE);
        assertThat(declined.out()).contains("Apply these changes?").contains("Apply cancelled.");
        assertThat(server.requests(POST, TYPEDEFS)).isEmpty();

        CliRunner.Result applied = cli.stdin("yes\n").run("types", "apply", file.toString());
        assertThat(applied.exitCode()).isZero();
        assertThat(applied.out()).contains("Applied:").contains("new_table");
        AtlasTypesDef sent = AtlasJson.fromJson(server.requests(POST, TYPEDEFS).get(0).body, AtlasTypesDef.class);
        assertThat(sent.getEntityDefs()).extracting(d -> d.getName()).containsExactly("new_table");
    }

    /** {@code types export}가 YAML을 출력하고, createdBy·guid 같은 서버 관리 필드는 빼야 합니다. */
    @Test
    void typesExportWritesYaml() {
        server.on(GET, "/api/atlas/v2/types/typedef/name/my_table", 200,
                "{\"category\":\"ENTITY\",\"name\":\"my_table\",\"guid\":\"g1\",\"createdBy\":\"admin\","
                        + "\"superTypes\":[\"DataSet\"],\"attributeDefs\":[]}");

        CliRunner.Result result = cli.run("types", "export", "my_table");

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(result.out()).contains("name: my_table").doesNotContain("createdBy").doesNotContain("guid");
    }

    /**
     * CSV {@code entities import}를 검증합니다.
     * <p>
     * 따옴표 안의 쉼표, 배열 열, 참조 열, classification/레이블 열, 빈 셀, 줄바꿈이 들어간 필드를 담은 3행 CSV를
     * {@code --batch-size 2}로 가져오면 요청이 2번 가야 합니다. 첫 요청의 엔티티 속성이 열 머리글 문법대로
     * 만들어졌는지, 빈 셀은 속성에서 빠졌는지, 여러 줄 필드가 그대로 보존되었는지 확인합니다.
     */
    @Test
    void entitiesImportReadsCsvAndSendsBatches() throws IOException {
        server.on(POST, BULK, 200, "{\"mutatedEntities\":{\"CREATE\":[{\"typeName\":\"t\",\"guid\":\"x\"}]}}");
        Path csv = dir.resolve("tables.csv");
        Files.writeString(csv, String.join("\n",
                "qualifiedName,name,description,tags[],db@hive_db,columns[]@hive_column,classifications,labels",
                "db.orders@prod,orders,\"Orders, all of them\",gold|finance,db@prod,db.orders.id@prod|db.orders.amount@prod,PII,core",
                "db.customers@prod,customers,,,db@prod,,,",
                "db.tmp@prod,tmp,\"multi",
                "line\",,,,,"));

        CliRunner.Result result = cli.run("entities", "import", csv.toString(), "--type", "hive_table",
                "--batch-size", "2");

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(result.out()).contains("Read 3 entities").contains("Batch 1/2: 2 entities")
                .contains("Imported 3 entities: 2 created, 0 updated");
        List<FakeAtlasServer.Request> requests = server.requests(POST, BULK);
        assertThat(requests).hasSize(2);
        AtlasEntitiesWithExtInfo first = AtlasJson.fromJson(requests.get(0).body, AtlasEntitiesWithExtInfo.class);
        AtlasEntity orders = first.getEntities().get(0);
        assertThat(orders.getTypeName()).isEqualTo("hive_table");
        assertThat(orders.getAttribute("description")).isEqualTo("Orders, all of them");
        assertThat(orders.getAttribute("tags")).isEqualTo(List.of("gold", "finance"));
        assertThat(orders.getAttribute("db")).isEqualTo(Map.of("typeName", "hive_db",
                "uniqueAttributes", Map.of("qualifiedName", "db@prod")));
        assertThat((List<?>) orders.getAttribute("columns")).hasSize(2);
        assertThat(orders.getClassifications()).extracting(c -> c.getTypeName()).containsExactly("PII");
        assertThat(orders.getLabels()).containsExactly("core");
        AtlasEntity customers = first.getEntities().get(1);
        assertThat(customers.getAttributes()).doesNotContainKeys("description", "tags", "columns");
        AtlasEntity tmp = AtlasJson.fromJson(requests.get(1).body, AtlasEntitiesWithExtInfo.class).getEntities().get(0);
        assertThat(tmp.getAttribute("description")).isEqualTo("multi\nline");
    }

    /**
     * 가져오기 전에 검증하는지 확인합니다. typeName이 없거나 qualifiedName이 없으면 행 번호를 담은 오류로
     * 실패하고 Atlas에는 요청을 하나도 보내지 않아야 합니다.
     */
    @Test
    void entitiesImportValidatesBeforeSending() throws IOException {
        Path csv = dir.resolve("bad.csv");
        Files.writeString(csv, "name\norders\n");
        CliRunner.Result noType = cli.run("entities", "import", csv.toString());
        assertThat(noType.exitCode()).isEqualTo(AtlasCli.EXIT_FAILURE);
        assertThat(noType.err()).contains("Row 2: no typeName");

        CliRunner.Result noName = cli.run("entities", "import", csv.toString(), "-t", "hive_table");
        assertThat(noName.err()).contains("Row 2: qualifiedName is missing");
        assertThat(server.requests(POST, BULK)).isEmpty();
    }

    /**
     * YAML 파일을 {@code --dry-run}으로 가져오면 엔티티 수와 첫 엔티티를 출력만 하고 요청은 보내지 않아야 합니다.
     * 첫 엔티티는 파일에 적은 typeName(hive_db)이 {@code -t} 기본값보다 우선해야 합니다.
     */
    @Test
    void entitiesImportReadsYamlInDryRun() throws IOException {
        Path yaml = dir.resolve("entities.yaml");
        Files.writeString(yaml, String.join("\n",
                "- typeName: hive_db",
                "  attributes: {qualifiedName: db@prod, name: db}",
                "- attributes: {qualifiedName: db.orders@prod, name: orders}"));

        CliRunner.Result result = cli.run("entities", "import", yaml.toString(), "-t", "hive_table", "--dry-run");

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(result.out()).contains("Read 2 entities").contains("\"typeName\" : \"hive_db\"");
        assertThat(server.requests(POST, BULK)).isEmpty();
    }

    /**
     * {@code check}가 프로세스 문제와 중복을 보고하는지 검증합니다.
     * <p>
     * 준비: 프로세스 3개(p1 정상, p2는 삭제된 과거 입력만 있음, p3은 출력 하나가 삭제된 엔티티를 가리키고
     * 다른 하나는 대상은 활성이지만 연결이 삭제됨)와, 타입 t에서 qualifiedName "dup"이 두 번 나오는 검색 결과.
     * 기대: p2는 ORPHAN_PROCESS, p3은 DELETED_REFERENCE와 INCOMPLETE_PROCESS, t는 DUPLICATE로 모두 4건이고,
     * 정상인 p1은 나오지 않으며, bulk 조회는 세 guid를 한 번에 요청해야 합니다.
     */
    @Test
    void checkReportsProcessProblemsAndDuplicates() {
        String search = "/api/atlas/v2/search/basic";
        server.on(POST, search, req -> {
            SearchParameters params = AtlasJson.fromJson(req.body, SearchParameters.class);
            // 첫 페이지 이후의 요청에는 빈 결과를 돌려줘 페이지 조회를 끝냅니다.
            if (params.getOffset() != null && params.getOffset() > 0) {
                return FakeAtlasServer.respond(200, "{}");
            }
            return FakeAtlasServer.respond(200, params.getTypeName().equals("Process")
                    ? "{\"entities\":[" + header("Process", "p1", "job1") + "," + header("Process", "p2", "job2") + ","
                    + header("Process", "p3", "job3") + "]}"
                    : "{\"entities\":[" + header("t", "a", "dup") + "," + header("t", "b", "dup") + ","
                    + header("t", "c", "single") + "]}");
        });
        server.on(GET, BULK, 200, "{\"entities\":["
                + process("p1", "job1", "[" + related("in1", "ACTIVE", "ACTIVE") + "]",
                "[" + related("out1", "ACTIVE", "ACTIVE") + "]") + ","
                + process("p2", "job2", "[" + related("old", "DELETED", "DELETED") + "]", "[]") + ","
                + process("p3", "job3", "[" + related("in3", "ACTIVE", "ACTIVE") + "]",
                "[" + related("gone", "ACTIVE", "DELETED") + "," + related("replaced", "DELETED", "ACTIVE") + "]")
                + "]}");

        CliRunner.Result result = cli.run("check", "--duplicates", "t");

        assertThat(result.exitCode()).isEqualTo(AtlasCli.EXIT_FAILURE);
        assertThat(result.out())
                .contains("ORPHAN_PROCESS     Process:job2 (p2) has no inputs and no outputs (1 former inputs were deleted)")
                .contains("DELETED_REFERENCE  Process:job3 (p3) output hive_table:gone is deleted")
                .contains("INCOMPLETE_PROCESS Process:job3 (p3) has no outputs")
                .contains("DUPLICATE          t:dup (a) 2 active entities share this qualifiedName; others: b")
                .contains("4 problems found.")
                .doesNotContain("job1");
        assertThat(server.requests(GET, BULK).get(0).queryParams().get("guid")).containsExactly("p1", "p2", "p3");
    }

    /** basic search 결과에 들어갈 활성 엔티티 헤더 JSON을 만듭니다. */
    private static String header(String type, String guid, String qualifiedName) {
        return "{\"typeName\":\"" + type + "\",\"guid\":\"" + guid + "\",\"status\":\"ACTIVE\","
                + "\"attributes\":{\"qualifiedName\":\"" + qualifiedName + "\"}}";
    }

    /** inputs/outputs relationshipAttributes를 가진 Process 엔티티 JSON을 만듭니다. */
    private static String process(String guid, String qualifiedName, String inputs, String outputs) {
        return "{\"typeName\":\"Process\",\"guid\":\"" + guid + "\",\"attributes\":{\"qualifiedName\":\""
                + qualifiedName + "\"},\"relationshipAttributes\":{\"inputs\":" + inputs + ",\"outputs\":" + outputs + "}}";
    }

    /** 관계 상태와 대상 엔티티 상태를 지정한 hive_table 참조 JSON을 만듭니다. */
    private static String related(String name, String relationshipStatus, String entityStatus) {
        return "{\"guid\":\"g-" + name + "\",\"typeName\":\"hive_table\",\"displayText\":\"" + name + "\","
                + "\"entityStatus\":\"" + entityStatus + "\",\"relationshipStatus\":\"" + relationshipStatus + "\"}";
    }

    /**
     * {@link EntityFiles#linkWithinBatch(List)}를 검증합니다. 같은 배치의 테이블을 가리키는 컬럼의 참조는 테이블의
     * placeholder guid(음수로 시작)로 바뀌고 uniqueAttributes는 사라져야 합니다.
     */
    @Test
    void referencesWithinABatchUsePlaceholderGuids() {
        List<AtlasEntity> entities = EntityFiles.fromCsv(String.join("\n",
                "typeName,qualifiedName,table@hive_table,db@hive_db",
                "hive_table,t@prod,,db@prod",
                "hive_column,t.id@prod,t@prod,"), null);

        EntityFiles.linkWithinBatch(entities);

        String tableGuid = entities.get(0).getGuid();
        assertThat(tableGuid).startsWith("-");
        AtlasObjectId table = (AtlasObjectId) entities.get(1).getAttribute("table");
        assertThat(table.getGuid()).isEqualTo(tableGuid);
        assertThat(table.getUniqueAttributes()).isNull();
        // 배치에 없는 엔티티를 가리키는 참조는 qualifiedName 참조로 그대로 남아야 합니다.
        assertThat(((AtlasObjectId) entities.get(0).getAttribute("db")).qualifiedName()).isEqualTo("db@prod");
    }

    /** glob의 {@code *}, {@code ?}는 와일드카드로, {@code .}은 글자 그대로 비교되어야 합니다. */
    @Test
    void qualifiedNameGlobs() {
        assertThat(EventsCommand.Tail.glob("sales.*@prod").matcher("sales.orders@prod").matches()).isTrue();
        assertThat(EventsCommand.Tail.glob("sales.*@prod").matcher("salesXorders@prod").matches()).isFalse();
        assertThat(EventsCommand.Tail.glob("t?@d").matcher("t1@d").matches()).isTrue();
    }

    /**
     * CSV 파서가 CRLF, 따옴표 안의 쉼표, 두 번 쓴 따옴표, 빈 줄 건너뛰기, 빈 필드 두 개({@code ,})를 올바르게
     * 처리하는지 확인합니다. 덧붙여 {@link AtlasObjectId#qualifiedName()}이 uniqueAttributes에서 값을 꺼내는지도
     * 확인합니다.
     */
    @Test
    void csvParserHandlesQuotesAndLineBreaks() {
        assertThat(Csv.parse("a,b\r\n\"x,1\",\"say \"\"hi\"\"\"\n\n,\n"))
                .containsExactly(List.of("a", "b"), List.of("x,1", "say \"hi\""), List.of("", ""));
        assertThat(AtlasObjectId.byQualifiedName("t", "q").qualifiedName()).isEqualTo("q");
    }
}
