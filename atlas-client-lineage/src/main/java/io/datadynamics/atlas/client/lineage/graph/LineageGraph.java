package io.datadynamics.atlas.client.lineage.graph;

import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageRelation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Traversable lineage graph built from an Atlas lineage response.
 * <p>
 * Edges point in data-flow direction ({@code input dataset -> process -> output dataset}).
 * "Upstream" follows edges backwards (where data comes from), "downstream" forwards
 * (where data goes). Cycles (a job reading and writing the same table) are handled.
 */
public final class LineageGraph {

    /** Upper bound for {@link #paths(LineageNode, LineageNode)} to keep dense graphs tractable. */
    public static final int MAX_PATHS = 1000;

    private final LineageNode base;
    private final Map<String, LineageNode> nodes;
    private final List<LineageEdge> edges;
    private final Map<String, List<LineageEdge>> outgoing = new HashMap<>();
    private final Map<String, List<LineageEdge>> incoming = new HashMap<>();

    private LineageGraph(LineageNode base, Map<String, LineageNode> nodes, List<LineageEdge> edges) {
        this.base = base;
        this.nodes = Collections.unmodifiableMap(nodes);
        this.edges = List.copyOf(edges);
        for (LineageEdge edge : edges) {
            outgoing.computeIfAbsent(edge.from().guid(), k -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.to().guid(), k -> new ArrayList<>()).add(edge);
        }
    }

    /**
     * @param isProcessType tells which entity types are processes (see {@code ProcessTypeResolver})
     */
    public static LineageGraph from(AtlasLineageInfo info, Predicate<String> isProcessType) {
        return from(info, isProcessType, null);
    }

    /**
     * @param fallbackBase header used for the base entity when Atlas omits it (entities without lineage)
     */
    public static LineageGraph from(AtlasLineageInfo info, Predicate<String> isProcessType,
                                    AtlasEntityHeader fallbackBase) {
        Map<String, LineageNode> nodes = new LinkedHashMap<>();
        info.getGuidEntityMap().forEach((guid, header) -> {
            if (header.getGuid() == null) {
                header.setGuid(guid);
            }
            nodes.put(guid, LineageNode.of(header, isProcessType.test(header.getTypeName())));
        });

        String baseGuid = info.getBaseEntityGuid();
        if (baseGuid != null && !nodes.containsKey(baseGuid)) {
            AtlasEntityHeader header = fallbackBase != null ? fallbackBase : new AtlasEntityHeader();
            header.setGuid(baseGuid);
            nodes.put(baseGuid, LineageNode.of(header, isProcessType.test(header.getTypeName())));
        }

        List<LineageEdge> edges = new ArrayList<>();
        for (LineageRelation relation : info.getRelations()) {
            LineageNode from = nodeOrPlaceholder(nodes, relation.getFromEntityId());
            LineageNode to = nodeOrPlaceholder(nodes, relation.getToEntityId());
            edges.add(new LineageEdge(from, to, relation.getRelationshipId()));
        }
        return new LineageGraph(baseGuid == null ? null : nodes.get(baseGuid), nodes, edges);
    }

    private static LineageNode nodeOrPlaceholder(Map<String, LineageNode> nodes, String guid) {
        return nodes.computeIfAbsent(guid, g -> {
            AtlasEntityHeader header = new AtlasEntityHeader();
            header.setGuid(g);
            return LineageNode.of(header, false);
        });
    }

    // ---------------------------------------------------------------- lookup

    /** The entity the lineage was requested for. */
    public LineageNode base() {
        return base;
    }

    public Collection<LineageNode> nodes() {
        return nodes.values();
    }

    public List<LineageEdge> edges() {
        return edges;
    }

    public Optional<LineageNode> node(String guid) {
        return Optional.ofNullable(nodes.get(guid));
    }

    public Optional<LineageNode> find(String typeName, String qualifiedName) {
        return nodes.values().stream()
                .filter(n -> typeName.equals(n.typeName()) && qualifiedName.equals(n.qualifiedName()))
                .findFirst();
    }

