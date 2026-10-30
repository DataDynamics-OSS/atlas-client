package io.datadynamics.atlas.client.it;

import io.datadynamics.atlas.client.lineage.AtlasLineage;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.LineageException;
import io.datadynamics.atlas.client.lineage.LineageRegistration;
import io.datadynamics.atlas.client.lineage.MissingEntityPolicy;
import io.datadynamics.atlas.client.lineage.graph.Impact;
import io.datadynamics.atlas.client.lineage.graph.LineageGraph;
import io.datadynamics.atlas.client.lineage.graph.LineageNode;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lineage registration and traversal against a real Atlas, with custom dataset, process and
 * column lineage types.
 */
class LineageIT extends AtlasIT {

    private static String table;
    private static String column;
    private static String job;
    private static String columnLineage;
    private static AtlasLineage lineage;

    @BeforeAll
    static void createTypes() {
        table = name("table");
        column = name("column");
        job = name("job");
        columnLineage = name("column_lineage");
        cleanupTypes(columnLineage, job, column, table);
        new TypeDefManager(atlas).sync(
                entity(table).dataSet(),
                entity(column).dataSet(),
                entity(job).process(),
                entity(columnLineage).process().attribute(attr("query", job)));
        lineage = new AtlasLineage(atlas).columnLineageType(columnLineage, "query");
    }

    private static EntityRef table(String suffix) {
        return EntityRef.of(table, name(suffix) + "@it");
    }

    private static EntityRef column(String suffix) {
        return EntityRef.of(column, name(suffix) + "@it");
    }

    private static void createDatasets(EntityRef... refs) {
        List<AtlasEntity> entities = java.util.Arrays.stream(refs)
                .map(r -> new AtlasEntity(r.typeName(), Map.of("qualifiedName", r.qualifiedName(), "name", r.defaultName())))
                .collect(Collectors.toList());
        atlas.entities().createOrUpdate(entities).created().forEach(h -> cleanupEntity(h.getGuid()));
    }

    private static List<String> qualifiedNames(List<LineageNode> nodes) {
        return nodes.stream().map(LineageNode::qualifiedName).collect(Collectors.toList());
    }

    @Test
    void registeredLineageIsVisibleAndImpactIsComputed() {
        EntityRef sales = table("sales");
        EntityRef customer = table("customer");
        EntityRef daily = table("daily");
        EntityRef report = table("report");
        createDatasets(sales, customer, daily, report);

        LineageRegistration first = lineage.process(job, name("agg") + "@it")
                .inputs(sales, customer).outputs(daily).register();
        LineageRegistration second = lineage.process(job, name("report") + "@it")
                .inputs(daily).outputs(report).register();
        first.guids().values().forEach(AtlasIT::cleanupEntity);
        second.guids().values().forEach(AtlasIT::cleanupEntity);

        LineageGraph graph = lineage.graph(daily);
        assertThat(graph.base().qualifiedName()).isEqualTo(daily.qualifiedName());
        assertThat(qualifiedNames(graph.upstreamDatasets(graph.base())))
                .containsExactlyInAnyOrder(sales.qualifiedName(), customer.qualifiedName());
        assertThat(qualifiedNames(graph.downstreamDatasets(graph.base()))).containsExactly(report.qualifiedName());
        assertThat(graph.processes()).allMatch(LineageNode::process).hasSize(2);

        List<Impact> impact = lineage.impact(sales, 5);
        assertThat(impact).extracting(i -> i.dataset().qualifiedName() + "@" + i.distance())
                .containsExactly(daily.qualifiedName() + "@1", report.qualifiedName() + "@2");
    }

    @Test
    void reRegisteringReplacesInputsAndKeepsTheProcess() {
        EntityRef a = table("re_a");
        EntityRef b = table("re_b");
        EntityRef out = table("re_out");
        createDatasets(a, b, out);
        String processName = name("re_job") + "@it";

        String guid = lineage.process(job, processName).inputs(a).outputs(out).register()
                .guidOf(job, processName).orElseThrow();
        cleanupEntity(guid);
        String guidAgain = lineage.process(job, processName).inputs(b).outputs(out).register()
                .guidOf(job, processName).orElseThrow();

        assertThat(guidAgain).as("placeholder guid must resolve to the existing process").isEqualTo(guid);
        LineageGraph graph = lineage.graph(out);
        assertThat(qualifiedNames(graph.upstreamDatasets(graph.base()))).containsExactly(b.qualifiedName());
    }

    @Test
    void missingDatasetsFailOrAreCreated() {
        EntityRef existing = table("existing");
        EntityRef missing = table("missing");
        createDatasets(existing);

        assertThatThrownBy(() -> lineage.process(job, name("fails") + "@it").inputs(missing).outputs(existing).register())
                .isInstanceOf(LineageException.class)
                .satisfies(e -> assertThat(((LineageException) e).getMissingEntities()).containsExactly(missing));
        assertThat(atlas.entities().findGuid(job, name("fails") + "@it")).isEmpty();

        AtlasLineage creating = new AtlasLineage(atlas).missingEntities(MissingEntityPolicy.CREATE);
        LineageRegistration registration = creating.process(job, name("creates") + "@it")
                .inputs(missing).outputs(existing).register();
        registration.guids().values().forEach(AtlasIT::cleanupEntity);

        assertThat(registration.createdEntities()).containsExactly(missing);
        assertThat(atlas.entities().findGuid(missing.typeName(), missing.qualifiedName())).isPresent();
    }

    @Test
    void columnLineageIsLinkedToItsProcess() {
        EntityRef src = table("col_src");
        EntityRef dst = table("col_dst");
        EntityRef price = column("col_src.price");
        EntityRef qty = column("col_src.qty");
        EntityRef amount = column("col_dst.amount");
        createDatasets(src, dst, price, qty, amount);
        String processName = name("col_job") + "@it";

        LineageRegistration registration = lineage.process(job, processName)
                .inputs(src).outputs(dst)
                .columnLineage(amount, price, qty)
                .register();
        registration.guids().values().forEach(AtlasIT::cleanupEntity);

        String processGuid = registration.guidOf(job, processName).orElseThrow();
        String columnLineageGuid = registration.guidOf(columnLineage, processName + ":" + amount.qualifiedName())
                .orElseThrow();
        AtlasEntity stored = atlas.entities().get(columnLineageGuid).getEntity();
        Object query = stored.getAttribute("query");
        assertThat(query).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) query).get("guid")).isEqualTo(processGuid);

        LineageGraph columnGraph = lineage.graph(amount);
        assertThat(qualifiedNames(columnGraph.upstreamDatasets(columnGraph.base())))
                .containsExactlyInAnyOrder(price.qualifiedName(), qty.qualifiedName());
    }

    @Test
    void processTypesAreResolvedFromTypeDefinitions() {
        assertThat(new io.datadynamics.atlas.client.lineage.ProcessTypeResolver(atlas).test(job)).isTrue();
        assertThat(new io.datadynamics.atlas.client.lineage.ProcessTypeResolver(atlas).test(table)).isFalse();
        assertThat(AtlasObjectId.QUALIFIED_NAME).isEqualTo("qualifiedName");
    }
}
