package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;

/**
 * {@code ENTITY_FULL_UPDATE_V2} 메시지입니다. {@code POST /v2/entity/bulk}처럼 엔티티를 주어진 정의로
 * 교체합니다.
 */
public class EntityFullUpdateRequest extends HookMessage {

    private AtlasEntitiesWithExtInfo entities;

    /** JSON 역직렬화용 빈 메시지를 만듭니다. */
    public EntityFullUpdateRequest() {
        super(HookMessageType.ENTITY_FULL_UPDATE_V2);
    }

    /**
     * 교체할 엔티티 정의로 메시지를 만듭니다.
     *
     * @param entities 새 엔티티 정의 묶음
     */
    public EntityFullUpdateRequest(AtlasEntitiesWithExtInfo entities) {
        this();
        this.entities = entities;
    }

    /** 교체할 엔티티 정의 묶음입니다. */
    public AtlasEntitiesWithExtInfo getEntities() {
        return entities;
    }

    /** 교체할 엔티티 정의 묶음을 정합니다. */
    public void setEntities(AtlasEntitiesWithExtInfo entities) {
        this.entities = entities;
    }

    /**
     * 엔티티가 하나 이상 있고 모두 typeName을 가졌는지 확인합니다.
     *
     * @throws IllegalArgumentException 엔티티가 없거나 typeName이 없는 엔티티가 있는 경우
     */
    @Override
    public void validate() {
        requireEntities(entities, getType());
    }

    /** 생성·전체 갱신 메시지가 함께 쓰는 검사입니다. Atlas는 typeName이 없는 엔티티를 처리할 수 없으므로 미리 거부합니다. */
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
