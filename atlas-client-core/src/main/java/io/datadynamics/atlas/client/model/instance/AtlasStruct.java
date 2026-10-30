package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Instance of a struct type: a type name plus attribute values.
 */
public class AtlasStruct extends AtlasModel {

    private String typeName;
    private Map<String, Object> attributes;

    public AtlasStruct() {
    }

    public AtlasStruct(String typeName) {
        this.typeName = typeName;
    }

    public AtlasStruct(String typeName, Map<String, Object> attributes) {
        this.typeName = typeName;
        this.attributes = attributes == null ? null : new LinkedHashMap<>(attributes);
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Object getAttribute(String name) {
        return attributes == null ? null : attributes.get(name);
    }

    public boolean hasAttribute(String name) {
        return attributes != null && attributes.containsKey(name);
    }

    public void setAttribute(String name, Object value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(name, value);
    }

    public void removeAttribute(String name) {
        if (attributes != null) {
            attributes.remove(name);
        }
    }
}
