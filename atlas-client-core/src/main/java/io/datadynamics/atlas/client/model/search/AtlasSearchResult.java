package io.datadynamics.atlas.client.model.search;

import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * basic, DSL, 전문(full-text) 검색의 결과입니다.
 * <p>
 * 어떤 필드가 채워지는지는 검색 종류에 따라 다릅니다. 엔티티 검색 결과는 {@link #getEntities()}에,
 * DSL {@code select} 쿼리의 표 형태 결과는 {@link #getAttributes()}에, 전문 검색 결과는
 * {@link #getFullTextResult()}에 담깁니다.
 */
public class AtlasSearchResult extends AtlasModel {

    private String queryType;
    private SearchParameters searchParameters;
    private String queryText;
    private String type;
    private String classification;
    private List<AtlasEntityHeader> entities;
    private AttributeSearchResult attributes;
    private List<Map<String, Object>> fullTextResult;
    private Map<String, AtlasEntityHeader> referredEntities;
    private Long approximateCount;
    private String nextMarker;

    /** Atlas가 실행한 검색 종류(예: {@code BASIC}, {@code DSL})를 문자열로 돌려줍니다. */
    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    /** basic 검색이었다면 그 검색 조건을 돌려줍니다. */
    public SearchParameters getSearchParameters() {
        return searchParameters;
    }

    public void setSearchParameters(SearchParameters searchParameters) {
        this.searchParameters = searchParameters;
    }

    /** DSL·전문 검색이었다면 실행된 쿼리 문자열을 돌려줍니다. */
    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    /** 조건에 맞는 엔티티 목록을 돌려줍니다. 결과가 없을 때 Atlas가 필드를 생략해도 {@code null}이 아니라 빈 목록을 돌려줍니다. */
    public List<AtlasEntityHeader> getEntities() {
        return entities == null ? Collections.emptyList() : entities;
    }

    public void setEntities(List<AtlasEntityHeader> entities) {
        this.entities = entities;
    }

    /** DSL {@code select} 쿼리의 표 형태 결과를 돌려줍니다. 다른 쿼리에서는 {@code null}입니다. */
    public AttributeSearchResult getAttributes() {
        return attributes;
    }

    public void setAttributes(AttributeSearchResult attributes) {
        this.attributes = attributes;
    }

    /** 전문 검색 결과를 원본 맵 목록으로 돌려줍니다. */
    public List<Map<String, Object>> getFullTextResult() {
        return fullTextResult;
    }

    public void setFullTextResult(List<Map<String, Object>> fullTextResult) {
        this.fullTextResult = fullTextResult;
    }

    /** 결과 엔티티가 참조하는 엔티티들을 guid를 키로 돌려줍니다. */
    public Map<String, AtlasEntityHeader> getReferredEntities() {
        return referredEntities;
    }

    public void setReferredEntities(Map<String, AtlasEntityHeader> referredEntities) {
        this.referredEntities = referredEntities;
    }

    /** Atlas가 추정한 전체 결과 수를 돌려줍니다. 정확한 값이 아닐 수 있으며 응답에 없으면 {@code null}입니다. */
    public Long getApproximateCount() {
        return approximateCount;
    }

    public void setApproximateCount(Long approximateCount) {
        this.approximateCount = approximateCount;
    }

    /** marker 기반 페이지 조회에서 다음 페이지를 가져올 때 쓰는 값을 돌려줍니다({@link SearchParameters#setMarker(String)}). */
    public String getNextMarker() {
        return nextMarker;
    }

    public void setNextMarker(String nextMarker) {
        this.nextMarker = nextMarker;
    }

    /**
     * DSL {@code select} 쿼리의 표 형태 결과입니다. {@link #getName()}이 열 이름 목록이고, {@link #getValues()}의
     * 각 원소가 그 열 순서를 따르는 행 하나입니다.
     */
    public static class AttributeSearchResult extends AtlasModel {

        private List<String> name;
        private List<List<Object>> values;

        public List<String> getName() {
            return name;
        }

        public void setName(List<String> name) {
            this.name = name;
        }

        public List<List<Object>> getValues() {
            return values;
        }

        public void setValues(List<List<Object>> values) {
            this.values = values;
        }
    }
}
