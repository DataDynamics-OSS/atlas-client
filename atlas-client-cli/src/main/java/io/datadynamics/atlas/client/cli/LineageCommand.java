package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.lineage.AtlasLineage;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.graph.Impact;
import io.datadynamics.atlas.client.lineage.graph.LineageGraph;
import io.datadynamics.atlas.client.lineage.graph.LineageNode;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageDirection;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code atlas lineage} 명령 그룹입니다. 리니지 그래프 출력과 영향도 분석 하위 명령을 묶습니다.
 * 리니지 조회와 그래프 계산은 {@link AtlasLineage}가 합니다.
 */
@Command(name = "lineage", description = "Shows lineage graphs and impact analysis.",
        subcommands = {LineageCommand.Graph.class, LineageCommand.ImpactCommand.class})
final class LineageCommand {

    /**
     * 노드 목록을 쉼표로 이은 문자열로 만듭니다. 목록이 비어 있으면 {@code -}를 돌려줍니다.
     *
     * @param nodes 출력할 노드
     * @return 텍스트 출력에 쓸 문자열
     */
    static String labels(List<LineageNode> nodes) {
        return nodes.isEmpty() ? "-" : nodes.stream().map(LineageNode::toString).collect(Collectors.joining(", "));
    }

    @Command(name = "graph", description = "Prints the lineage around an entity as text, Mermaid or Graphviz DOT.")
    /**
     * {@code atlas lineage graph}: typeName과 qualifiedName으로 지정한 엔티티 주변의 리니지를 출력합니다.
     * <p>
     * 형식은 텍스트(프로세스마다 입력과 출력 목록), Mermaid, Graphviz DOT 중에서 고릅니다.
     */
    static final class Graph extends ClientCommand {

        @Parameters(index = "0", paramLabel = "TYPE")
        String typeName;

        @Parameters(index = "1", paramLabel = "QUALIFIED_NAME")
        String qualifiedName;

        @Option(names = "--direction", description = "${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})",
                defaultValue = "BOTH")
        LineageDirection direction;

        @Option(names = "--depth", defaultValue = "3", description = "Process hops in each direction (default: ${DEFAULT-VALUE})")
        int depth;

        @Option(names = {"-f", "--format"}, defaultValue = "text",
                description = "${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
        Format format;

        /** 출력 형식입니다. 값 이름이 그대로 명령줄 값이 됩니다. */
        enum Format { text, mermaid, dot }

        /**
         * 리니지 그래프를 조회해 선택한 형식으로 출력합니다.
         *
         * @return 항상 0. 엔티티가 없는 등의 실패는 예외로 알립니다
         */
        @Override
        public Integer call() {
            LineageGraph graph = new AtlasLineage(client())
                    .graph(EntityRef.of(typeName, qualifiedName), direction, depth);
            PrintWriter out = out();
            switch (format) {
                case mermaid -> out.print(graph.toMermaid());
                case dot -> out.print(graph.toDot());
                default -> {
                    out.printf("Lineage of %s: %d datasets, %d processes%n", graph.base(), graph.datasets().size(),
                            graph.processes().size());
                    for (LineageNode process : graph.processes()) {
                        out.printf("%n%s%n  inputs : %s%n  outputs: %s%n", process, labels(graph.inputsOf(process)),
                                labels(graph.outputsOf(process)));
                    }
                }
            }
            out.flush();
            return 0;
        }
    }

    @Command(name = "impact", description = "Lists the datasets affected by a change to an entity "
            + "(or, with --upstream, the datasets it is derived from).")
    /**
     * {@code atlas lineage impact}: 엔티티가 바뀌면 영향을 받는 하위 데이터셋을 가까운 것부터 나열합니다.
     * <p>
     * {@code --upstream}을 주면 반대로 이 엔티티를 만드는 데 쓰인 상위(원천) 데이터셋을 나열합니다.
     * 클래스 이름이 {@code Impact}가 아닌 것은 결과 타입 {@link Impact}와 이름이 겹치지 않게 하기 위해서입니다.
     */
    static final class ImpactCommand extends ClientCommand {

        @Parameters(index = "0", paramLabel = "TYPE")
        String typeName;

        @Parameters(index = "1", paramLabel = "QUALIFIED_NAME")
        String qualifiedName;

        @Option(names = "--depth", defaultValue = "5", description = "Maximum process hops (default: ${DEFAULT-VALUE})")
        int depth;

        @Option(names = "--upstream", description = "Show sources instead of impacted datasets")
        boolean upstream;

        /**
         * 영향도(또는 원천) 분석 결과를 홉 수, 데이터셋, 경로 열로 된 표로 출력합니다.
         * 경로는 기준 엔티티에서 데이터셋까지의 최단 경로 하나이며, 중간 프로세스도 포함합니다.
         *
         * @return 항상 0. 결과가 없어도 0입니다
         */
        @Override
        public Integer call() {
            AtlasLineage lineage = new AtlasLineage(client());
            EntityRef ref = EntityRef.of(typeName, qualifiedName);
            List<Impact> impacts = upstream ? lineage.sources(ref, depth) : lineage.impact(ref, depth);
            PrintWriter out = out();
            if (impacts.isEmpty()) {
                out.println(upstream ? "No upstream datasets." : "No downstream datasets.");
                return 0;
            }
            out.printf("%-4s  %-40s  %s%n", "HOPS", "DATASET", "PATH");
            for (Impact impact : impacts) {
                String path = impact.path().stream().map(LineageNode::label).collect(Collectors.joining(" -> "));
                out.printf("%-4d  %-40s  %s%n", impact.distance(), impact.dataset(), path);
            }
            out.printf("%d %s datasets%n", impacts.size(), upstream ? "upstream" : "downstream");
            return 0;
        }
    }
}
