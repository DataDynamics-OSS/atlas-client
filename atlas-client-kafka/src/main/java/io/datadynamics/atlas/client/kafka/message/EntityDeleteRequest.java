package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.instance.AtlasObjectId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** {@code ENTITY_DELETE_V2} 메시지입니다. typeName과 uniqueAttributes로 지정한 엔티티를 삭제합니다. */
public class EntityDeleteRequest extends HookMessage {

    private List<AtlasObjectId> entities;

    /** JSON 역직렬화용 빈 메시지를 만듭니다. */
    public EntityDeleteRequest() {
        super(HookMessageType.ENTITY_DELETE_V2);
    }

    /**
     * 삭제할 엔티티로 메시지를 만듭니다. 컬렉션은 복사해 보관합니다.
     *
     * @param entities 삭제할 엔티티(typeName과 uniqueAttributes로 지정)
     */
    public EntityDeleteRequest(Collection<AtlasObjectId> entities) {
        this();
        this.entities = new ArrayList<>(entities);
    }

    /** 삭제할 엔티티 목록입니다. */
    public List<AtlasObjectId> getEntities() {
        return entities;
    }

    /** 삭제할 엔티티 목록을 정합니다. */
    public void setEntities(List<AtlasObjectId> entities) {
        this.entities = entities;
    }

    /**
     * 엔티티가 하나 이상 있고 각각 typeName과 uniqueAttributes를 가졌는지 확인합니다.
     *
     * @throws IllegalArgumentException 엔티티가 없거나 uniqueAttributes로 찾을 수 없는 엔티티가 있는 경우
     */
    @Override
    public void validate() {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException(getType() + " needs at least one entity");
        }
        entities.forEach(id -> requireUniqueAttributes(id, getType() + " entity"));
    }
}