    public List<LineageNode> datasets() {
        return nodes.values().stream().filter(LineageNode::isDataset).collect(Collectors.toList());
    }

    public List<LineageNode> processes() {
        return nodes.values().stream().filter(LineageNode::process).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- traversal

    /** Direct predecessors: the processes writing a dataset, or the input datasets of a process. */
    public List<LineageNode> inputsOf(LineageNode node) {
        return incoming.getOrDefault(node.guid(), List.of()).stream().map(LineageEdge::from).collect(Collectors.toList());
    }

    /** Direct successors: the processes reading a dataset, or the output datasets of a process. */
    public List<LineageNode> outputsOf(LineageNode node) {
        return outgoing.getOrDefault(node.guid(), List.of()).stream().map(LineageEdge::to).collect(Collectors.toList());
    }

    /** Everything the node depends on, nearest first. */
    public List<LineageNode> upstream(LineageNode node) {
        return reachable(node, false);
    }

    /** Everything that depends on the node, nearest first. */
    public List<LineageNode> downstream(LineageNode node) {
        return reachable(node, true);
    }

    public List<LineageNode> upstreamDatasets(LineageNode node) {
        return upstream(node).stream().filter(LineageNode::isDataset).collect(Collectors.toList());
    }

    public List<LineageNode> downstreamDatasets(LineageNode node) {
        return downstream(node).stream().filter(LineageNode::isDataset).collect(Collectors.toList());
    }

    /** Nodes without inputs: original sources of the data in this graph. */
    public List<LineageNode> roots() {
        return nodes.values().stream().filter(n -> !incoming.containsKey(n.guid())).collect(Collectors.toList());
    }

    /** Nodes without outputs: final consumers of the data in this graph. */
    public List<LineageNode> leaves() {
        return nodes.values().stream().filter(n -> !outgoing.containsKey(n.guid())).collect(Collectors.toList());
    }

    /**
     * Datasets affected by a change of {@code node}, with their distance and one shortest path.
     * Ordered by distance.
     */
    public List<Impact> impactOf(LineageNode node) {
        return impacts(node, true);
    }

    /** Datasets {@code node} is derived from, with their distance and one shortest path. */
    public List<Impact> sourcesOf(LineageNode node) {
        return impacts(node, false);
    }

    /** All simple paths from {@code from} to {@code to} in data-flow direction (at most {@value #MAX_PATHS}). */
    public List<List<LineageNode>> paths(LineageNode from, LineageNode to) {
        List<List<LineageNode>> result = new ArrayList<>();
        Deque<LineageNode> current = new ArrayDeque<>();
        Set<String> onPath = new LinkedHashSet<>();
        current.addLast(from);
        onPath.add(from.guid());
        collectPaths(from, to, current, onPath, result);
        return result;
    }

    private void collectPaths(LineageNode node, LineageNode target, Deque<LineageNode> current,
                              Set<String> onPath, List<List<LineageNode>> result) {
        if (result.size() >= MAX_PATHS) {
            return;
        }
        if (node.equals(target)) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (LineageNode next : outputsOf(node)) {
            if (onPath.add(next.guid())) {
                current.addLast(next);
                collectPaths(next, target, current, onPath, result);
                current.removeLast();
                onPath.remove(next.guid());
            }
        }
    }

    private List<LineageNode> reachable(LineageNode start, boolean forward) {
        return bfs(start, forward).keySet().stream()
                .filter(n -> !n.equals(start))
                .collect(Collectors.toList());
    }

    /** Breadth-first search; maps each reached node to its predecessor on a shortest path. */
    private Map<LineageNode, LineageNode> bfs(LineageNode start, boolean forward) {
        Map<LineageNode, LineageNode> previous = new LinkedHashMap<>();
        previous.put(start, null);
        Deque<LineageNode> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            LineageNode node = queue.poll();
            for (LineageNode next : forward ? outputsOf(node) : inputsOf(node)) {
                if (!previous.containsKey(next)) {
                    previous.put(next, node);
                    queue.add(next);
                }
            }
        }
        return previous;
    }

