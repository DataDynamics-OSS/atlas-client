package io.datadynamics.atlas.client.model.search;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Payload of {@code POST /v2/search/basic}.
 */
public class SearchParameters extends AtlasModel {

    public enum SortOrder {
        ASCENDING,
        DESCENDING
    }

    private String query;
    private String typeName;
    private String classification;
    private String termName;
    private String sortBy;
    private SortOrder sortOrder;
    private Boolean excludeDeletedEntities;
    private Boolean includeClassificationAttributes;
    private Boolean includeSubTypes;
    private Boolean includeSubClassifications;
    private Integer limit;
    private Integer offset;
    private String marker;
    private FilterCriteria entityFilters;
    private FilterCriteria tagFilters;
    private Set<String> attributes;

    public SearchParameters() {
    }

    public static SearchParameters forType(String typeName) {
        SearchParameters params = new SearchParameters();
        params.setTypeName(typeName);
        params.setExcludeDeletedEntities(true);
        return params;
    }

    public SearchParameters copy() {
        SearchParameters copy = new SearchParameters();
        copy.query = query;
        copy.typeName = typeName;
        copy.classification = classification;
        copy.termName = termName;
        copy.sortBy = sortBy;
        copy.sortOrder = sortOrder;
        copy.excludeDeletedEntities = excludeDeletedEntities;
        copy.includeClassificationAttributes = includeClassificationAttributes;
        copy.includeSubTypes = includeSubTypes;
        copy.includeSubClassifications = includeSubClassifications;
        copy.limit = limit;
        copy.offset = offset;
        copy.marker = marker;
        copy.entityFilters = entityFilters;
        copy.tagFilters = tagFilters;
        copy.attributes = attributes == null ? null : new LinkedHashSet<>(attributes);
        copy.getAdditionalProperties().putAll(getAdditionalProperties());
        return copy;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public String getTermName() {
        return termName;
    }

    public void setTermName(String termName) {
        this.termName = termName;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getExcludeDeletedEntities() {
        return excludeDeletedEntities;
    }

    public void setExcludeDeletedEntities(Boolean excludeDeletedEntities) {
        this.excludeDeletedEntities = excludeDeletedEntities;
    }

    public Boolean getIncludeClassificationAttributes() {
        return includeClassificationAttributes;
    }

    public void setIncludeClassificationAttributes(Boolean includeClassificationAttributes) {
        this.includeClassificationAttributes = includeClassificationAttributes;
    }

    public Boolean getIncludeSubTypes() {
        return includeSubTypes;
    }

    public void setIncludeSubTypes(Boolean includeSubTypes) {
        this.includeSubTypes = includeSubTypes;
    }

    public Boolean getIncludeSubClassifications() {
        return includeSubClassifications;
    }

    public void setIncludeSubClassifications(Boolean includeSubClassifications) {
        this.includeSubClassifications = includeSubClassifications;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    public FilterCriteria getEntityFilters() {
        return entityFilters;
    }

    public void setEntityFilters(FilterCriteria entityFilters) {
        this.entityFilters = entityFilters;
    }

    public FilterCriteria getTagFilters() {
        return tagFilters;
    }

    public void setTagFilters(FilterCriteria tagFilters) {
        this.tagFilters = tagFilters;
    }

    /** Extra attributes to return in each {@link io.datadynamics.atlas.client.model.instance.AtlasEntityHeader}. */
    public Set<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Set<String> attributes) {
        this.attributes = attributes;
    }

    public void addAttributes(String... attributeNames) {
        if (attributes == null) {
            attributes = new LinkedHashSet<>();
        }
        attributes.addAll(Arrays.asList(attributeNames));
    }
}
