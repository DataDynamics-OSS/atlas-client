package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;

import java.time.Instant;

/**
 * Atlas가 {@code ATLAS_ENTITIES} 토픽에 발행하는 변경 알림({@code ENTITY_NOTIFICATION_V2})입니다.
 * 엔티티 작업에는 {@link #getEntity()}가, relationship 작업에는 {@link #getRelationship()}이 들어 있습니다.
 */
public class EntityChangeEvent extends AtlasModel {

    /** 이 클래스가 나타내는 알림의 {@code type} 값입니다. */
    public static final String TYPE_V2 = "ENTITY_NOTIFICATION_V2";

    private String type;
    private AtlasEntityHeader entity;
    private RelationshipHeader relationship;
    private OperationType operationType;
    private Long eventTime;

    /** 알림 종류({@code type})입니다. 보통 {@value #TYPE_V2}입니다. */
    public String getType() {
        return type;
    }

    /** 알림 종류를 정합니다. */
    public void setType(String type) {
        this.type = type;
    }

    /** 변경된 엔티티입니다. relationship 작업이면 {@code null}일 수 있습니다. */
    public AtlasEntityHeader getEntity() {
        return entity;
    }

    /** 변경된 엔티티를 정합니다. */
    public void setEntity(AtlasEntityHeader entity) {
        this.entity = entity;
    }

    /** 변경된 relationship입니다. 엔티티 작업이면 {@code null}일 수 있습니다. */
    public RelationshipHeader getRelationship() {
        return relationship;
    }

    /** 변경된 relationship을 정합니다. */
    public void setRelationship(RelationshipHeader relationship) {
        this.relationship = relationship;
    }

    /** 무슨 변경이 일어났는지 나타냅니다. */
    public OperationType getOperationType() {
        return operationType;
    }

    /** 변경 종류를 정합니다. */
    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    /** 변경이 일어난 시각(epoch 밀리초)입니다. */
    public Long getEventTime() {
        return eventTime;
    }

    /** 변경 시각(epoch 밀리초)을 정합니다. */
    public void setEventTime(Long eventTime) {
        this.eventTime = eventTime;
    }

    // 아래 편의 메서드는 JSON으로 직렬화되지 않도록 일부러 getXxx 같은 bean 이름을 쓰지 않습니다.

    /**
     * 엔티티의 타입 이름을, relationship 작업이면 relationship의 타입 이름을 돌려줍니다.
     *
     * @return 타입 이름. 둘 다 없으면 {@code null}
     */
    public String typeName() {
        if (entity != null) {
            return entity.getTypeName();
        }
        return relationship == null ? null : relationship.getTypeName();
    }

    /**
     * 엔티티의 guid를, relationship 작업이면 relationship의 guid를 돌려줍니다.
     *
     * @return guid. 둘 다 없으면 {@code null}
     */
    public String guid() {
        if (entity != null) {
            return entity.getGuid();
        }
        return relationship == null ? null : relationship.getGuid();
    }

    /**
     * 엔티티의 qualifiedName을 돌려줍니다.
     *
     * @return qualifiedName. 엔티티가 없거나 qualifiedName이 없으면 {@code null}
     */
    public String qualifiedName() {
        return entity == null ? null : entity.qualifiedName();
    }

    /**
     * 변경 시각을 {@link Instant}로 돌려줍니다.
     *
     * @return 변경 시각. 알림에 없으면 {@code null}
     */
    public Instant time() {
        return eventTime == null ? null : Instant.ofEpochMilli(eventTime);
    }
}
