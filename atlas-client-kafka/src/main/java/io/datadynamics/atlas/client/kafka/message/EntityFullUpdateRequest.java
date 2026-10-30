package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;

/**
 * {@code ENTITY_FULL_UPDATE_V2}: replaces entities with the given definitions, like
 * {@code POST /v2/entity/bulk}.
 */
public class EntityFullUpdateRequest extends HookMessage {

    private AtlasEntitiesWithExtInfo entities;

    public EntityFullUpdateRequest() {
        super(HookMessageType.ENTITY_FULL_UPDATE_V2);
    }

    public EntityFullUpdateRequest(AtlasEntitiesWithExtInfo entities) {
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
        requireEntities(entities, getType());
    }

    static void requireEntities(AtlasEntitiesWithExtInfo entities, HookMessageType type) {
        if (entities == null || entities.getEntities() == null || entities.getEntities().isEmpty()) {
            throw new IllegalArgumentException(type + " needs at least one entity");
        }
        for (AtlasEntity entity : entities.getEntities()) {
            if (entity.getTypeName() == null) {
                throw new IllegalArgumentException(type + ": entity without typeName: " + entity);
            }
        }
    }
}
