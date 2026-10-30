package io.datadynamics.atlas.client.model.typedef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Struct type definition; also the base of entity, classification and business metadata definitions.
 */
public class AtlasStructDef extends AtlasBaseTypeDef {

    private List<AtlasAttributeDef> attributeDefs;

    public AtlasStructDef() {
        super(TypeCategory.STRUCT);
    }

    public AtlasStructDef(String name) {
        super(TypeCategory.STRUCT, name);
    }

    protected AtlasStructDef(TypeCategory category) {
        super(category);
    }

    protected AtlasStructDef(TypeCategory category, String name) {
        super(category, name);
    }

    public List<AtlasAttributeDef> getAttributeDefs() {
        return attributeDefs;
    }

    public void setAttributeDefs(List<AtlasAttributeDef> attributeDefs) {
        this.attributeDefs = attributeDefs;
    }

    public void addAttributeDef(AtlasAttributeDef attributeDef) {
        if (attributeDefs == null) {
            attributeDefs = new ArrayList<>();
        }
        attributeDefs.add(attributeDef);
    }

    public Optional<AtlasAttributeDef> findAttributeDef(String attributeName) {
        if (attributeDefs == null) {
            return Optional.empty();
        }
        return attributeDefs.stream().filter(a -> attributeName.equals(a.getName())).findFirst();
    }
}
