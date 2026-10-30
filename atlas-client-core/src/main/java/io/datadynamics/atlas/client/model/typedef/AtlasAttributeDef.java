package io.datadynamics.atlas.client.model.typedef;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Definition of one attribute of a struct, entity, classification or business metadata type.
 */
public class AtlasAttributeDef extends AtlasModel {

    /** Index type hint used by Atlas for string attributes. */
    public enum IndexType {
        DEFAULT,
        STRING
    }

    private String name;
    private String typeName;
    @JsonProperty("isOptional")
    private Boolean optional;
    private Cardinality cardinality;
    private Integer valuesMinCount;
    private Integer valuesMaxCount;
    @JsonProperty("isUnique")
    private Boolean unique;
    @JsonProperty("isIndexable")
    private Boolean indexable;
    private Boolean includeInNotification;
    private String defaultValue;
    private String description;
    private Integer searchWeight;
    private IndexType indexType;
    private String displayName;
    private List<AtlasConstraintDef> constraints;
    private Map<String, String> options;

    public AtlasAttributeDef() {
    }

    public AtlasAttributeDef(String name, String typeName) {
        this.name = name;
        this.typeName = typeName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Boolean getOptional() {
        return optional;
    }

    public void setOptional(Boolean optional) {
        this.optional = optional;
    }

    public Cardinality getCardinality() {
        return cardinality;
    }

    public void setCardinality(Cardinality cardinality) {
        this.cardinality = cardinality;
    }

    public Integer getValuesMinCount() {
        return valuesMinCount;
    }

    public void setValuesMinCount(Integer valuesMinCount) {
        this.valuesMinCount = valuesMinCount;
    }

    public Integer getValuesMaxCount() {
        return valuesMaxCount;
    }

    public void setValuesMaxCount(Integer valuesMaxCount) {
        this.valuesMaxCount = valuesMaxCount;
    }

    public Boolean getUnique() {
        return unique;
    }

    public void setUnique(Boolean unique) {
        this.unique = unique;
    }

    public Boolean getIndexable() {
        return indexable;
    }

    public void setIndexable(Boolean indexable) {
        this.indexable = indexable;
    }

    public Boolean getIncludeInNotification() {
        return includeInNotification;
    }

    public void setIncludeInNotification(Boolean includeInNotification) {
        this.includeInNotification = includeInNotification;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSearchWeight() {
        return searchWeight;
    }

    public void setSearchWeight(Integer searchWeight) {
        this.searchWeight = searchWeight;
    }

    public IndexType getIndexType() {
        return indexType;
    }

    public void setIndexType(IndexType indexType) {
        this.indexType = indexType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<AtlasConstraintDef> getConstraints() {
        return constraints;
    }

    public void setConstraints(List<AtlasConstraintDef> constraints) {
        this.constraints = constraints;
    }

    public void addConstraint(AtlasConstraintDef constraint) {
        if (constraints == null) {
            constraints = new ArrayList<>();
        }
        constraints.add(constraint);
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }
}
