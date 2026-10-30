package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasStructDef;

/**
 * Builder of definitions that carry attributes (struct, entity, classification, relationship,
 * business metadata).
 */
public abstract class StructLikeDefBuilder<B extends StructLikeDefBuilder<B, D>, D extends AtlasStructDef>
        extends TypeDefBuilder<B, D> {

    protected StructLikeDefBuilder(D def, String name) {
        super(def, name);
    }

    public B attribute(AttributeDefBuilder attribute) {
        def.addAttributeDef(attribute.build());
        return self();
    }

    /** Adds an optional attribute of the given type. */
    public B attribute(String name, String typeName) {
        return attribute(TypeDefs.attr(name, typeName));
    }

    public B attributes(AttributeDefBuilder... attributes) {
        for (AttributeDefBuilder attribute : attributes) {
            attribute(attribute);
        }
        return self();
    }
}
