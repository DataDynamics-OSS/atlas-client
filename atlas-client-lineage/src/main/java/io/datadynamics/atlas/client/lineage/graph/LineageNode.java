package io.datadynamics.atlas.client.lineage.graph;

import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.EntityStatus;

/**
 * A dataset or process in a lineage graph. Identity is the guid.
 *
 * @param header the entity summary returned by Atlas
 */
public record LineageNode(String guid, String typeName, String qualifiedName, String displayText,
                          EntityStatus status, boolean process, AtlasEntityHeader header) {

    static LineageNode of(AtlasEntityHeader header, boolean process) {
        return new LineageNode(header.getGuid(), header.getTypeName(), header.qualifiedName(),
                header.getDisplayText(), header.getStatus(), process, header);
    }

    public boolean isDataset() {
        return !process;
    }

    public boolean isDeleted() {
        return status == EntityStatus.DELETED;
    }

    /** Best human-readable label: display text, else qualified name, else guid. */
    public String label() {
        if (displayText != null && !displayText.isBlank()) {
            return displayText;
        }
        return qualifiedName != null ? qualifiedName : guid;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LineageNode && guid.equals(((LineageNode) o).guid);
    }

    @Override
    public int hashCode() {
        return guid.hashCode();
    }

    @Override
    public String toString() {
        return typeName + ":" + (qualifiedName != null ? qualifiedName : guid);
    }
}
