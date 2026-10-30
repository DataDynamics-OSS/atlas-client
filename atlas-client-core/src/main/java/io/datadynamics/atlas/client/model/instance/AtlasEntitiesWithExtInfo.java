package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Several entities plus the entities they reference, the payload of bulk calls.
 */
public class AtlasEntitiesWithExtInfo extends AtlasModel {

    private List<AtlasEntity> entities;
    private Map<String, AtlasEntity> referredEntities;

    public AtlasEntitiesWithExtInfo() {
    }

    public AtlasEntitiesWithExtInfo(Collection<AtlasEntity> entities) {
        this.entities = new ArrayList<>(entities);
    }

    public List<AtlasEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<AtlasEntity> entities) {
        this.entities = entities;
    }

    public void addEntity(AtlasEntity entity) {
        if (entities == null) {
            entities = new ArrayList<>();
        }
        entities.add(entity);
    }

    public Map<String, AtlasEntity> getReferredEntities() {
        return referredEntities;
    }

    public void setReferredEntities(Map<String, AtlasEntity> referredEntities) {
        this.referredEntities = referredEntities;
    }
}
