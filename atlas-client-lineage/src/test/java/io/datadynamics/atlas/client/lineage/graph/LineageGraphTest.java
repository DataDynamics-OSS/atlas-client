package io.datadynamics.atlas.client.lineage.graph;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.lineage.LineageFixtures;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LineageGraphTest {

    static LineageGraph graph() {
        return LineageGraph.from(AtlasJson.fromJson(LineageFixtures.LINEAGE, AtlasLineageInfo.class), "etl_job"::equals);
    }

    private static List<String> guids(List<LineageNode> nodes) {
        return nodes.stream().map(LineageNode::guid).collect(Collectors.toList());
    }

    @Test
    void nodesAreClassified() {
        LineageGraph graph = graph();

        assertThat(graph.base().qualifiedName()).isEqualTo("mart.daily@prod");
        assertThat(guids(graph.datasets())).containsExactly("sales", "customer", "daily", "dashboard");
        assertThat(guids(graph.processes())).containsExactly("p_agg", "p_fix", "p_report");
        assertThat(graph.find("hive_table", "raw.sales@prod")).map(LineageNode::guid).contains("sales");
        assertThat(graph.node("dashboard").orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void directNeighbours() {
        LineageGraph graph = graph();
        LineageNode agg = graph.node("p_agg").orElseThrow();

        assertThat(guids(graph.inputsOf(agg))).containsExactly("sales", "customer");
        assertThat(guids(graph.outputsOf(agg))).containsExactly("daily");
    }

    @Test
    void transitiveTraversalHandlesCycles() {
        LineageGraph graph = graph();
        LineageNode daily = graph.base();

        assertThat(guids(graph.upstream(daily))).containsExactly("p_agg", "p_fix", "sales", "customer");
        assertThat(guids(graph.downstream(daily))).containsExactly("p_fix", "p_report", "dashboard");
        assertThat(guids(graph.upstreamDatasets(daily))).containsExactly("sales", "customer");
        assertThat(guids(graph.downstreamDatasets(graph.node("sales").orElseThrow()))).containsExactly("daily", "dashboard");
    }

    @Test
    void rootsAndLeaves() {
        LineageGraph graph = graph();

        assertThat(guids(graph.roots())).containsExactly("sales", "customer");
        assertThat(guids(graph.leaves())).containsExactly("dashboard");
    }

    @Test
    void impactHasDistancesAndPaths() {
        LineageGraph graph = graph();

        List<Impact> impact = graph.impactOf(graph.node("sales").orElseThrow());

        assertThat(impact).extracting(i -> i.dataset().guid()).containsExactly("daily", "dashboard");
        assertThat(impact).extracting(Impact::distance).containsExactly(1, 2);
        assertThat(guids(impact.get(1).path())).containsExactly("sales", "p_agg", "daily", "p_report", "dashboard");
    }

    @Test
    void sourcesOfDataset() {
        LineageGraph graph = graph();

        List<Impact> sources = graph.sourcesOf(graph.node("dashboard").orElseThrow());

        assertThat(sources).extracting(i -> i.dataset().guid() + "@" + i.distance())
                .containsExactly("daily@1", "sales@2", "customer@2");
    }

    @Test
    void pathsEnumeratesSimplePaths() {
        LineageGraph graph = graph();

        List<List<LineageNode>> paths = graph.paths(graph.node("customer").orElseThrow(), graph.node("dashboard").orElseThrow());

        assertThat(paths).hasSize(1);
        assertThat(guids(paths.get(0))).containsExactly("customer", "p_agg", "daily", "p_report", "dashboard");
    }

    @Test
    void mermaidOutput() {
        String mermaid = graph().toMermaid();

        assertThat(mermaid)
                .startsWith("flowchart LR\n")
                .contains("n0[\"sales<br/>hive_table\"]")
                .contains("n2([\"daily #quot;agg#quot;<br/>etl_job\"])")
                .contains("n0 --> n2")
                .contains("class n3 base")
                .contains("class n6 deleted");
    }

    @Test
    void dotOutput() {
        String dot = graph().toDot();

        assertThat(dot)
                .startsWith("digraph lineage {")
                .contains("\"p_agg\" [label=\"daily \\\"agg\\\"\\netl_job\", shape=ellipse];")
                .contains("\"daily\" [label=\"mart.daily@prod\\nhive_table\", shape=box, style=\"bold\"];")
                .contains("\"dashboard\" [label=\"dash@prod\\nbi_dashboard\", shape=box, style=\"dashed\"];")
                .contains("\"sales\" -> \"p_agg\";");
    }

    @Test
    void entityWithoutLineageUsesFallbackBase() {
        AtlasLineageInfo info = AtlasJson.fromJson("{\"baseEntityGuid\":\"lonely\",\"guidEntityMap\":{},\"relations\":[]}",
                AtlasLineageInfo.class);

        LineageGraph graph = LineageGraph.from(info, t -> false);

        assertThat(graph.base().guid()).isEqualTo("lonely");
        assertThat(graph.impactOf(graph.base())).isEmpty();
        assertThat(graph.edges()).isEmpty();
    }
}
