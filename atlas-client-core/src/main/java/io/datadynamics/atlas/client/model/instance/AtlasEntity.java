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
 * Entity instance.
 * <p>
 * Note the two attribute maps Atlas uses: {@link #getAttributes() attributes} for attributes
 * declared on the entity type, and {@link #getRelationshipAttributes() relationshipAttributes}
 * for attributes contributed by relationship types.
 */
public class AtlasEntity extends AtlasStruct {

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

    public AtlasEntity() {
    }

    public AtlasEntity(String typeName) {
        super(typeName);
    }

    public AtlasEntity(String typeName, Map<String, Object> attributes) {
        super(typeName, attributes);
    }

    /**
     * Assigns a negative placeholder guid so that other entities in the same bulk request can
     * reference this entity before Atlas has assigned its real guid.
     * {@link EntityMutationResponse#getGuidAssignments()} maps placeholders to real guids.
     */
    public AtlasEntity assignPlaceholderGuid() {
        this.guid = "-" + PLACEHOLDER_GUID_SEQ.incrementAndGet();
        return this;
    }

    /**
     * Reference to this entity: by guid when one is set, otherwise by {@code qualifiedName}.
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

    public String getHomeId() {
        return homeId;
    }

    public void setHomeId(String homeId) {
        this.homeId = homeId;
    }

    public Boolean getProxy() {
        return proxy;
    }

    public void setProxy(Boolean proxy) {
        this.proxy = proxy;
    }

    public Boolean getIncomplete() {
        return incomplete;
    }

    public void setIncomplete(Boolean incomplete) {
        this.incomplete = incomplete;
    }

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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Map<String, Object> getRelationshipAttributes() {
        return relationshipAttributes;
    }

    public void setRelationshipAttributes(Map<String, Object> relationshipAttributes) {
        this.relationshipAttributes = relationshipAttributes;
    }

    public Object getRelationshipAttribute(String name) {
        return relationshipAttributes == null ? null : relationshipAttributes.get(name);
    }

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

    public void addClassification(AtlasClassification classification) {
        if (classifications == null) {
            classifications = new ArrayList<>();
        }
        classifications.add(classification);
    }

    /** Glossary term assignments, kept as raw maps until the glossary module models them. */
    public List<Map<String, Object>> getMeanings() {
        return meanings;
    }

    public void setMeanings(List<Map<String, Object>> meanings) {
        this.meanings = meanings;
    }

    public Map<String, String> getCustomAttributes() {
        return customAttributes;
    }

    public void setCustomAttributes(Map<String, String> customAttributes) {
        this.customAttributes = customAttributes;
    }

    public Map<String, Map<String, Object>> getBusinessAttributes() {
        return businessAttributes;
    }

    public void setBusinessAttributes(Map<String, Map<String, Object>> businessAttributes) {
        this.businessAttributes = businessAttributes;
    }

    public Set<String> getLabels() {
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }

    public void addLabel(String label) {
        if (labels == null) {
            labels = new LinkedHashSet<>();
        }
        labels.add(label);
    }
}
