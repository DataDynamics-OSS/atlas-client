package io.datadynamics.atlas.client.it;

import io.datadynamics.atlas.client.cli.AtlasCli;
import io.datadynamics.atlas.client.lineage.AtlasLineage;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.MissingEntityPolicy;
import io.datadynamics.atlas.client.lineage.LineageRegistration;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityWithExtInfo;
import io.datadynamics.atlas.client.test.AtlasTestServer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Atlas를 상대로 {@code atlas} 명령줄 도구를 검증합니다.
 * <p>
 * 타입 plan/apply/export, CSV 가져오기와 조회, 리니지 영향도와 그래프, 일관성 검사, Kafka 이벤트 tail을
 * 실제 명령줄 인자로 실행해 봅니다.
 */
class CliIT extends AtlasIT {

    /**
     * 명령 실행 결과입니다.
     *
     * @param exitCode 종료 코드
     * @param out      표준 출력 내용
     * @param err      표준 오류 내용
     */
    record Result(int exitCode, String out, String err) {
    }

    /** 테스트용 타입 이름입니다(테이블, 컬럼, 프로세스). */
    private static String table;
    private static String column;
    private static String job;

    /** 타입 정의 파일, CSV, export 결과를 쓰는 임시 디렉터리입니다. 클래스 전체에서 공유합니다. */
    @TempDir
    static Path dir;

    /**
     * 테이블·컬럼·프로세스 타입과 테이블-컬럼 composition relationship을 YAML 파일로 쓰고, CLI로 plan과 apply를
     * 실행해 만듭니다. plan은 4개 생성을 보여 줘야 하고, {@code --yes}로 apply하면 확인 없이 반영되어야 합니다.
     */
    @BeforeAll
    static void createTypes() throws IOException {
        table = name("cli_table");
        column = name("cli_column");
        job = name("cli_job");
        cleanupTypes(name("cli_table_columns"), job, column, table);
        Path types = dir.resolve("types");
        Files.createDirectories(types);
        Files.writeString(types.resolve("model.yaml"), String.join("\n",
                "entityDefs:",
                "  - {name: " + table + ", superTypes: [DataSet], attributeDefs: [{name: rowCount, typeName: long, isOptional: true}]}",
                "  - {name: " + column + ", superTypes: [DataSet]}",
                "  - {name: " + job + ", superTypes: [Process]}",
                "relationshipDefs:",
                "  - name: " + name("cli_table_columns"),
                "    relationshipCategory: COMPOSITION",
                "    endDef1: {type: " + table + ", name: columns, cardinality: SET, isContainer: true}",
                "    endDef2: {type: " + column + ", name: table, cardinality: SINGLE}"));

        Result plan = cli("types", "plan", types.toString());
        assertThat(plan.exitCode()).as(plan.err()).isZero();
        assertThat(plan.out()).contains("+ create    ENTITY            " + table).contains("4 to create");
        Result apply = cli("types", "apply", types.toString(), "--yes");
        assertThat(apply.exitCode()).as(apply.err()).isZero();
        assertThat(apply.out()).contains("Applied:");
    }

    /**
     * 공유 테스트 서버를 대상으로 CLI를 같은 프로세스 안에서 실행합니다.
     * <p>
     * {@code events} 명령은 REST 접속 옵션이 없으므로 그 경우를 빼고 {@code --url}, {@code -u}, {@code --password}를
     * 덧붙입니다.
     *
     * @param args 명령줄 인자(접속 옵션 제외)
     * @return 종료 코드와 출력
     */
    static Result cli(String... args) {
        AtlasTestServer server = AtlasTestServer.shared();
        List<String> all = new ArrayList<>(List.of(args));
        if (!args[0].equals("events")) {
            all.addAll(List.of("--url", server.url(), "-u", server.username(), "--password", server.password()));
        }
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        // AtlasCli는 생성될 때 System.in을 잡으므로, 빈 입력으로 바꾼 뒤에 만들어야 확인 질문이 테스트를 멈추지 않습니다.
        // 다른 테스트에 영향이 없도록 끝나면 원래 입력으로 되돌립니다.
        InputStream stdin = System.in;
        System.setIn(new ByteArrayInputStream(new byte[0]));
        try {
            CommandLine commandLine = AtlasCli.commandLine(new AtlasCli());
            commandLine.setOut(new PrintWriter(out, true));
            commandLine.setErr(new PrintWriter(err, true));
            int exitCode = commandLine.execute(all.toArray(String[]::new));
            return new Result(exitCode, out.toString(), err.toString());
        } finally {
            System.setIn(stdin);
        }
    }

    /**
     * apply한 정의로 다시 plan하면 4개 모두 변경 없음이어야 하고, 테이블과 컬럼 타입을 파일로 export하면 두 타입이
     * 들어 있으면서 서버 관리 필드(createTime)는 빠져 있어야 합니다.
     */
    @Test
    void appliedTypesPlanUnchangedAndExport() throws IOException {
        Path types = dir.resolve("types");
        Result again = cli("types", "plan", types.toString());
        assertThat(again.out()).contains("Plan: 0 to create, 0 to update, 0 to delete, 4 unchanged");

        Path export = dir.resolve("export.yaml");
        Result exported = cli("types", "export", table, column, "-o", export.toString());
        assertThat(exported.exitCode()).as(exported.err()).isZero();
        assertThat(Files.readString(export)).contains("name: " + table).contains("name: " + column)
                .doesNotContain("createTime");
    }

