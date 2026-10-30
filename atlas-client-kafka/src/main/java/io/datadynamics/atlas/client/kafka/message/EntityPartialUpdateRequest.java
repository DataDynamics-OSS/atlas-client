package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;

/**
 * {@code ENTITY_PARTIAL_UPDATE_V2}: updates only the attributes present in {@code entity} of the
 * entity identified by {@code entityId} (type name and unique attributes).
 */
public class EntityPartialUpdateRequest extends HookMessage {

    private AtlasObjectId entityId;
    private AtlasEntityWithExtInfo entity;

    public EntityPartialUpdateRequest() {
        super(HookMessageType.ENTITY_PARTIAL_UPDATE_V2);
    }

    public EntityPartialUpdateRequest(AtlasObjectId entityId, AtlasEntity entity) {
        this();
        this.entityId = entityId;
        this.entity = new AtlasEntityWithExtInfo(entity);
    }

    public AtlasObjectId getEntityId() {
        return entityId;
    }

    public void setEntityId(AtlasObjectId entityId) {
        this.entityId = entityId;
    }

    public AtlasEntityWithExtInfo getEntity() {
        return entity;
    }

    public void setEntity(AtlasEntityWithExtInfo entity) {
        this.entity = entity;
    }

    @Override
    public void validate() {
        requireUniqueAttributes(entityId, getType() + " entityId");
        if (entity == null || entity.getEntity() == null) {
            throw new IllegalArgumentException(getType() + " needs the entity with the attributes to update");
        }
    }
}
