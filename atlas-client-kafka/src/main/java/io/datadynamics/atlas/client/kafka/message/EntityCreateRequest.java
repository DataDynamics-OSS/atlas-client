package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;

/**
 * {@code ENTITY_CREATE_V2} 메시지입니다. {@code POST /v2/entity/bulk}처럼 엔티티를 만들고, 같은
 * uniqueAttributes를 가진 엔티티가 이미 있으면 갱신합니다. 엔티티끼리는 임시(placeholder) guid로 서로
 * 참조할 수 있습니다.
 */
public class EntityCreateRequest extends HookMessage {

    private AtlasEntitiesWithExtInfo entities;

    /** JSON 역직렬화용 빈 메시지를 만듭니다. */
    public EntityCreateRequest() {
        super(HookMessageType.ENTITY_CREATE_V2);
    }

    /**
     * 만들거나 갱신할 엔티티로 메시지를 만듭니다.
     *
     * @param entities 엔티티 묶음(참조 엔티티 포함 가능)
     */
    public EntityCreateRequest(AtlasEntitiesWithExtInfo entities) {
        this();
        this.entities = entities;
    }

    /** 만들거나 갱신할 엔티티 묶음입니다. */
    public AtlasEntitiesWithExtInfo getEntities() {
        return entities;
    }

    /** 만들거나 갱신할 엔티티 묶음을 정합니다. */
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
        EntityFullUpdateRequest.requireEntities(entities, getType());
    }
}
