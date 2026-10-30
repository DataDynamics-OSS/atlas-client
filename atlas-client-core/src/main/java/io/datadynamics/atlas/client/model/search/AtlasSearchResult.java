package io.datadynamics.atlas.client.model.search;

import io.datadynamics.atlas.client.model.AtlasModel;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of basic, DSL and full-text searches.
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

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public SearchParameters getSearchParameters() {
        return searchParameters;
    }

    public void setSearchParameters(SearchParameters searchParameters) {
        this.searchParameters = searchParameters;
    }

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

    /** Matching entities; never {@code null} (Atlas omits the field when nothing matches). */
    public List<AtlasEntityHeader> getEntities() {
        return entities == null ? Collections.emptyList() : entities;
    }

    public void setEntities(List<AtlasEntityHeader> entities) {
        this.entities = entities;
    }

    /** Tabular result of DSL {@code select} queries. */
    public AttributeSearchResult getAttributes() {
        return attributes;
    }

    public void setAttributes(AttributeSearchResult attributes) {
        this.attributes = attributes;
    }

    public List<Map<String, Object>> getFullTextResult() {
        return fullTextResult;
    }

    public void setFullTextResult(List<Map<String, Object>> fullTextResult) {
        this.fullTextResult = fullTextResult;
    }

    public Map<String, AtlasEntityHeader> getReferredEntities() {
        return referredEntities;
    }

    public void setReferredEntities(Map<String, AtlasEntityHeader> referredEntities) {
        this.referredEntities = referredEntities;
    }

    public Long getApproximateCount() {
        return approximateCount;
    }

    public void setApproximateCount(Long approximateCount) {
        this.approximateCount = approximateCount;
    }

    public String getNextMarker() {
        return nextMarker;
    }

    public void setNextMarker(String nextMarker) {
        this.nextMarker = nextMarker;
    }

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
