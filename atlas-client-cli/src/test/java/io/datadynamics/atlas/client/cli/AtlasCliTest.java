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

class AtlasCliTest {

    private static final String TYPEDEFS = "/api/atlas/v2/types/typedefs";
    private static final String BULK = "/api/atlas/v2/entity/bulk";
    private static final String SERVER_TYPES = "{\"entityDefs\":["
            + "{\"category\":\"ENTITY\",\"name\":\"DataSet\",\"attributeDefs\":[]},"
            + "{\"category\":\"ENTITY\",\"name\":\"my_table\",\"superTypes\":[\"DataSet\"],\"guid\":\"g1\","
            + "\"attributeDefs\":[{\"name\":\"owner2\",\"typeName\":\"string\",\"isOptional\":true}]}]}";

    private final FakeAtlasServer server = new FakeAtlasServer().on(GET, TYPEDEFS, 200, SERVER_TYPES);
    private final CliRunner cli = new CliRunner(server.url());

    @TempDir
    Path dir;

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void statusShowsVersionAndHaState() {
        server.on(GET, "/api/atlas/admin/version", 200, "{\"Version\":\"2.3.0\",\"Name\":\"apache-atlas\"}")
                .on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"ACTIVE\"}");

        CliRunner.Result result = cli.run("status");

        assertThat(result.exitCode()).isZero();
        assertThat(result.out()).isEqualTo(String.format("Atlas 2.3.0 (ACTIVE)%n"));
    }

    @Test
    void connectionSettingsComeFromOptionsEnvironmentOrFile() throws IOException {
        server.on(GET, "/api/atlas/admin/version", 200, "{\"Version\":\"2.3.0\"}")
                .on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"ACTIVE\"}");
        Path config = dir.resolve("client.properties");
        Files.writeString(config, "url=" + server.url() + "\nuser=admin\npassword=secret\n");

        CliRunner.Result result = new CliRunner("").run("status", "--config", config.toString(), "--user", "bob");

        assertThat(result.exitCode()).isZero();
        // --user wins over the file; the password still comes from the file.
        assertThat(server.lastRequest().header("Authorization")).isEqualTo("Basic Ym9iOnNlY3JldA==");

        CliRunner.Result noUrl = new CliRunner("").run("status");
        assertThat(noUrl.exitCode()).isEqualTo(AtlasCli.EXIT_FAILURE);
        assertThat(noUrl.err()).contains("No Atlas URL");
    }

    @Test
    void usageErrorsExitWith2() {
        assertThat(cli.run().exitCode()).isEqualTo(AtlasCli.EXIT_USAGE);
        assertThat(cli.run("types", "plan").exitCode()).isEqualTo(AtlasCli.EXIT_USAGE);
    }

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

    @Test
    void typesExportWritesYaml() {
        server.on(GET, "/api/atlas/v2/types/typedef/name/my_table", 200,
                "{\"category\":\"ENTITY\",\"name\":\"my_table\",\"guid\":\"g1\",\"createdBy\":\"admin\","
                        + "\"superTypes\":[\"DataSet\"],\"attributeDefs\":[]}");

        CliRunner.Result result = cli.run("types", "export", "my_table");

        assertThat(result.exitCode()).as(result.err()).isZero();
        assertThat(result.out()).contains("name: my_table").doesNotContain("createdBy").doesNotContain("guid");
    }

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

    @Test
    void checkReportsProcessProblemsAndDuplicates() {
        String search = "/api/atlas/v2/search/basic";
        server.on(POST, search, req -> {
            SearchParameters params = AtlasJson.fromJson(req.body, SearchParameters.class);
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

    private static String header(String type, String guid, String qualifiedName) {
        return "{\"typeName\":\"" + type + "\",\"guid\":\"" + guid + "\",\"status\":\"ACTIVE\","
                + "\"attributes\":{\"qualifiedName\":\"" + qualifiedName + "\"}}";
    }

    private static String process(String guid, String qualifiedName, String inputs, String outputs) {
        return "{\"typeName\":\"Process\",\"guid\":\"" + guid + "\",\"attributes\":{\"qualifiedName\":\""
                + qualifiedName + "\"},\"relationshipAttributes\":{\"inputs\":" + inputs + ",\"outputs\":" + outputs + "}}";
    }

    private static String related(String name, String relationshipStatus, String entityStatus) {
        return "{\"guid\":\"g-" + name + "\",\"typeName\":\"hive_table\",\"displayText\":\"" + name + "\","
                + "\"entityStatus\":\"" + entityStatus + "\",\"relationshipStatus\":\"" + relationshipStatus + "\"}";
    }

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
        // Not in the batch: stays a reference by qualifiedName.
        assertThat(((AtlasObjectId) entities.get(0).getAttribute("db")).qualifiedName()).isEqualTo("db@prod");
    }

    @Test
    void qualifiedNameGlobs() {
        assertThat(EventsCommand.Tail.glob("sales.*@prod").matcher("sales.orders@prod").matches()).isTrue();
        assertThat(EventsCommand.Tail.glob("sales.*@prod").matcher("salesXorders@prod").matches()).isFalse();
        assertThat(EventsCommand.Tail.glob("t?@d").matcher("t1@d").matches()).isTrue();
    }

    @Test
    void csvParserHandlesQuotesAndLineBreaks() {
        assertThat(Csv.parse("a,b\r\n\"x,1\",\"say \"\"hi\"\"\"\n\n,\n"))
                .containsExactly(List.of("a", "b"), List.of("x,1", "say \"hi\""), List.of("", ""));
        assertThat(AtlasObjectId.byQualifiedName("t", "q").qualifiedName()).isEqualTo("q");
    }
}
