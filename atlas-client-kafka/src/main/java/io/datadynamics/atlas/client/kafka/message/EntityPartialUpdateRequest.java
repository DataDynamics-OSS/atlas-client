package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;

/**
 * {@code ENTITY_PARTIAL_UPDATE_V2} 메시지입니다. {@code entityId}(typeName과 uniqueAttributes)로 지정한
 * 엔티티에서 {@code entity}에 들어 있는 속성만 갱신합니다.
 */
public class EntityPartialUpdateRequest extends HookMessage {

    private AtlasObjectId entityId;
    private AtlasEntityWithExtInfo entity;

    /** JSON 역직렬화용 빈 메시지를 만듭니다. */
    public EntityPartialUpdateRequest() {
        super(HookMessageType.ENTITY_PARTIAL_UPDATE_V2);
    }

    /**
     * 갱신 대상과 갱신할 속성으로 메시지를 만듭니다.
     *
     * @param entityId 갱신할 엔티티(typeName과 uniqueAttributes로 지정)
     * @param entity   갱신할 속성만 담은 엔티티
     */
    public EntityPartialUpdateRequest(AtlasObjectId entityId, AtlasEntity entity) {
        this();
        this.entityId = entityId;
        this.entity = new AtlasEntityWithExtInfo(entity);
    }

    /** 갱신할 엔티티를 가리키는 ID입니다. */
    public AtlasObjectId getEntityId() {
        return entityId;
    }

    /** 갱신할 엔티티를 가리키는 ID를 정합니다. */
    public void setEntityId(AtlasObjectId entityId) {
        this.entityId = entityId;
    }

    /** 갱신할 속성을 담은 엔티티입니다. */
    public AtlasEntityWithExtInfo getEntity() {
        return entity;
    }

    /** 갱신할 속성을 담은 엔티티를 정합니다. */
    public void setEntity(AtlasEntityWithExtInfo entity) {
        this.entity = entity;
    }

    /**
     * 대상 ID에 typeName과 uniqueAttributes가 있고, 갱신할 엔티티가 있는지 확인합니다.
     *
     * @throws IllegalArgumentException 대상을 uniqueAttributes로 찾을 수 없거나 갱신할 엔티티가 없는 경우
     */
    @Override
    public void validate() {
        requireUniqueAttributes(entityId, getType() + " entityId");
        if (entity == null || entity.getEntity() == null) {
            throw new IllegalArgumentException(getType() + " needs the entity with the attributes to update");
        }
    }
}
