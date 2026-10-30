package io.datadynamics.atlas.client.lineage.graph;

/**
 * Directed edge in data-flow direction: an input dataset to a process, or a process to an output dataset.
 */
public record LineageEdge(LineageNode from, LineageNode to, String relationshipGuid) {

    @Override
    public String toString() {
        return from + " -> " + to;
    }
}
