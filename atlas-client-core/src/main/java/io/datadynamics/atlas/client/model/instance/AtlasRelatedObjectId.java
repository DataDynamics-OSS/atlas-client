package io.datadynamics.atlas.client.model.instance;

/**
 * {@code relationshipAttributes} 안에 담겨 오는 엔티티 참조입니다.
 * <p>
 * 참조된 엔티티 정보와 함께, 그 엔티티로 이어지는 relationship 인스턴스의 정보(타입, guid, 상태, 속성)를
 * 담습니다. 주로 Atlas 응답에서 읽기만 합니다.
 */
public class AtlasRelatedObjectId extends AtlasObjectId {

    private EntityStatus entityStatus;
    private String displayText;
    private String relationshipType;
    private String relationshipGuid;
    private EntityStatus relationshipStatus;
    private AtlasStruct relationshipAttributes;

    /** 참조된 엔티티의 상태를 돌려줍니다. */
    public EntityStatus getEntityStatus() {
        return entityStatus;
    }

    public void setEntityStatus(EntityStatus entityStatus) {
        this.entityStatus = entityStatus;
    }

    /** 참조된 엔티티를 화면에 표시할 때 쓰는 이름을 돌려줍니다. */
    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    /** 두 엔티티를 잇는 relationship의 타입 이름을 돌려줍니다. */
    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    /** 두 엔티티를 잇는 relationship 인스턴스의 guid를 돌려줍니다. */
    public String getRelationshipGuid() {
        return relationshipGuid;
    }

    public void setRelationshipGuid(String relationshipGuid) {
        this.relationshipGuid = relationshipGuid;
    }

    /** relationship 인스턴스의 상태를 돌려줍니다. 엔티티 상태와는 별개입니다. */
    public EntityStatus getRelationshipStatus() {
        return relationshipStatus;
    }

    public void setRelationshipStatus(EntityStatus relationshipStatus) {
        this.relationshipStatus = relationshipStatus;
    }

    /** relationship 인스턴스 자체가 가진 속성 값들을 돌려줍니다. */
    public AtlasStruct getRelationshipAttributes() {
        return relationshipAttributes;
    }

    public void setRelationshipAttributes(AtlasStruct relationshipAttributes) {
        this.relationshipAttributes = relationshipAttributes;
    }
}
