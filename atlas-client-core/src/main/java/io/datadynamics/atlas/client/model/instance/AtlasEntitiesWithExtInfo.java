package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 여러 엔티티와 그 엔티티들이 참조하는 엔티티들을 함께 담습니다. 일괄(bulk) API의 요청·응답 본문입니다.
 */
public class AtlasEntitiesWithExtInfo extends AtlasModel {

    private List<AtlasEntity> entities;
    private Map<String, AtlasEntity> referredEntities;

    /** 빈 객체를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasEntitiesWithExtInfo() {
    }

    /**
     * 엔티티 목록을 복사해 담습니다.
     *
     * @param entities 담을 엔티티들({@code null}이면 안 됩니다)
     */
    public AtlasEntitiesWithExtInfo(Collection<AtlasEntity> entities) {
        this.entities = new ArrayList<>(entities);
    }

    public List<AtlasEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<AtlasEntity> entities) {
        this.entities = entities;
    }

    /**
     * 엔티티를 목록 끝에 추가합니다. 목록이 없으면 새로 만듭니다.
     *
     * @param entity 추가할 엔티티
     */
    public void addEntity(AtlasEntity entity) {
        if (entities == null) {
            entities = new ArrayList<>();
        }
        entities.add(entity);
    }

    /** 주 엔티티들이 참조하는 엔티티들을 guid를 키로 돌려줍니다. 없으면 {@code null}일 수 있습니다. */
    public Map<String, AtlasEntity> getReferredEntities() {
        return referredEntities;
    }

    public void setReferredEntities(Map<String, AtlasEntity> referredEntities) {
        this.referredEntities = referredEntities;
    }
}
