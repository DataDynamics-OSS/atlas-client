package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reference to an entity, either by {@code guid} or by {@code typeName} + unique attributes.
 * <p>
 * Use {@link #byQualifiedName(String, String)} to reference an entity without knowing its guid,
 * which is how most lineage and relationship payloads are easiest to build.
 */
public class AtlasObjectId extends AtlasModel {

    public static final String QUALIFIED_NAME = "qualifiedName";

    private String guid;
    private String typeName;
    private Map<String, Object> uniqueAttributes;

    public AtlasObjectId() {
    }

    public AtlasObjectId(String guid, String typeName) {
        this.guid = guid;
        this.typeName = typeName;
    }

    public AtlasObjectId(String typeName, Map<String, Object> uniqueAttributes) {
        this.typeName = typeName;
        this.uniqueAttributes = uniqueAttributes == null ? null : new LinkedHashMap<>(uniqueAttributes);
    }

    public static AtlasObjectId byGuid(String guid) {
        return new AtlasObjectId(Objects.requireNonNull(guid, "guid"), (String) null);
    }

    public static AtlasObjectId byGuid(String guid, String typeName) {
        return new AtlasObjectId(Objects.requireNonNull(guid, "guid"), typeName);
    }

    public static AtlasObjectId byUniqueAttribute(String typeName, String attributeName, Object value) {
        Objects.requireNonNull(typeName, "typeName");
        Map<String, Object> unique = new LinkedHashMap<>();
        unique.put(attributeName, value);
        return new AtlasObjectId(typeName, unique);
    }

    public static AtlasObjectId byQualifiedName(String typeName, String qualifiedName) {
        return byUniqueAttribute(typeName, QUALIFIED_NAME, Objects.requireNonNull(qualifiedName, "qualifiedName"));
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Map<String, Object> getUniqueAttributes() {
        return uniqueAttributes;
    }

    public void setUniqueAttributes(Map<String, Object> uniqueAttributes) {
        this.uniqueAttributes = uniqueAttributes;
    }

    /** {@code true} if the guid was assigned by Atlas (placeholder guids used in batches are negative). */
    public boolean hasAssignedGuid() {
        return guid != null && !guid.startsWith("-");
    }

    /** The {@code qualifiedName} unique attribute, if this reference uses one. */
    public String qualifiedName() {
        Object value = uniqueAttributes == null ? null : uniqueAttributes.get(QUALIFIED_NAME);
        return value == null ? null : value.toString();
    }
}
