package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.model.instance.AtlasObjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reference to a dataset, column or process by type and {@code qualifiedName}.
 * <p>
 * {@code attributes} are only used when the entity does not exist and
 * {@link MissingEntityPolicy#CREATE} creates it; supply the type's mandatory attributes there.
 */
public record EntityRef(String typeName, String qualifiedName, Map<String, Object> attributes) {

    public EntityRef {
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static EntityRef of(String typeName, String qualifiedName) {
        return new EntityRef(typeName, qualifiedName, Map.of());
    }

    /** Copy with an attribute used if this entity has to be created. */
    public EntityRef withAttribute(String name, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(attributes);
        copy.put(name, value);
        return new EntityRef(typeName, qualifiedName, copy);
    }

    public AtlasObjectId toObjectId() {
        return AtlasObjectId.byQualifiedName(typeName, qualifiedName);
    }

    /** Identity of the referenced entity, ignoring creation attributes. */
    public String key() {
        return typeName + "|" + qualifiedName;
    }

    /**
     * Default display name derived from the qualified name: the cluster suffix ({@code @prod})
     * and any {@code db.table.} prefix are removed, so {@code mart.daily_sales@prod} gives
     * {@code daily_sales}.
     */
    public String defaultName() {
        String name = qualifiedName;
        int at = name.lastIndexOf('@');
        if (at > 0) {
            name = name.substring(0, at);
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            name = name.substring(dot + 1);
        }
        return name;
    }

    @Override
    public String toString() {
        return typeName + ":" + qualifiedName;
    }
}
