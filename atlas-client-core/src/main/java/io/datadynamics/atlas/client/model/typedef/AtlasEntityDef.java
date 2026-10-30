package io.datadynamics.atlas.client.model.typedef;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entity type definition.
 * <p>
 * {@code subTypes}, {@code relationshipAttributeDefs} and {@code businessAttributeDefs} are
 * computed by Atlas and returned on reads; they are ignored by Atlas on create/update.
 */
public class AtlasEntityDef extends AtlasStructDef {

    private Set<String> superTypes;
    private Set<String> subTypes;
    private List<AtlasAttributeDef> relationshipAttributeDefs;
    private Map<String, List<AtlasAttributeDef>> businessAttributeDefs;

    public AtlasEntityDef() {
        super(TypeCategory.ENTITY);
    }

    public AtlasEntityDef(String name) {
        super(TypeCategory.ENTITY, name);
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

    public Set<String> getSubTypes() {
        return subTypes;
    }

    public void setSubTypes(Set<String> subTypes) {
        this.subTypes = subTypes;
    }

    public List<AtlasAttributeDef> getRelationshipAttributeDefs() {
        return relationshipAttributeDefs;
    }

    public void setRelationshipAttributeDefs(List<AtlasAttributeDef> relationshipAttributeDefs) {
        this.relationshipAttributeDefs = relationshipAttributeDefs;
    }

    public Map<String, List<AtlasAttributeDef>> getBusinessAttributeDefs() {
        return businessAttributeDefs;
    }

    public void setBusinessAttributeDefs(Map<String, List<AtlasAttributeDef>> businessAttributeDefs) {
        this.businessAttributeDefs = businessAttributeDefs;
    }
}
