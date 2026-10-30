package io.datadynamics.atlas.client.model.typedef;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.Map;

/**
 * Attribute constraint such as {@code ownedRef} or {@code inverseRef} (legacy relationship modelling).
 */
public class AtlasConstraintDef extends AtlasModel {

    public static final String CONSTRAINT_TYPE_OWNED_REF = "ownedRef";
    public static final String CONSTRAINT_TYPE_INVERSE_REF = "inverseRef";
    public static final String CONSTRAINT_PARAM_ATTRIBUTE = "attribute";

    private String type;
    private Map<String, Object> params;

    public AtlasConstraintDef() {
    }

    public AtlasConstraintDef(String type, Map<String, Object> params) {
        this.type = type;
        this.params = params;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
