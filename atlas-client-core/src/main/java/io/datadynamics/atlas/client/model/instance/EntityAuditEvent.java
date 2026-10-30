package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.time.Instant;

/**
 * Entity audit event ({@code EntityAuditEventV2}).
 * <p>
 * {@code action} is kept as a string (e.g. {@code ENTITY_CREATE}, {@code CLASSIFICATION_ADD})
 * because the set of actions grows with every Atlas release.
 */
public class EntityAuditEvent extends AtlasModel {

    private String entityId;
    private Instant timestamp;
    private String user;
    private String action;
    private String details;
    private String eventKey;
    private AtlasEntity entity;
    private String type;

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    /** Opaque key used as {@code startKey} to fetch the next page of audit events. */
    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public AtlasEntity getEntity() {
        return entity;
    }

    public void setEntity(AtlasEntity entity) {
        this.entity = entity;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
