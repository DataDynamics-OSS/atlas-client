package io.datadynamics.atlas.client.model.typedef;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Classification (tag) type definition.
 */
public class AtlasClassificationDef extends AtlasStructDef {

    private Set<String> superTypes;
    private Set<String> entityTypes;
    private Set<String> subTypes;

    public AtlasClassificationDef() {
        super(TypeCategory.CLASSIFICATION);
    }

    public AtlasClassificationDef(String name) {
        super(TypeCategory.CLASSIFICATION, name);
    }

    public Set<String> getSuperTypes() {
        return superTypes;
    }

    public void setSuperTypes(Set<String> superTypes) {
        this.superTypes = superTypes;
    }

    public void addSuperType(String superType) {
        if (superTypes == null) {
            superTypes = new LinkedHashSet<>();
        }
        superTypes.add(superType);
    }

    /** Entity types this classification may be attached to; empty means any. */
    public Set<String> getEntityTypes() {
        return entityTypes;
    }

    public void setEntityTypes(Set<String> entityTypes) {
        this.entityTypes = entityTypes;
    }

    public void addEntityType(String entityType) {
        if (entityTypes == null) {
            entityTypes = new LinkedHashSet<>();
        }
        entityTypes.add(entityType);
    }

    public Set<String> getSubTypes() {
        return subTypes;
    }

    public void setSubTypes(Set<String> subTypes) {
        this.subTypes = subTypes;
    }
}
