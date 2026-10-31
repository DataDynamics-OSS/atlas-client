package io.datadynamics.atlas.client.model.typedef;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 모든 Atlas 타입 정의가 공통으로 가지는 필드를 모은 추상 클래스입니다.
 * <p>
 * 하위 클래스는 생성자에서 자신의 {@link TypeCategory}를 정해 넘깁니다. 그래서 새로 만든 정의 객체는
 * 따로 설정하지 않아도 올바른 {@code category} 값을 가집니다.
 */
public abstract class AtlasBaseTypeDef extends AtlasModel {

    private TypeCategory category;
    private String guid;
    private String createdBy;
    private String updatedBy;
    private Instant createTime;
    private Instant updateTime;
    private Long version;
    private String name;
    private String description;
    private String typeVersion;
    private String serviceType;
    private Map<String, String> options;

    /**
     * 분류만 정해 정의를 만듭니다.
     *
     * @param category 이 정의의 타입 분류
     */
    protected AtlasBaseTypeDef(TypeCategory category) {
        this.category = category;
    }

    /**
     * 분류와 이름을 정해 정의를 만듭니다.
     *
     * @param category 이 정의의 타입 분류
     * @param name     타입 이름(예: {@code hive_table})
     */
    protected AtlasBaseTypeDef(TypeCategory category, String name) {
        this.category = category;
        this.name = name;
    }

    public TypeCategory getCategory() {
        return category;
    }

    public void setCategory(TypeCategory category) {
        this.category = category;
    }

    /** Atlas가 타입 정의에 부여한 guid를 돌려줍니다. 아직 Atlas에 저장하지 않은 정의라면 {@code null}입니다. */
    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
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

    /** Atlas가 관리하는 이 타입 정의의 버전 번호를 돌려줍니다. {@link #getTypeVersion()}과는 다른 값입니다. */
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** 타입 작성자가 붙이는 타입 버전 문자열(예: {@code "1.0"})을 돌려줍니다. */
    public String getTypeVersion() {
        return typeVersion;
    }

    public void setTypeVersion(String typeVersion) {
        this.typeVersion = typeVersion;
    }

    /** 이 타입이 속한 서비스 구분(예: {@code hive})을 돌려줍니다. */
    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    /** 타입 수준의 옵션(키-값 문자열 맵)을 돌려줍니다. 설정한 적이 없으면 {@code null}일 수 있습니다. */
    public Map<String, String> getOptions() {
        return options;
    }

    public void setOptions(Map<String, String> options) {
        this.options = options;
    }

    /**
     * 타입 수준 옵션 하나를 추가하거나 덮어씁니다. 옵션 맵이 없으면 새로 만듭니다.
     *
     * @param key   옵션 키
     * @param value 옵션 값
     */
    public void putOption(String key, String value) {
        if (options == null) {
            options = new LinkedHashMap<>();
        }
        options.put(key, value);
    }
}