    /**
     * 테이블 하나와 그 테이블을 참조하는 컬럼 두 개를 담은 CSV를 가져오면 3개가 생성되어야 합니다. 저장된 테이블은
     * rowCount, 레이블, 컬럼 2개와의 관계를 가져야 하고, {@code entities get}으로 컬럼을 조회할 수 있어야 합니다.
     */
    @Test
    void importCsvWithReferences() throws IOException {
        Path csv = dir.resolve("entities.csv");
        String tableName = name("orders@cli");
        Files.writeString(csv, String.join("\n",
                "typeName,qualifiedName,name,rowCount,table@" + table + ",labels",
                table + "," + tableName + ",orders,42,,gold|finance",
                column + "," + tableName + ".id,id,," + tableName + ",",
                column + "," + tableName + ".amount,amount,," + tableName + ","));

        Result result = cli("entities", "import", csv.toString());

        assertThat(result.exitCode()).as(result.err()).isZero();
        // 컬럼은 같은 파일·같은 배치의 테이블을 참조합니다(placeholder guid로 연결되어야 성공합니다).
        assertThat(result.out()).contains("Imported 3 entities: 3 created");
        AtlasEntityWithExtInfo stored = atlas.entities().getByQualifiedName(table, tableName);
        cleanupEntity(stored.getEntity().getGuid());
        assertThat(stored.getEntity().getAttribute("rowCount")).isEqualTo(42);
        assertThat(stored.getEntity().getLabels()).containsExactlyInAnyOrder("gold", "finance");
        assertThat((List<?>) stored.getEntity().getRelationshipAttributes().get("columns")).hasSize(2);

        Result get = cli("entities", "get", column, tableName + ".id");
        assertThat(get.out()).contains("\"qualifiedName\" : \"" + tableName + ".id\"");
    }

    /**
     * 리니지와 일관성 검사를 검증합니다.
     * <p>
     * 준비: raw → load → mart → publish → report 리니지를 등록합니다(없는 데이터셋은 만듭니다).
     * 기대: raw의 영향도에 mart와 report 2개가 나오고, mart의 Mermaid 그래프가 출력되어야 합니다.
     * 이어서 입출력이 없는 프로세스를 만들고 report를 삭제하면, {@code check}가 그 프로세스(ORPHAN_PROCESS)와
     * 출력을 잃은 publish(INCOMPLETE_PROCESS) 2건을 보고하고 정상인 load는 보고하지 않아야 합니다.
     */
    @Test
    void lineageAndConsistencyCheck() {
        AtlasLineage lineage = new AtlasLineage(atlas).missingEntities(MissingEntityPolicy.CREATE);
        String raw = name("raw@cli");
        String mart = name("mart@cli");
        String report = name("report@cli");
        LineageRegistration registration = lineage.register(List.of(
                lineage.process(job, name("load@cli")).inputs(EntityRef.of(table, raw)).outputs(EntityRef.of(table, mart)),
                lineage.process(job, name("publish@cli")).inputs(EntityRef.of(table, mart))
                        .outputs(EntityRef.of(table, report))));
        registration.guids().values().forEach(AtlasIT::cleanupEntity);

        Result impact = cli("lineage", "impact", table, raw);
        assertThat(impact.exitCode()).as(impact.err()).isZero();
        assertThat(impact.out()).contains(table + ":" + mart).contains(table + ":" + report)
                .contains("2 downstream datasets");

        Result mermaid = cli("lineage", "graph", table, mart, "--format", "mermaid");
        assertThat(mermaid.out()).contains("flowchart LR").contains(name("load") + "<br/>" + job).contains(" --> ");

        // 입출력이 없는 프로세스를 하나 만들고, publish 프로세스의 출력(report)을 삭제합니다.
        String orphan = atlas.entities().createOrUpdate(new AtlasEntity(job,
                Map.of("qualifiedName", name("orphan@cli"), "name", "orphan"))).firstGuid().orElseThrow();
        cleanupEntity(orphan);
        atlas.entities().deleteByQualifiedName(table, report);

        // 검색은 색인을 기준으로 하는데, Atlas는 색인을 비동기로 갱신하므로 결과가 반영될 때까지 기다립니다.
        Result check = awaitValue(() -> cli("check", "--process-type", job),
                r -> r.out().contains("2 problems found."), Duration.ofSeconds(60));
        assertThat(check.exitCode()).as(check.out() + check.err()).isEqualTo(AtlasCli.EXIT_FAILURE);
        assertThat(check.out())
                .contains("ORPHAN_PROCESS     " + job + ":" + name("orphan@cli"))
                .contains("INCOMPLETE_PROCESS " + job + ":" + name("publish@cli"))
                .contains("2 problems found.")
                .doesNotContain(name("load@cli"));
    }

    /**
     * 엔티티를 만든 뒤 {@code events tail}로 처음부터 읽으면, 타입·qualifiedName·연산 필터를 통과한 생성 이벤트
     * 하나를 출력하고 끝나야 합니다. 테스트 서버의 Kafka 주소를 모르면 건너뜁니다.
     */
    @Test
    void eventsTailPrintsChanges() {
        String servers = AtlasTestServer.shared().kafkaBootstrapServers().orElse(null);
        Assumptions.assumeTrue(servers != null, "Atlas server without known Kafka");
        String qualifiedName = name("tail@cli");
        String guid = atlas.entities().createOrUpdate(new AtlasEntity(table,
                Map.of("qualifiedName", qualifiedName, "name", "tail"))).firstGuid().orElseThrow();
        cleanupEntity(guid);

        Result tail = cli("events", "tail", "--bootstrap-servers", servers, "--from-beginning", "--type", table,
                "--qualified-name", prefix + "tail@*", "--operation", "ENTITY_CREATE", "--max-events", "1",
                "--duration", "90");

        assertThat(tail.exitCode()).as(tail.err()).isZero();
        assertThat(tail.out()).contains("ENTITY_CREATE").contains(table + ":").contains("(" + guid + ")");
    }
}