    private List<Impact> impacts(LineageNode start, boolean forward) {
        Map<LineageNode, LineageNode> previous = bfs(start, forward);
        List<Impact> result = new ArrayList<>();
        for (LineageNode node : previous.keySet()) {
            if (node.equals(start) || !node.isDataset()) {
                continue;
            }
            List<LineageNode> path = new ArrayList<>();
            for (LineageNode n = node; n != null; n = previous.get(n)) {
                path.add(n);
            }
            Collections.reverse(path);
            int distance = (int) path.stream().filter(LineageNode::process).count();
            result.add(new Impact(node, distance, path));
        }
        result.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        return result;
    }

    // ---------------------------------------------------------------- rendering

    /** Mermaid flowchart; datasets are boxes, processes are rounded, the base entity is bold. */
    public String toMermaid() {
        Map<String, String> ids = shortIds();
        StringBuilder sb = new StringBuilder("flowchart LR\n");
        for (LineageNode node : nodes.values()) {
            String label = escapeMermaid(node.label()) + "<br/>" + escapeMermaid(String.valueOf(node.typeName()));
            String id = ids.get(node.guid());
            sb.append("  ").append(id)
                    .append(node.process() ? "([\"" : "[\"").append(label).append(node.process() ? "\"])" : "\"]")
                    .append('\n');
        }
        for (LineageEdge edge : edges) {
            sb.append("  ").append(ids.get(edge.from().guid())).append(" --> ").append(ids.get(edge.to().guid())).append('\n');
        }
        sb.append("  classDef base stroke-width:3px\n");
        sb.append("  classDef deleted stroke-dasharray: 5 5,opacity:0.6\n");
        if (base != null) {
            sb.append("  class ").append(ids.get(base.guid())).append(" base\n");
        }
        List<String> deleted = nodes.values().stream().filter(LineageNode::isDeleted)
                .map(n -> ids.get(n.guid())).collect(Collectors.toList());
        if (!deleted.isEmpty()) {
            sb.append("  class ").append(String.join(",", deleted)).append(" deleted\n");
        }
        return sb.toString();
    }

    /** Graphviz DOT; datasets are boxes, processes are ellipses, deleted entities are dashed. */
    public String toDot() {
        StringBuilder sb = new StringBuilder("digraph lineage {\n  rankdir=LR;\n  node [fontname=\"Helvetica\"];\n");
        for (LineageNode node : nodes.values()) {
            sb.append("  \"").append(escapeDot(node.guid())).append("\" [label=\"")
                    .append(escapeDot(node.label())).append("\\n").append(escapeDot(String.valueOf(node.typeName())))
                    .append("\", shape=").append(node.process() ? "ellipse" : "box");
            List<String> styles = new ArrayList<>();
            if (node.isDeleted()) {
                styles.add("dashed");
            }
            if (node.equals(base)) {
                styles.add("bold");
            }
            if (!styles.isEmpty()) {
                sb.append(", style=\"").append(String.join(",", styles)).append('"');
            }
            sb.append("];\n");
        }
        for (LineageEdge edge : edges) {
            sb.append("  \"").append(escapeDot(edge.from().guid())).append("\" -> \"")
                    .append(escapeDot(edge.to().guid())).append("\";\n");
        }
        return sb.append("}\n").toString();
    }

    private Map<String, String> shortIds() {
        Map<String, String> ids = new HashMap<>();
        int i = 0;
        for (String guid : nodes.keySet()) {
            ids.put(guid, "n" + i++);
        }
        return ids;
    }

    private static String escapeMermaid(String value) {
        return value.replace("\"", "#quot;").replace("<", "#lt;").replace(">", "#gt;");
    }

    private static String escapeDot(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        return "LineageGraph{base=" + base + ", nodes=" + nodes.size() + ", edges=" + edges.size() + "}";
    }
}
