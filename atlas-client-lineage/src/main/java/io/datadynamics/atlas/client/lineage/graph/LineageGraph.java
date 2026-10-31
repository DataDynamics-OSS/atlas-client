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
 * Atlas 리니지 응답으로 만든, 탐색할 수 있는 리니지 그래프입니다.
 * <p>
 * 간선은 데이터 흐름 방향({@code 입력 데이터셋 -> 프로세스 -> 출력 데이터셋})을 가리킵니다.
 * "upstream"은 간선을 거꾸로 따라가고(데이터가 어디서 오는지), "downstream"은 정방향으로 따라갑니다
 * (데이터가 어디로 가는지). 순환(같은 테이블을 읽고 쓰는 작업 등)이 있어도 무한히 돌지 않습니다.
 * <p>
 * 만든 뒤에는 바뀌지 않으므로 여러 스레드에서 읽기 전용으로 함께 써도 됩니다. 노드는 guid로 식별합니다.
 */
public final class LineageGraph {

    /**
     * {@link #paths(LineageNode, LineageNode)}가 돌려주는 경로 수의 상한입니다.
     * 간선이 많은 그래프에서도 계산이 감당할 수 있는 수준에 머물게 합니다.
     */
    public static final int MAX_PATHS = 1000;

    private final LineageNode base;
    private final Map<String, LineageNode> nodes;
    private final List<LineageEdge> edges;
    // guid별로 그 노드에서 나가는 간선과 들어오는 간선을 미리 색인해 두어 탐색 때마다 전체 간선을 훑지 않게 합니다.
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
     * Atlas 리니지 응답으로 그래프를 만듭니다.
     *
     * @param info          Atlas 리니지 API 응답
     * @param isProcessType 어떤 엔티티 타입이 프로세스인지 알려 주는 조건({@code ProcessTypeResolver} 참고)
     * @return 리니지 그래프
     */
    public static LineageGraph from(AtlasLineageInfo info, Predicate<String> isProcessType) {
        return from(info, isProcessType, null);
    }

    /**
     * Atlas 리니지 응답으로 그래프를 만들되, 기준 엔티티 정보가 응답에 없을 때 쓸 헤더를 함께 받습니다.
     * <p>
     * 응답의 {@code guidEntityMap}에 있는 엔티티가 노드가 되고, {@code relations}가 간선이 됩니다.
     * 관계가 가리키는 guid가 {@code guidEntityMap}에 없으면 guid만 가진 데이터셋 노드를 만들어 채웁니다.
     * 이 메서드는 응답 안의 헤더에 guid가 비어 있으면 맵의 키로 채워 넣으므로 {@code info}를 변경합니다.
     *
     * @param info          Atlas 리니지 API 응답
     * @param isProcessType 어떤 엔티티 타입이 프로세스인지 알려 주는 조건
     * @param fallbackBase  Atlas가 기준 엔티티를 응답에서 빼먹었을 때(리니지가 없는 엔티티) 기준 노드로 쓸 헤더.
     *                      {@code null}이면 guid만 가진 헤더를 씁니다. 전달한 헤더의 guid는 덮어씁니다.
     * @return 리니지 그래프
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

        // 리니지가 없는 엔티티는 응답의 guidEntityMap에 자기 자신도 없으므로 대체 헤더로 기준 노드를 만듭니다.
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

    /** guid에 해당하는 노드를 돌려주고, 없으면 guid만 가진 데이터셋 노드를 만들어 등록한 뒤 돌려줍니다. */
    private static LineageNode nodeOrPlaceholder(Map<String, LineageNode> nodes, String guid) {
        return nodes.computeIfAbsent(guid, g -> {
            AtlasEntityHeader header = new AtlasEntityHeader();
            header.setGuid(g);
            return LineageNode.of(header, false);
        });
    }

    // ---------------------------------------------------------------- 조회

    /**
     * 리니지를 요청한 기준 엔티티를 돌려줍니다.
     *
     * @return 기준 노드. 응답에 기준 엔티티 guid가 없었으면 {@code null}
     */
    public LineageNode base() {
        return base;
    }

    /**
     * 그래프의 모든 노드를 돌려줍니다(응답에 나온 순서, 변경 불가능).
     *
     * @return 모든 노드
     */
    public Collection<LineageNode> nodes() {
        return nodes.values();
    }

    /**
     * 그래프의 모든 간선을 돌려줍니다(변경 불가능).
     *
     * @return 모든 간선
     */
    public List<LineageEdge> edges() {
        return edges;
    }

    /**
     * guid로 노드를 찾습니다.
     *
     * @param guid 엔티티 guid
     * @return 노드. 그래프에 없으면 빈 값
     */
    public Optional<LineageNode> node(String guid) {
        return Optional.ofNullable(nodes.get(guid));
    }

