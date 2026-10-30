package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasStructDef;

/**
 * Builder of struct types (value objects embedded in entities). Created with {@link TypeDefs#struct(String)}.
 */
public final class StructDefBuilder extends StructLikeDefBuilder<StructDefBuilder, AtlasStructDef> {

    StructDefBuilder(String name) {
        super(new AtlasStructDef(), name);
    }
}
