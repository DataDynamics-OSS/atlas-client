package io.datadynamics.atlas.client.model.typedef;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Enum type definition.
 */
public class AtlasEnumDef extends AtlasBaseTypeDef {

    private List<AtlasEnumElementDef> elementDefs;
    private String defaultValue;

    public AtlasEnumDef() {
        super(TypeCategory.ENUM);
    }

    public AtlasEnumDef(String name) {
        super(TypeCategory.ENUM, name);
    }

    public List<AtlasEnumElementDef> getElementDefs() {
        return elementDefs;
    }

    public void setElementDefs(List<AtlasEnumElementDef> elementDefs) {
        this.elementDefs = elementDefs;
    }

    /** Appends an element whose ordinal is its position in the list. */
    public void addElement(String value) {
        if (elementDefs == null) {
            elementDefs = new ArrayList<>();
        }
        elementDefs.add(new AtlasEnumElementDef(value, null, elementDefs.size()));
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public static class AtlasEnumElementDef extends AtlasModel {

        private String value;
        private String description;
        private Integer ordinal;

        public AtlasEnumElementDef() {
        }

        public AtlasEnumElementDef(String value, String description, Integer ordinal) {
            this.value = value;
            this.description = description;
            this.ordinal = ordinal;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getOrdinal() {
            return ordinal;
        }

        public void setOrdinal(Integer ordinal) {
            this.ordinal = ordinal;
        }
    }
}
