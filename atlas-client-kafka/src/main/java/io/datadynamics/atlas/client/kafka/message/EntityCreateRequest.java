package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;

/**
 * {@code ENTITY_CREATE_V2}: creates entities, or updates the ones that exist with the same unique
 * attributes, like {@code POST /v2/entity/bulk}. Entities may reference each other through
 * placeholder guids.
 */
public class EntityCreateRequest extends HookMessage {

    private AtlasEntitiesWithExtInfo entities;

    public EntityCreateRequest() {
        super(HookMessageType.ENTITY_CREATE_V2);
    }

    public EntityCreateRequest(AtlasEntitiesWithExtInfo entities) {
        this();
        this.entities = entities;
    }

    public AtlasEntitiesWithExtInfo getEntities() {
        return entities;
    }

    public void setEntities(AtlasEntitiesWithExtInfo entities) {
        this.entities = entities;
    }

    @Override
    public void validate() {
        EntityFullUpdateRequest.requireEntities(entities, getType());
    }
}
