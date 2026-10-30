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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The {@code atlas} command line tool against a real Atlas. */
class CliIT extends AtlasIT {

    record Result(int exitCode, String out, String err) {
    }

    private static String table;
    private static String column;
    private static String job;

    @TempDir
    static Path dir;

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

    static Result cli(String... args) {
        AtlasTestServer server = AtlasTestServer.shared();
        List<String> all = new ArrayList<>(List.of(args));
        if (!args[0].equals("events")) {
            all.addAll(List.of("--url", server.url(), "-u", server.username(), "--password", server.password()));
        }
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = AtlasCli.commandLine(new AtlasCli());
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        System.setIn(new ByteArrayInputStream(new byte[0]));
        int exitCode = commandLine.execute(all.toArray(String[]::new));
        return new Result(exitCode, out.toString(), err.toString());
    }

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
        // The columns reference the table of the same file and batch.
        assertThat(result.out()).contains("Imported 3 entities: 3 created");
        AtlasEntityWithExtInfo stored = atlas.entities().getByQualifiedName(table, tableName);
        cleanupEntity(stored.getEntity().getGuid());
        assertThat(stored.getEntity().getAttribute("rowCount")).isEqualTo(42);
        assertThat(stored.getEntity().getLabels()).containsExactlyInAnyOrder("gold", "finance");
        assertThat((List<?>) stored.getEntity().getRelationshipAttributes().get("columns")).hasSize(2);

        Result get = cli("entities", "get", column, tableName + ".id");
        assertThat(get.out()).contains("\"qualifiedName\" : \"" + tableName + ".id\"");
    }

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

        // An orphan process, and a process whose output gets deleted.
        String orphan = atlas.entities().createOrUpdate(new AtlasEntity(job,
                Map.of("qualifiedName", name("orphan@cli"), "name", "orphan"))).firstGuid().orElseThrow();
        cleanupEntity(orphan);
        atlas.entities().deleteByQualifiedName(table, report);

        // Search runs on the index, which Atlas updates asynchronously.
        Result check = awaitValue(() -> cli("check", "--process-type", job),
                r -> r.out().contains("2 problems found."), Duration.ofSeconds(60));
        assertThat(check.exitCode()).as(check.out() + check.err()).isEqualTo(AtlasCli.EXIT_FAILURE);
        assertThat(check.out())
                .contains("ORPHAN_PROCESS     " + job + ":" + name("orphan@cli"))
                .contains("INCOMPLETE_PROCESS " + job + ":" + name("publish@cli"))
                .contains("2 problems found.")
                .doesNotContain(name("load@cli"));
    }

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
