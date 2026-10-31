package io.datadynamics.atlas.client.model.instance;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 엔티티 인스턴스입니다.
 * <p>
 * Atlas가 쓰는 두 가지 속성 맵에 주의해야 합니다. 엔티티 타입에 선언된 속성은
 * {@link #getAttributes() attributes}에, relationship 타입이 더해 주는 속성은
 * {@link #getRelationshipAttributes() relationshipAttributes}에 들어갑니다.
 * <p>
 * 새 엔티티를 만들 때는 보통 타입 이름과 {@code qualifiedName} 등 필수 속성만 채우고, guid는 비워 두거나
 * 같은 요청 안에서 참조해야 하면 {@link #assignPlaceholderGuid()}로 임시 guid를 붙입니다.
 */
public class AtlasEntity extends AtlasStruct {

    // 임시 guid 번호 생성기입니다. JVM마다 다른 값에서 시작하도록 System.nanoTime()의 하위 비트로 초기화합니다.
    private static final AtomicLong PLACEHOLDER_GUID_SEQ = new AtomicLong(System.nanoTime() & 0xFFFFFFFL);

    private String guid;
    private String homeId;
    @JsonProperty("isProxy")
    private Boolean proxy;
    @JsonProperty("isIncomplete")
    private Boolean incomplete;
    private Integer provenanceType;
    private EntityStatus status;
    private String createdBy;
    private String updatedBy;
    private Instant createTime;
    private Instant updateTime;
    private Long version;
    private Map<String, Object> relationshipAttributes;
    private List<AtlasClassification> classifications;
    private List<Map<String, Object>> meanings;
    private Map<String, String> customAttributes;
    private Map<String, Map<String, Object>> businessAttributes;
    private Set<String> labels;

    /** 빈 엔티티를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasEntity() {
    }

    /**
     * 타입 이름만 지정해 엔티티를 만듭니다.
     *
     * @param typeName 엔티티 타입 이름(예: {@code hive_table})
     */
    public AtlasEntity(String typeName) {
        super(typeName);
    }

    /**
     * 타입 이름과 속성 값으로 엔티티를 만듭니다. 맵은 복사해서 보관합니다.
     *
     * @param typeName   엔티티 타입 이름
     * @param attributes 엔티티 타입에 선언된 속성 값
     */
    public AtlasEntity(String typeName, Map<String, Object> attributes) {
        super(typeName, attributes);
    }

    /**
     * 음수 형태의 임시(placeholder) guid를 붙입니다.
     * <p>
     * Atlas가 실제 guid를 부여하기 전에도, 같은 일괄 요청 안의 다른 엔티티가 이 엔티티를 guid로 참조할 수 있게
     * 하려는 것입니다. 요청 후 {@link EntityMutationResponse#getGuidAssignments()}에서 임시 guid와 실제 guid의
     * 대응을 확인할 수 있습니다. 이미 있던 guid는 덮어씁니다.
     *
     * @return 메서드 체이닝을 위한 이 엔티티
     */
    public AtlasEntity assignPlaceholderGuid() {
        this.guid = "-" + PLACEHOLDER_GUID_SEQ.incrementAndGet();
        return this;
    }

    /**
     * 이 엔티티를 가리키는 참조를 만듭니다. guid가 있으면 guid로, 없으면 {@code qualifiedName}으로 가리킵니다.
     *
     * @return 이 엔티티에 대한 참조
     * @throws IllegalStateException guid도 {@code qualifiedName} 속성도 없을 때
     */
    public AtlasObjectId toObjectId() {
        if (guid != null) {
            return AtlasObjectId.byGuid(guid, getTypeName());
        }
        Object qualifiedName = getAttribute(AtlasObjectId.QUALIFIED_NAME);
        if (qualifiedName == null) {
            throw new IllegalStateException("Entity of type " + getTypeName() + " has neither guid nor qualifiedName");
        }
        return AtlasObjectId.byQualifiedName(getTypeName(), qualifiedName.toString());
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    /** 엔티티가 처음 만들어진 Atlas 인스턴스를 나타내는 식별자입니다. 여러 Atlas 사이에서 복제할 때 쓰입니다. */
    public String getHomeId() {
        return homeId;
    }

    public void setHomeId(String homeId) {
        this.homeId = homeId;
    }

    /** 엔티티가 실제 내용 없이 참조용으로만 존재하는지 돌려줍니다. JSON 속성 이름은 {@code isProxy}입니다. */
    public Boolean getProxy() {
        return proxy;
    }

    public void setProxy(Boolean proxy) {
        this.proxy = proxy;
    }

    /** Atlas가 엔티티 정보를 일부만 가지고 있는 상태인지 돌려줍니다. JSON 속성 이름은 {@code isIncomplete}입니다. */
    public Boolean getIncomplete() {
        return incomplete;
    }

    public void setIncomplete(Boolean incomplete) {
        this.incomplete = incomplete;
    }

    /** 엔티티가 어디에서 왔는지를 나타내는 Atlas의 출처 구분 값을 돌려줍니다. */
    public Integer getProvenanceType() {
        return provenanceType;
    }

    public void setProvenanceType(Integer provenanceType) {
        this.provenanceType = provenanceType;
    }

    public EntityStatus getStatus() {
        return status;
    }

    public void setStatus(EntityStatus status) {
        this.status = status;
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

    /** Atlas가 엔티티를 수정할 때마다 올리는 버전 번호를 돌려줍니다. */
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    /**
     * relationship 타입이 더해 주는 속성들을 돌려줍니다. 조회 응답에서는 값이 보통 참조 정보
     * ({@link AtlasRelatedObjectId} 형태의 맵)입니다. 설정한 적이 없으면 {@code null}일 수 있습니다.
     */
    public Map<String, Object> getRelationshipAttributes() {
        return relationshipAttributes;
    }

    public void setRelationshipAttributes(Map<String, Object> relationshipAttributes) {
        this.relationshipAttributes = relationshipAttributes;
    }

    /**
     * relationship 속성 값 하나를 돌려줍니다.
     *
     * @param name 속성 이름
     * @return 속성 값, 없으면 {@code null}
     */
    public Object getRelationshipAttribute(String name) {
        return relationshipAttributes == null ? null : relationshipAttributes.get(name);
    }

    /**
     * relationship 속성 값 하나를 설정합니다. 맵이 없으면 새로 만듭니다. 값으로는 보통
     * {@link AtlasObjectId}(또는 그 목록)를 넣습니다.
     *
     * @param name  속성 이름
     * @param value 속성 값
     */
    public void setRelationshipAttribute(String name, Object value) {
        if (relationshipAttributes == null) {
            relationshipAttributes = new LinkedHashMap<>();
        }
        relationshipAttributes.put(name, value);
    }

    public List<AtlasClassification> getClassifications() {
        return classifications;
    }

    public void setClassifications(List<AtlasClassification> classifications) {
        this.classifications = classifications;
    }

    /**
     * classification을 하나 추가합니다. 목록이 없으면 새로 만듭니다. 엔티티를 생성·수정하는 요청과 함께
     * 보내야 Atlas에 반영됩니다.
     *
     * @param classification 추가할 classification
     */
    public void addClassification(AtlasClassification classification) {
        if (classifications == null) {
            classifications = new ArrayList<>();
        }
        classifications.add(classification);
    }

    /** 용어집(glossary) 용어 지정 정보입니다. glossary 모듈이 이를 모델로 만들기 전까지는 원본 맵 형태로 둡니다. */
    public List<Map<String, Object>> getMeanings() {
        return meanings;
    }

    public void setMeanings(List<Map<String, Object>> meanings) {
        this.meanings = meanings;
    }

    /** 타입 정의와 상관없이 엔티티에 붙이는 자유로운 문자열 키-값 속성을 돌려줍니다. */
    public Map<String, String> getCustomAttributes() {
        return customAttributes;
    }

    public void setCustomAttributes(Map<String, String> customAttributes) {
        this.customAttributes = customAttributes;
    }

    /**
     * business metadata 속성 값을 돌려줍니다. 바깥 맵의 키는 business metadata 타입 이름이고, 안쪽 맵은
     * 그 타입의 속성 이름에서 값으로의 맵입니다.
     */
    public Map<String, Map<String, Object>> getBusinessAttributes() {
        return businessAttributes;
    }

    public void setBusinessAttributes(Map<String, Map<String, Object>> businessAttributes) {
        this.businessAttributes = businessAttributes;
    }

    /** 엔티티에 붙은 레이블(자유 형식 문자열 태그) 집합을 돌려줍니다. */
    public Set<String> getLabels() {
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }

    /**
     * 레이블을 하나 추가합니다. 집합이 없으면 새로 만들며 추가한 순서를 유지합니다.
     *
     * @param label 추가할 레이블
     */
    public void addLabel(String label) {
        if (labels == null) {
            labels = new LinkedHashSet<>();
        }
        labels.add(label);
    }
}
