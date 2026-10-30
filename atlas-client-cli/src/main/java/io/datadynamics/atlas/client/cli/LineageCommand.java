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

@Command(name = "lineage", description = "Shows lineage graphs and impact analysis.",
        subcommands = {LineageCommand.Graph.class, LineageCommand.ImpactCommand.class})
final class LineageCommand {

    static String labels(List<LineageNode> nodes) {
        return nodes.isEmpty() ? "-" : nodes.stream().map(LineageNode::toString).collect(Collectors.joining(", "));
    }

    @Command(name = "graph", description = "Prints the lineage around an entity as text, Mermaid or Graphviz DOT.")
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

        enum Format { text, mermaid, dot }

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
    static final class ImpactCommand extends ClientCommand {

        @Parameters(index = "0", paramLabel = "TYPE")
        String typeName;

        @Parameters(index = "1", paramLabel = "QUALIFIED_NAME")
        String qualifiedName;

        @Option(names = "--depth", defaultValue = "5", description = "Maximum process hops (default: ${DEFAULT-VALUE})")
        int depth;

        @Option(names = "--upstream", description = "Show sources instead of impacted datasets")
        boolean upstream;

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
