package io.datadynamics.atlas.client.model.search;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code POST /v2/search/basic}의 요청 본문, 즉 basic 검색 조건입니다.
 * <p>
 * 보통 {@link #forType(String)}으로 시작해 필요한 조건을 setter로 더합니다. 설정하지 않은 값은 {@code null}로 남아
 * JSON에서 빠지므로 Atlas 기본값이 적용됩니다.
 */
public class SearchParameters extends AtlasModel {

    /** 정렬 방향입니다. */
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

    /** 아무 조건도 없는 검색 조건을 만듭니다. */
    public SearchParameters() {
    }

    /**
     * 한 엔티티 타입을 검색하는 조건을 만듭니다. 삭제된 엔티티는 기본으로 제외합니다.
     *
     * @param typeName 검색할 엔티티 타입 이름
     * @return 새 검색 조건
     */
    public static SearchParameters forType(String typeName) {
        SearchParameters params = new SearchParameters();
        params.setTypeName(typeName);
        params.setExcludeDeletedEntities(true);
        return params;
    }

    /**
     * 이 검색 조건의 복사본을 만듭니다. 페이지마다 {@code limit}/{@code offset}만 바꿔 보낼 때 원본을 건드리지
     * 않으려고 씁니다.
     * <p>
     * {@code attributes} 집합과 추가 속성 맵은 새로 복사하지만, 필터({@link FilterCriteria}) 객체는 원본과
     * 공유하는 얕은 복사입니다.
     *
     * @return 새 검색 조건
     */
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

    /** 함께 적용할 전문 검색어를 돌려줍니다. */
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

    /** 용어집 용어로 결과를 제한할 때 쓰는 용어 이름을 돌려줍니다. */
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

    /** 결과 엔티티 요약에 classification 속성 값까지 담을지 돌려줍니다. */
    public Boolean getIncludeClassificationAttributes() {
        return includeClassificationAttributes;
    }

    public void setIncludeClassificationAttributes(Boolean includeClassificationAttributes) {
        this.includeClassificationAttributes = includeClassificationAttributes;
    }

    /** {@code typeName}의 하위 타입 엔티티도 결과에 포함할지 돌려줍니다. */
    public Boolean getIncludeSubTypes() {
        return includeSubTypes;
    }

    public void setIncludeSubTypes(Boolean includeSubTypes) {
        this.includeSubTypes = includeSubTypes;
    }

    /** {@code classification}의 하위 classification이 붙은 엔티티도 결과에 포함할지 돌려줍니다. */
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

    /** marker 기반 페이지 조회에서 이전 응답의 {@link AtlasSearchResult#getNextMarker()} 값을 돌려줍니다. */
    public String getMarker() {
        return marker;
    }

    public void setMarker(String marker) {
        this.marker = marker;
    }

    /** 엔티티 속성에 대한 필터 조건을 돌려줍니다. */
    public FilterCriteria getEntityFilters() {
        return entityFilters;
    }

    public void setEntityFilters(FilterCriteria entityFilters) {
        this.entityFilters = entityFilters;
    }

    /** classification 속성에 대한 필터 조건을 돌려줍니다. */
    public FilterCriteria getTagFilters() {
        return tagFilters;
    }

    public void setTagFilters(FilterCriteria tagFilters) {
        this.tagFilters = tagFilters;
    }

    /** 결과의 각 {@link io.datadynamics.atlas.client.model.instance.AtlasEntityHeader}에 추가로 담을 속성 이름들입니다. */
    public Set<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Set<String> attributes) {
        this.attributes = attributes;
    }

    /**
     * 결과에 추가로 담을 속성 이름들을 더합니다. 집합이 없으면 새로 만들며 추가한 순서를 유지합니다.
     *
     * @param attributeNames 속성 이름들
     */
    public void addAttributes(String... attributeNames) {
        if (attributes == null) {
            attributes = new LinkedHashSet<>();
        }
        attributes.addAll(Arrays.asList(attributeNames));
    }
}
