package io.datadynamics.atlas.client.lineage.graph;

import java.util.List;

/**
 * 영향(또는 원천) 분석의 시작점에서 도달한 데이터셋 하나입니다.
 *
 * @param dataset  영향을 받는(또는 원천인) 데이터셋
 * @param distance 시작점과 데이터셋 사이에 있는 프로세스 수(1 = 직접 연결)
 * @param path     최단 경로 하나. 시작점이 처음, 데이터셋이 마지막이며 중간의 프로세스도 포함합니다.
 */
public record Impact(LineageNode dataset, int distance, List<LineageNode> path) {

    /** 경로를 변경 불가능한 복사본으로 바꿉니다. */
    public Impact {
        path = List.copyOf(path);
    }

    /** {@code 데이터셋 (distance N)} 형식의 문자열을 돌려줍니다. */
    @Override
    public String toString() {
        return dataset + " (distance " + distance + ")";
    }
}
