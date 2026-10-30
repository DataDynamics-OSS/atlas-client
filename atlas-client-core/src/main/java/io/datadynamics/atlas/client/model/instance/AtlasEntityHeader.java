package io.datadynamics.atlas.client.model.instance;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entity summary returned by search, lineage and mutation responses.
 */
public class AtlasEntityHeader extends AtlasStruct {

    private String guid;
    private EntityStatus status;
    private String displayText;
    private List<String> classificationNames;
    private List<AtlasClassification> classifications;
    private List<String> meaningNames;
    private List<Map<String, Object>> meanings;
    @JsonProperty("isIncomplete")
    private Boolean incomplete;
    private Set<String> labels;

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public EntityStatus getStatus() {
        return status;
    }

    public void setStatus(EntityStatus status) {
        this.status = status;
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    public List<String> getClassificationNames() {
        return classificationNames;
    }

    public void setClassificationNames(List<String> classificationNames) {
        this.classificationNames = classificationNames;
    }

    public List<AtlasClassification> getClassifications() {
        return classifications;
    }

    public void setClassifications(List<AtlasClassification> classifications) {
        this.classifications = classifications;
    }

    public List<String> getMeaningNames() {
        return meaningNames;
    }

    public void setMeaningNames(List<String> meaningNames) {
        this.meaningNames = meaningNames;
    }

    public List<Map<String, Object>> getMeanings() {
        return meanings;
    }

    public void setMeanings(List<Map<String, Object>> meanings) {
        this.meanings = meanings;
    }

    public Boolean getIncomplete() {
        return incomplete;
    }

    public void setIncomplete(Boolean incomplete) {
        this.incomplete = incomplete;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }

    /** The {@code qualifiedName} attribute, if present. */
    public String qualifiedName() {
        Object value = getAttribute(AtlasObjectId.QUALIFIED_NAME);
        return value == null ? null : value.toString();
    }

    public AtlasObjectId toObjectId() {
        return AtlasObjectId.byGuid(guid, getTypeName());
    }
}
