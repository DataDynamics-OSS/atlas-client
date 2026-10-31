package io.datadynamics.atlas.client.lineage.graph;

/**
 * 데이터 흐름 방향의 방향 간선입니다. 입력 데이터셋에서 프로세스로, 또는 프로세스에서 출력 데이터셋으로 향합니다.
 *
 * @param from             간선의 시작 노드(데이터가 나오는 쪽)
 * @param to               간선의 끝 노드(데이터가 들어가는 쪽)
 * @param relationshipGuid 이 간선에 해당하는 Atlas relationship의 guid
 */
public record LineageEdge(LineageNode from, LineageNode to, String relationshipGuid) {

    /** {@code from -> to} 형식의 문자열을 돌려줍니다. */
    @Override
    public String toString() {
        return from + " -> " + to;
    }
}
