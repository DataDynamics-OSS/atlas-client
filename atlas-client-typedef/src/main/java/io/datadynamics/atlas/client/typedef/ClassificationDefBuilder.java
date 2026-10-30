package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasClassificationDef;

/**
 * Builder of classification (tag) types. Created with {@link TypeDefs#classification(String)}.
 */
public final class ClassificationDefBuilder
        extends StructLikeDefBuilder<ClassificationDefBuilder, AtlasClassificationDef> {

    ClassificationDefBuilder(String name) {
        super(new AtlasClassificationDef(), name);
    }

    public ClassificationDefBuilder superTypes(String... superTypes) {
        for (String superType : superTypes) {
            def.addSuperType(superType);
        }
        return this;
    }

    /** Restricts the classification to these entity types (and their sub-types). Default: any entity. */
    public ClassificationDefBuilder entityTypes(String... entityTypes) {
        for (String entityType : entityTypes) {
            def.addEntityType(entityType);
        }
        return this;
    }
}
