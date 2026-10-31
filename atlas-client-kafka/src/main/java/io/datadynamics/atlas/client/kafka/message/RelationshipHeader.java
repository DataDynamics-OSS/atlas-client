package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.model.instance.EntityStatus;

import java.util.Map;

/** {@code RELATIONSHIP_*} 작업의 {@link EntityChangeEvent}에 담긴 relationship 정보입니다. */
public class RelationshipHeader extends AtlasModel {

    private String typeName;
    private String guid;
    private EntityStatus status;
    private String label;
    private String propagateTags;
    private AtlasObjectId end1;
    private AtlasObjectId end2;
    private Map<String, Object> attributes;

    /** relationship 타입 이름입니다. */
    public String getTypeName() {
        return typeName;
    }

    /** relationship 타입 이름을 정합니다. */
    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    /** relationship의 guid입니다. */
    public String getGuid() {
        return guid;
    }

    /** relationship의 guid를 정합니다. */
    public void setGuid(String guid) {
        this.guid = guid;
    }

    /** relationship 상태(ACTIVE, DELETED 등)입니다. */
    public EntityStatus getStatus() {
        return status;
    }

    /** relationship 상태를 정합니다. */
    public void setStatus(EntityStatus status) {
        this.status = status;
    }

    /** Atlas가 relationship에 붙이는 레이블입니다. */
    public String getLabel() {
        return label;
    }

    /** relationship 레이블을 정합니다. */
    public void setLabel(String label) {
        this.label = label;
    }

    /** classification 전파 방향(Atlas가 보낸 문자열 그대로)입니다. */
    public String getPropagateTags() {
        return propagateTags;
    }

    /** classification 전파 방향을 정합니다. */
    public void setPropagateTags(String propagateTags) {
        this.propagateTags = propagateTags;
    }

    /** relationship의 첫 번째 끝 엔티티입니다. */
    public AtlasObjectId getEnd1() {
        return end1;
    }

    /** relationship의 첫 번째 끝 엔티티를 정합니다. */
    public void setEnd1(AtlasObjectId end1) {
        this.end1 = end1;
    }

    /** relationship의 두 번째 끝 엔티티입니다. */
    public AtlasObjectId getEnd2() {
        return end2;
    }

    /** relationship의 두 번째 끝 엔티티를 정합니다. */
    public void setEnd2(AtlasObjectId end2) {
        this.end2 = end2;
    }

    /** relationship 속성입니다. */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /** relationship 속성을 정합니다. */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
