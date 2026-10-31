package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.Map;

/**
 * 엔티티 하나와 그 엔티티가 참조하는 엔티티들(guid를 키로 하는 맵)을 함께 담습니다.
 * 단일 엔티티 API의 요청·응답 본문입니다.
 */
public class AtlasEntityWithExtInfo extends AtlasModel {

    private AtlasEntity entity;
    private Map<String, AtlasEntity> referredEntities;

    /** 빈 객체를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasEntityWithExtInfo() {
    }

    /**
     * 참조 엔티티 없이 엔티티 하나만 담아 만듭니다.
     *
     * @param entity 담을 엔티티
     */
    public AtlasEntityWithExtInfo(AtlasEntity entity) {
        this.entity = entity;
    }

    public AtlasEntity getEntity() {
        return entity;
    }

    public void setEntity(AtlasEntity entity) {
        this.entity = entity;
    }

    /** 주 엔티티가 참조하는 엔티티들을 guid를 키로 돌려줍니다. 없으면 {@code null}일 수 있습니다. */
    public Map<String, AtlasEntity> getReferredEntities() {
        return referredEntities;
    }

    public void setReferredEntities(Map<String, AtlasEntity> referredEntities) {
        this.referredEntities = referredEntities;
    }
}
