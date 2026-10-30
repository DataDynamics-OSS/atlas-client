package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;

/**
 * Builder of entity types. Created with {@link TypeDefs#entity(String)}.
 * <p>
 * Most entity types should extend {@code DataSet} (tables, files, topics) or {@code Process}
 * (jobs, queries), which brings {@code qualifiedName}, {@code name}, {@code owner} and lineage support.
 */
public final class EntityDefBuilder extends StructLikeDefBuilder<EntityDefBuilder, AtlasEntityDef> {

    EntityDefBuilder(String name) {
        super(new AtlasEntityDef(), name);
    }

    public EntityDefBuilder superTypes(String... superTypes) {
        for (String superType : superTypes) {
            def.addSuperType(superType);
        }
        return this;
    }

    /** Shortcut for {@code superTypes("DataSet")}. */
    public EntityDefBuilder dataSet() {
        return superTypes(TypeDefs.DATA_SET);
    }

    /** Shortcut for {@code superTypes("Process")}. */
    public EntityDefBuilder process() {
        return superTypes(TypeDefs.PROCESS);
    }
}
