package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.exception.AtlasClientException;

import java.util.List;

/**
 * Lineage could not be registered; nothing was sent to Atlas.
 */
public class LineageException extends AtlasClientException {

    private final List<EntityRef> missingEntities;

    public LineageException(String message, List<EntityRef> missingEntities) {
        super(message);
        this.missingEntities = List.copyOf(missingEntities);
    }

    /** Referenced entities that do not exist in Atlas. */
    public List<EntityRef> getMissingEntities() {
        return missingEntities;
    }
}
