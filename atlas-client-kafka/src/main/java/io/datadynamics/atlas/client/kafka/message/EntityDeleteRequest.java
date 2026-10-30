package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.instance.AtlasObjectId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** {@code ENTITY_DELETE_V2}: deletes entities identified by type name and unique attributes. */
public class EntityDeleteRequest extends HookMessage {

    private List<AtlasObjectId> entities;

    public EntityDeleteRequest() {
        super(HookMessageType.ENTITY_DELETE_V2);
    }

    public EntityDeleteRequest(Collection<AtlasObjectId> entities) {
        this();
        this.entities = new ArrayList<>(entities);
    }

    public List<AtlasObjectId> getEntities() {
        return entities;
    }

    public void setEntities(List<AtlasObjectId> entities) {
        this.entities = entities;
    }

    @Override
    public void validate() {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException(getType() + " needs at least one entity");
        }
        entities.forEach(id -> requireUniqueAttributes(id, getType() + " entity"));
    }
}