    /**
     * 타입과 qualifiedName으로 노드를 찾습니다. 여러 개가 맞으면 처음 것을 돌려줍니다.
     *
     * @param typeName      엔티티 타입 이름
     * @param qualifiedName 엔티티 qualifiedName
     * @return 노드. 그래프에 없으면 빈 값
     */
    public Optional<LineageNode> find(String typeName, String qualifiedName) {
        return nodes.values().stream()
                .filter(n -> typeName.equals(n.typeName()) && qualifiedName.equals(n.qualifiedName()))
                .findFirst();
    }

    /**
     * 데이터셋 노드(프로세스가 아닌 노드)만 돌려줍니다.
     *
     * @return 데이터셋 노드 목록
     */
    public List<LineageNode> datasets() {
        return nodes.values().stream().filter(LineageNode::isDataset).collect(Collectors.toList());
    }

    /**
     * 프로세스 노드만 돌려줍니다.
     *
     * @return 프로세스 노드 목록
     */
    public List<LineageNode> processes() {
        return nodes.values().stream().filter(LineageNode::process).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- 탐색

    /**
     * 바로 앞 노드들을 돌려줍니다. 데이터셋이면 그것을 쓰는 프로세스들, 프로세스이면 그 입력 데이터셋들입니다.
     *
     * @param node 기준 노드
     * @return 바로 앞 노드 목록. 없으면 빈 목록
     */
    public List<LineageNode> inputsOf(LineageNode node) {
        return incoming.getOrDefault(node.guid(), List.of()).stream().map(LineageEdge::from).collect(Collectors.toList());
    }

    /**
     * 바로 뒤 노드들을 돌려줍니다. 데이터셋이면 그것을 읽는 프로세스들, 프로세스이면 그 출력 데이터셋들입니다.
     *
     * @param node 기준 노드
     * @return 바로 뒤 노드 목록. 없으면 빈 목록
     */
    public List<LineageNode> outputsOf(LineageNode node) {
        return outgoing.getOrDefault(node.guid(), List.of()).stream().map(LineageEdge::to).collect(Collectors.toList());
    }

    /**
     * 노드가 의존하는 모든 노드(프로세스 포함)를 가까운 것부터 돌려줍니다. 기준 노드 자신은 빠집니다.
     *
     * @param node 기준 노드
     * @return 상위 노드 목록
     */
    public List<LineageNode> upstream(LineageNode node) {
        return reachable(node, false);
    }

    /**
     * 노드에 의존하는 모든 노드(프로세스 포함)를 가까운 것부터 돌려줍니다. 기준 노드 자신은 빠집니다.
     *
     * @param node 기준 노드
     * @return 하위 노드 목록
     */
    public List<LineageNode> downstream(LineageNode node) {
        return reachable(node, true);
    }

    /**
     * {@link #upstream(LineageNode)} 중 데이터셋만 가까운 것부터 돌려줍니다.
     *
     * @param node 기준 노드
     * @return 상위 데이터셋 목록
     */
    public List<LineageNode> upstreamDatasets(LineageNode node) {
        return upstream(node).stream().filter(LineageNode::isDataset).collect(Collectors.toList());
    }

    /**
     * {@link #downstream(LineageNode)} 중 데이터셋만 가까운 것부터 돌려줍니다.
     *
     * @param node 기준 노드
     * @return 하위 데이터셋 목록
     */
    public List<LineageNode> downstreamDatasets(LineageNode node) {
        return downstream(node).stream().filter(LineageNode::isDataset).collect(Collectors.toList());
    }

    /**
     * 들어오는 간선이 없는 노드, 즉 이 그래프 안 데이터의 최초 원천을 돌려줍니다.
     *
     * @return 루트 노드 목록
     */
    public List<LineageNode> roots() {
        return nodes.values().stream().filter(n -> !incoming.containsKey(n.guid())).collect(Collectors.toList());
    }

    /**
     * 나가는 간선이 없는 노드, 즉 이 그래프 안 데이터의 최종 소비처를 돌려줍니다.
     *
     * @return 리프 노드 목록
     */
    public List<LineageNode> leaves() {
        return nodes.values().stream().filter(n -> !outgoing.containsKey(n.guid())).collect(Collectors.toList());
    }

    /**
     * {@code node}가 바뀌면 영향을 받는 하위 데이터셋을, 거리와 최단 경로 하나와 함께 돌려줍니다.
     * 거리가 가까운 순서로 정렬됩니다.
     *
     * @param node 기준 노드
     * @return 영향 목록
     */
    public List<Impact> impactOf(LineageNode node) {
        return impacts(node, true);
    }

    /**
     * {@code node}의 원천인 상위 데이터셋을, 거리와 최단 경로 하나와 함께 돌려줍니다.
     * 거리가 가까운 순서로 정렬되며, 경로는 기준 노드에서 시작해 원천 데이터셋에서 끝납니다.
     *
     * @param node 기준 노드
     * @return 원천 목록
     */
    public List<Impact> sourcesOf(LineageNode node) {
        return impacts(node, false);
    }

    /**
     * 데이터 흐름 방향으로 {@code from}에서 {@code to}까지 가는 모든 단순 경로(같은 노드를 두 번 지나지 않는
     * 경로)를 돌려줍니다. 최대 {@value #MAX_PATHS}개까지만 찾습니다.
     *
     * @param from 시작 노드
     * @param to   끝 노드
     * @return 경로 목록. 각 경로는 {@code from}으로 시작해 {@code to}로 끝납니다. 경로가 없으면 빈 목록
     */
    public List<List<LineageNode>> paths(LineageNode from, LineageNode to) {
        List<List<LineageNode>> result = new ArrayList<>();
        Deque<LineageNode> current = new ArrayDeque<>();
        Set<String> onPath = new LinkedHashSet<>();
        current.addLast(from);
        onPath.add(from.guid());
        collectPaths(from, to, current, onPath, result);
        return result;
    }

    /**
     * 깊이 우선 탐색으로 경로를 모읍니다. {@code onPath}에 현재 경로의 guid를 두어 순환을 피하고,
     * 되돌아갈 때 빼서 다른 경로에서는 같은 노드를 다시 지날 수 있게 합니다.
     */
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

    /** 시작 노드에서 한 방향으로 닿는 모든 노드를 BFS 순서(가까운 순)로 돌려줍니다. 시작 노드는 뺍니다. */
    private List<LineageNode> reachable(LineageNode start, boolean forward) {
        return bfs(start, forward).keySet().stream()
                .filter(n -> !n.equals(start))
                .collect(Collectors.toList());
    }

    /**
     * 너비 우선 탐색을 합니다. 도달한 각 노드를 최단 경로상의 직전 노드에 대응시킨 맵을 돌려줍니다.
     * 시작 노드의 직전 노드는 {@code null}입니다. 맵은 방문 순서를 유지하므로 키 순서가 곧 거리 순서입니다.
     *
     * @param forward {@code true}이면 간선 정방향(downstream), {@code false}이면 역방향(upstream)
     */
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

    /**
     * BFS 결과에서 데이터셋마다 직전 노드를 거슬러 올라가 경로를 복원하고, 경로에 있는 프로세스 수를 거리로
     * 삼아 {@link Impact}를 만듭니다.
     */
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
            // 데이터셋 사이에는 항상 프로세스가 있으므로 경로의 프로세스 수가 곧 홉 수입니다.
            int distance = (int) path.stream().filter(LineageNode::process).count();
            result.add(new Impact(node, distance, path));
        }
        // List.sort는 안정 정렬이므로 거리가 같으면 BFS 방문 순서가 유지됩니다.
        result.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        return result;
    }

    // ---------------------------------------------------------------- 출력

    /**
     * Mermaid flowchart 문법의 문자열을 만듭니다. 데이터셋은 사각형, 프로세스는 둥근 모양으로 그리고,
     * 기준 엔티티는 굵은 테두리, 삭제된 엔티티는 점선으로 표시합니다.
     *
     * @return Mermaid 다이어그램 소스
     */
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

    /**
     * Graphviz DOT 문법의 문자열을 만듭니다. 데이터셋은 사각형, 프로세스는 타원으로 그리고, 삭제된 엔티티는
     * 점선, 기준 엔티티는 굵게 표시합니다. 노드 ID로는 guid를 그대로 씁니다.
     *
     * @return DOT 소스
     */
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

    /** Mermaid 노드 ID로 쓸 짧은 ID({@code n0}, {@code n1}, ...)를 guid별로 만듭니다. */
    private Map<String, String> shortIds() {
        Map<String, String> ids = new HashMap<>();
        int i = 0;
        for (String guid : nodes.keySet()) {
            ids.put(guid, "n" + i++);
        }
        return ids;
    }

    /** Mermaid 레이블 안에서 문법 문자로 해석되는 따옴표와 꺾쇠를 Mermaid 엔티티 코드로 바꿉니다. */
    private static String escapeMermaid(String value) {
        return value.replace("\"", "#quot;").replace("<", "#lt;").replace(">", "#gt;");
    }

    /** DOT 문자열 리터럴 안에 넣을 수 있도록 역슬래시와 큰따옴표를 이스케이프합니다. */
    private static String escapeDot(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 기준 노드와 노드·간선 수를 담은 요약 문자열을 돌려줍니다. */
    @Override
    public String toString() {
        return "LineageGraph{base=" + base + ", nodes=" + nodes.size() + ", edges=" + edges.size() + "}";
    }
}
