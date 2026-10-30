package io.datadynamics.atlas.client.kafka.message;

import com.fasterxml.jackson.annotation.JsonCreator;

/** What happened to the entity or relationship of an {@link EntityChangeEvent}. */
public enum OperationType {
    ENTITY_CREATE,
    ENTITY_UPDATE,
    ENTITY_DELETE,
    ENTITY_IMPORT_CREATE,
    ENTITY_IMPORT_UPDATE,
    ENTITY_IMPORT_DELETE,
    CLASSIFICATION_ADD,
    CLASSIFICATION_DELETE,
    CLASSIFICATION_UPDATE,
    RELATIONSHIP_CREATE,
    RELATIONSHIP_UPDATE,
    RELATIONSHIP_DELETE,
    /** An operation added by a newer Atlas version. */
    UNKNOWN;

    @JsonCreator
    public static OperationType from(String value) {
        for (OperationType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public boolean isRelationshipOperation() {
        return name().startsWith("RELATIONSHIP_");
    }
}
