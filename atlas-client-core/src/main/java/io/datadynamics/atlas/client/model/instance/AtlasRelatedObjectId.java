package io.datadynamics.atlas.client.model.instance;

/**
 * Entity reference as returned inside {@code relationshipAttributes}: the referenced entity
 * plus details of the relationship instance that links to it.
 */
public class AtlasRelatedObjectId extends AtlasObjectId {

    private EntityStatus entityStatus;
    private String displayText;
    private String relationshipType;
    private String relationshipGuid;
    private EntityStatus relationshipStatus;
    private AtlasStruct relationshipAttributes;

    public EntityStatus getEntityStatus() {
        return entityStatus;
    }

    public void setEntityStatus(EntityStatus entityStatus) {
        this.entityStatus = entityStatus;
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getRelationshipGuid() {
        return relationshipGuid;
    }

    public void setRelationshipGuid(String relationshipGuid) {
        this.relationshipGuid = relationshipGuid;
    }

    public EntityStatus getRelationshipStatus() {
        return relationshipStatus;
    }

    public void setRelationshipStatus(EntityStatus relationshipStatus) {
        this.relationshipStatus = relationshipStatus;
    }

    public AtlasStruct getRelationshipAttributes() {
        return relationshipAttributes;
    }

    public void setRelationshipAttributes(AtlasStruct relationshipAttributes) {
        this.relationshipAttributes = relationshipAttributes;
    }
}
