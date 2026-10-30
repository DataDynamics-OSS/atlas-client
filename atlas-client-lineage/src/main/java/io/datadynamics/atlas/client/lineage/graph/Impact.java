package io.datadynamics.atlas.client.lineage.graph;

import java.util.List;

/**
 * A dataset reached from the starting point of an impact (or source) analysis.
 *
 * @param dataset  the affected dataset
 * @param distance number of processes between the starting point and the dataset (1 = direct)
 * @param path     one shortest path, starting point first, dataset last, processes included
 */
public record Impact(LineageNode dataset, int distance, List<LineageNode> path) {

    public Impact {
        path = List.copyOf(path);
    }

    @Override
    public String toString() {
        return dataset + " (distance " + distance + ")";
    }
}
