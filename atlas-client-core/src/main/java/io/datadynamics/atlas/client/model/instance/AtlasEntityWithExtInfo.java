package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.Map;

/**
 * An entity plus the entities it references (keyed by guid), the payload of single-entity calls.
 */
public class AtlasEntityWithExtInfo extends AtlasModel {

    private AtlasEntity entity;
    private Map<String, AtlasEntity> referredEntities;

    public AtlasEntityWithExtInfo() {
    }

    public AtlasEntityWithExtInfo(AtlasEntity entity) {
        this.entity = entity;
    }

    public AtlasEntity getEntity() {
        return entity;
    }

    public void setEntity(AtlasEntity entity) {
        this.entity = entity;
    }

    public Map<String, AtlasEntity> getReferredEntities() {
        return referredEntities;
    }

    public void setReferredEntities(Map<String, AtlasEntity> referredEntities) {
        this.referredEntities = referredEntities;
    }
}
