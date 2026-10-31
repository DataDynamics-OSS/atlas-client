package io.datadynamics.atlas.client.model.instance;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 검색, 리니지, 변경(mutation) 응답에 담겨 오는 엔티티 요약 정보입니다.
 * <p>
 * 전체 엔티티({@link AtlasEntity})와 달리 guid, 상태, 표시 이름, classification 이름과 일부 속성만 담습니다.
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

    /** 엔티티를 화면에 표시할 때 쓰는 이름을 돌려줍니다. */
    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    /** 엔티티에 붙은 classification 이름 목록을 돌려줍니다. */
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

    /** 엔티티에 지정된 용어집(glossary) 용어 이름 목록을 돌려줍니다. */
    public List<String> getMeaningNames() {
        return meaningNames;
    }

    public void setMeaningNames(List<String> meaningNames) {
        this.meaningNames = meaningNames;
    }

    /** 용어집 용어 지정 정보를 원본 맵 형태로 돌려줍니다. */
    public List<Map<String, Object>> getMeanings() {
        return meanings;
    }

    public void setMeanings(List<Map<String, Object>> meanings) {
        this.meanings = meanings;
    }

    /** Atlas가 엔티티 정보를 일부만 가지고 있는 상태인지 돌려줍니다. JSON 속성 이름은 {@code isIncomplete}입니다. */
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

    /** {@code qualifiedName} 속성 값을 문자열로 돌려줍니다. 속성이 없으면 {@code null}입니다. */
    public String qualifiedName() {
        Object value = getAttribute(AtlasObjectId.QUALIFIED_NAME);
        return value == null ? null : value.toString();
    }

    /**
     * 이 엔티티를 guid로 가리키는 참조를 만듭니다.
     *
     * @return guid와 타입 이름을 담은 참조
     * @throws NullPointerException guid가 {@code null}일 때
     */
    public AtlasObjectId toObjectId() {
        return AtlasObjectId.byGuid(guid, getTypeName());
    }
}
