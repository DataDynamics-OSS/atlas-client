package io.datadynamics.atlas.client.lineage.graph;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.lineage.LineageFixtures;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LineageGraph}의 노드 분류, 탐색(순환 포함), 영향·원천 분석, 경로 열거, Mermaid·DOT 출력을
 * {@link LineageFixtures#LINEAGE} 응답으로 검증합니다.
 */
class LineageGraphTest {

    /** 공용 리니지 응답으로 그래프를 만듭니다. {@code etl_job} 타입만 프로세스로 봅니다. */
    static LineageGraph graph() {
        return LineageGraph.from(AtlasJson.fromJson(LineageFixtures.LINEAGE, AtlasLineageInfo.class), "etl_job"::equals);
    }

    private static List<String> guids(List<LineageNode> nodes) {
        return nodes.stream().map(LineageNode::guid).collect(Collectors.toList());
    }

    /** 기준 노드, 데이터셋·프로세스 분류, typeName·qualifiedName 검색, 삭제 상태 판별이 올바른지 확인합니다. */
    @Test
    void nodesAreClassified() {
        LineageGraph graph = graph();

        assertThat(graph.base().qualifiedName()).isEqualTo("mart.daily@prod");
        assertThat(guids(graph.datasets())).containsExactly("sales", "customer", "daily", "dashboard");
        assertThat(guids(graph.processes())).containsExactly("p_agg", "p_fix", "p_report");
        assertThat(graph.find("hive_table", "raw.sales@prod")).map(LineageNode::guid).contains("sales");
        assertThat(graph.node("dashboard").orElseThrow().isDeleted()).isTrue();
    }

    /** 프로세스 {@code p_agg}의 바로 앞 노드가 입력 데이터셋, 바로 뒤 노드가 출력 데이터셋인지 확인합니다. */
    @Test
    void directNeighbours() {
        LineageGraph graph = graph();
        LineageNode agg = graph.node("p_agg").orElseThrow();

        assertThat(guids(graph.inputsOf(agg))).containsExactly("sales", "customer");
        assertThat(guids(graph.outputsOf(agg))).containsExactly("daily");
    }

    /**
     * {@code daily ↔ p_fix} 순환이 있어도 upstream/downstream 탐색이 끝나고, 각 노드를 한 번씩 가까운 순서로
     * 돌려주는지 확인합니다.
     */
    @Test
    void transitiveTraversalHandlesCycles() {
        LineageGraph graph = graph();
        LineageNode daily = graph.base();

        assertThat(guids(graph.upstream(daily))).containsExactly("p_agg", "p_fix", "sales", "customer");
        assertThat(guids(graph.downstream(daily))).containsExactly("p_fix", "p_report", "dashboard");
        assertThat(guids(graph.upstreamDatasets(daily))).containsExactly("sales", "customer");
        assertThat(guids(graph.downstreamDatasets(graph.node("sales").orElseThrow()))).containsExactly("daily", "dashboard");
    }

    /** 들어오는 간선이 없는 노드(루트)와 나가는 간선이 없는 노드(리프)를 올바르게 찾는지 확인합니다. */
    @Test
    void rootsAndLeaves() {
        LineageGraph graph = graph();

        assertThat(guids(graph.roots())).containsExactly("sales", "customer");
        assertThat(guids(graph.leaves())).containsExactly("dashboard");
    }

    /**
     * {@code sales}의 영향 분석이 하위 데이터셋을 거리 순으로 돌려주고, 거리는 경로상의 프로세스 수이며, 경로에
     * 프로세스가 포함되는지 확인합니다.
     */
    @Test
    void impactHasDistancesAndPaths() {
        LineageGraph graph = graph();

        List<Impact> impact = graph.impactOf(graph.node("sales").orElseThrow());

        assertThat(impact).extracting(i -> i.dataset().guid()).containsExactly("daily", "dashboard");
        assertThat(impact).extracting(Impact::distance).containsExactly(1, 2);
        assertThat(guids(impact.get(1).path())).containsExactly("sales", "p_agg", "daily", "p_report", "dashboard");
    }

    /** {@code dashboard}의 원천 분석이 상위 데이터셋을 거리 순으로 돌려주는지 확인합니다. */
    @Test
    void sourcesOfDataset() {
        LineageGraph graph = graph();

        List<Impact> sources = graph.sourcesOf(graph.node("dashboard").orElseThrow());

        assertThat(sources).extracting(i -> i.dataset().guid() + "@" + i.distance())
                .containsExactly("daily@1", "sales@2", "customer@2");
    }

    /** 순환이 있어도 {@code customer}에서 {@code dashboard}까지의 단순 경로는 하나뿐인지 확인합니다. */
    @Test
    void pathsEnumeratesSimplePaths() {
        LineageGraph graph = graph();

        List<List<LineageNode>> paths = graph.paths(graph.node("customer").orElseThrow(), graph.node("dashboard").orElseThrow());

        assertThat(paths).hasSize(1);
        assertThat(guids(paths.get(0))).containsExactly("customer", "p_agg", "daily", "p_report", "dashboard");
    }

    /**
     * Mermaid 출력에서 노드 모양(데이터셋 사각형, 프로세스 둥근 모양), 따옴표 이스케이프, 간선, 기준·삭제 노드 클래스가
     * 올바른지 확인합니다.
     */
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

    /** DOT 출력에서 노드 모양, 따옴표 이스케이프, 기준 노드 bold, 삭제 노드 dashed, 간선이 올바른지 확인합니다. */
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

    /**
     * 응답에 엔티티와 관계가 없어도 baseEntityGuid로 기준 노드를 만들고, 영향 분석과 간선이 비어 있는지
     * 확인합니다.
     */
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
