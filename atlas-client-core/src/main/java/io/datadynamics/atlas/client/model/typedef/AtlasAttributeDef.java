package io.datadynamics.atlas.client.model.typedef;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * struct, 엔티티, classification, business metadata 타입이 가지는 속성 하나의 정의입니다.
 * <p>
 * 최소한 이름({@code name})과 속성의 타입 이름({@code typeName}, 예: {@code string}, {@code int},
 * {@code array<string>})이 있어야 합니다. 나머지 값은 설정하지 않으면 {@code null}로 남고 JSON에서 생략되므로
 * Atlas의 기본값이 적용됩니다.
 */
public class AtlasAttributeDef extends AtlasModel {

    /** 문자열 속성을 인덱싱할 때 Atlas가 참고하는 인덱스 종류 힌트입니다. */
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

    /** 빈 속성 정의를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasAttributeDef() {
    }

    /**
     * 이름과 타입을 지정해 속성 정의를 만듭니다.
     *
     * @param name     속성 이름
     * @param typeName 속성 값의 타입 이름(예: {@code string}, {@code array<string>})
     */
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

    /** 값을 생략해도 되는 속성인지 돌려줍니다. JSON 속성 이름은 {@code isOptional}입니다. */
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

    /** 값이 여러 개인 속성(LIST/SET)에서 최소 값 개수를 돌려줍니다. */
    public Integer getValuesMinCount() {
        return valuesMinCount;
    }

    public void setValuesMinCount(Integer valuesMinCount) {
        this.valuesMinCount = valuesMinCount;
    }

    /** 값이 여러 개인 속성(LIST/SET)에서 최대 값 개수를 돌려줍니다. */
    public Integer getValuesMaxCount() {
        return valuesMaxCount;
    }

    public void setValuesMaxCount(Integer valuesMaxCount) {
        this.valuesMaxCount = valuesMaxCount;
    }

    /** 값이 같은 타입의 엔티티들 사이에서 유일해야 하는지 돌려줍니다. JSON 속성 이름은 {@code isUnique}입니다. */
    public Boolean getUnique() {
        return unique;
    }

    public void setUnique(Boolean unique) {
        this.unique = unique;
    }

    /** 검색 인덱스를 만들 속성인지 돌려줍니다. JSON 속성 이름은 {@code isIndexable}입니다. */
    public Boolean getIndexable() {
        return indexable;
    }

    public void setIndexable(Boolean indexable) {
        this.indexable = indexable;
    }

    /** 엔티티 변경 알림 메시지에 이 속성 값을 포함할지 돌려줍니다. */
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

    /** 검색 결과 순위를 매길 때 이 속성에 주는 가중치를 돌려줍니다. */
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

    /**
     * 제약 조건을 하나 추가합니다. 목록이 없으면 새로 만듭니다.
     *
     * @param constraint 추가할 제약 조건
     */
    public void addConstraint(AtlasConstraintDef constraint) {
        if (constraints == null) {
            constraints = new ArrayList<>();
        }
        constraints.add(constraint);
    }

    /**
     * 속성 수준 옵션을 돌려줍니다. 예를 들어 business metadata 속성은
     * {@link AtlasBusinessMetadataDef#OPT_APPLICABLE_ENTITY_TYPES} 옵션을 여기에 담습니다.
     */
    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }
}
