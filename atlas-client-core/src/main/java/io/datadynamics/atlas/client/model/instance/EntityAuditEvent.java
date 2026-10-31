package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.time.Instant;

/**
 * 엔티티 감사(audit) 이벤트입니다. Atlas의 {@code EntityAuditEventV2}에 해당합니다.
 * <p>
 * {@code action}(예: {@code ENTITY_CREATE}, {@code CLASSIFICATION_ADD})은 enum이 아닌 문자열로 둡니다.
 * Atlas가 새 버전을 낼 때마다 action 종류가 늘어나기 때문입니다.
 */
public class EntityAuditEvent extends AtlasModel {

    private String entityId;
    private Instant timestamp;
    private String user;
    private String action;
    private String details;
    private String eventKey;
    private AtlasEntity entity;
    private String type;

    /** 이벤트가 일어난 엔티티의 guid를 돌려줍니다. */
    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /** 변경을 수행한 사용자를 돌려줍니다. */
    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    /** 이벤트 종류(예: {@code ENTITY_CREATE})를 돌려줍니다. */
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    /** Atlas가 남긴 이벤트 상세 내용(문자열)을 돌려줍니다. */
    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    /** 다음 페이지의 감사 이벤트를 가져올 때 {@code startKey}로 넘기는 키입니다. 내용은 해석하지 않고 그대로 씁니다. */
    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    /** 이벤트 시점의 엔티티 내용을 돌려줍니다. Atlas가 담아 주지 않으면 {@code null}입니다. */
    public AtlasEntity getEntity() {
        return entity;
    }

    public void setEntity(AtlasEntity entity) {
        this.entity = entity;
    }

    /** Atlas가 보내는 이벤트 형식 구분 값을 문자열 그대로 돌려줍니다. */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
