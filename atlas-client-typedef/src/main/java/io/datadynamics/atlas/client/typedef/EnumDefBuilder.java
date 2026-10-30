package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasEnumDef;

import java.util.ArrayList;

/**
 * Builder of enum types. Created with {@link TypeDefs#enumType(String)}.
 * Ordinals follow declaration order.
 */
public final class EnumDefBuilder extends TypeDefBuilder<EnumDefBuilder, AtlasEnumDef> {

    EnumDefBuilder(String name) {
        super(new AtlasEnumDef(), name);
        def.setElementDefs(new ArrayList<>());
    }

    public EnumDefBuilder values(String... values) {
        for (String value : values) {
            def.addElement(value);
        }
        return this;
    }

    public EnumDefBuilder value(String value, String description) {
        def.addElement(value);
        def.getElementDefs().get(def.getElementDefs().size() - 1).setDescription(description);
        return this;
    }

    public EnumDefBuilder defaultValue(String defaultValue) {
        def.setDefaultValue(defaultValue);
        return this;
    }
}
