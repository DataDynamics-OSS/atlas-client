package io.datadynamics.atlas.client.kafka.message;

import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;
import io.datadynamics.atlas.client.model.instance.EntityStatus;

import java.util.Map;

/** The relationship of a {@code RELATIONSHIP_*} {@link EntityChangeEvent}. */
public class RelationshipHeader extends AtlasModel {

    private String typeName;
    private String guid;
    private EntityStatus status;
    private String label;
    private String propagateTags;
    private AtlasObjectId end1;
    private AtlasObjectId end2;
    private Map<String, Object> attributes;

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public EntityStatus getStatus() {
        return status;
    }

    public void setStatus(EntityStatus status) {
        this.status = status;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getPropagateTags() {
        return propagateTags;
    }

    public void setPropagateTags(String propagateTags) {
        this.propagateTags = propagateTags;
    }

    public AtlasObjectId getEnd1() {
        return end1;
    }

    public void setEnd1(AtlasObjectId end1) {
        this.end1 = end1;
    }

    public AtlasObjectId getEnd2() {
        return end2;
    }

    public void setEnd2(AtlasObjectId end2) {
        this.end2 = end2;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
