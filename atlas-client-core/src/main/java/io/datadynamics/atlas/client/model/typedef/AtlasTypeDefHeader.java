package io.datadynamics.atlas.client.model.typedef;

import io.datadynamics.atlas.client.model.AtlasModel;

/**
 * Lightweight type summary returned by {@code /v2/types/typedefs/headers}.
 */
public class AtlasTypeDefHeader extends AtlasModel {

    private String guid;
    private String name;
    private TypeCategory category;
    private String serviceType;

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TypeCategory getCategory() {
        return category;
    }

    public void setCategory(TypeCategory category) {
        this.category = category;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }
}
