package io.datadynamics.atlas.client.model.typedef;

import io.datadynamics.atlas.client.model.AtlasModel;

/**
 * {@code /v2/types/typedefs/headers}가 돌려주는 가벼운 타입 요약 정보입니다.
 * <p>
 * 속성 정의 같은 상세 내용 없이 guid, 이름, 분류, 서비스 구분만 담습니다. 많은 타입의 존재 여부를
 * 한꺼번에 확인할 때처럼 전체 정의가 필요 없을 때 씁니다.
 */
public class AtlasTypeDefHeader extends AtlasModel {

    private String guid;
    private String name;
    private TypeCategory category;
    private String serviceType;

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TypeCategory getCategory() {
        return category;
    }

    public void setCategory(TypeCategory category) {
        this.category = category;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }
}
