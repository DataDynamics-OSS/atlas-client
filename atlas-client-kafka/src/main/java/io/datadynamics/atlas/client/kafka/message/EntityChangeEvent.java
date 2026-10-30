package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;

import java.time.Instant;

/**
 * A change notification Atlas publishes to the {@code ATLAS_ENTITIES} topic
 * ({@code ENTITY_NOTIFICATION_V2}). Entity operations carry {@link #getEntity()}, relationship
 * operations carry {@link #getRelationship()}.
 */
public class EntityChangeEvent extends AtlasModel {

    public static final String TYPE_V2 = "ENTITY_NOTIFICATION_V2";

    private String type;
    private AtlasEntityHeader entity;
    private RelationshipHeader relationship;
    private OperationType operationType;
    private Long eventTime;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public AtlasEntityHeader getEntity() {
        return entity;
    }

    public void setEntity(AtlasEntityHeader entity) {
        this.entity = entity;
    }

    public RelationshipHeader getRelationship() {
        return relationship;
    }

    public void setRelationship(RelationshipHeader relationship) {
        this.relationship = relationship;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    /** Epoch millis of the change. */
    public Long getEventTime() {
        return eventTime;
    }

    public void setEventTime(Long eventTime) {
        this.eventTime = eventTime;
    }

    // Convenience accessors use non-bean names so they are not serialized.

    /** Type name of the entity, or of the relationship for relationship operations. */
    public String typeName() {
        if (entity != null) {
            return entity.getTypeName();
        }
        return relationship == null ? null : relationship.getTypeName();
    }

    public String guid() {
        if (entity != null) {
            return entity.getGuid();
        }
        return relationship == null ? null : relationship.getGuid();
    }

    /** Qualified name of the entity, if it has one. */
    public String qualifiedName() {
        return entity == null ? null : entity.qualifiedName();
    }

    public Instant time() {
        return eventTime == null ? null : Instant.ofEpochMilli(eventTime);
    }
}
