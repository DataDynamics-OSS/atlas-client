package io.datadynamics.atlas.client.model.typedef;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fields shared by every Atlas type definition.
 */
public abstract class AtlasBaseTypeDef extends AtlasModel {

    private TypeCategory category;
    private String guid;
    private String createdBy;
    private String updatedBy;
    private Instant createTime;
    private Instant updateTime;
    private Long version;
    private String name;
    private String description;
    private String typeVersion;
    private String serviceType;
    private Map<String, String> options;

    protected AtlasBaseTypeDef(TypeCategory category) {
        this.category = category;
    }

    protected AtlasBaseTypeDef(TypeCategory category, String name) {
        this.category = category;
        this.name = name;
    }

    public TypeCategory getCategory() {
        return category;
    }

    public void setCategory(TypeCategory category) {
        this.category = category;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTypeVersion() {
        return typeVersion;
    }

    public void setTypeVersion(String typeVersion) {
        this.typeVersion = typeVersion;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    public void putOption(String key, String value) {
        if (options == null) {
            options = new LinkedHashMap<>();
        }
        options.put(key, value);
    }
}
