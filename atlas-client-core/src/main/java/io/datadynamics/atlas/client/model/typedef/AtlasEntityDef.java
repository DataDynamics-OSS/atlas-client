package io.datadynamics.atlas.client.model.typedef;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 엔티티 타입 정의입니다.
 * <p>
 * {@code subTypes}, {@code relationshipAttributeDefs}, {@code businessAttributeDefs}는 Atlas가 계산해서
 * 조회 응답에만 채워 주는 값입니다. 생성·수정 요청에 넣어도 Atlas는 무시합니다.
 */
public class AtlasEntityDef extends AtlasStructDef {

    private Set<String> superTypes;
    private Set<String> subTypes;
    private List<AtlasAttributeDef> relationshipAttributeDefs;
    private Map<String, List<AtlasAttributeDef>> businessAttributeDefs;

    /** 빈 엔티티 타입 정의를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasEntityDef() {
        super(TypeCategory.ENTITY);
    }

    /**
     * 이름을 지정해 엔티티 타입 정의를 만듭니다.
     *
     * @param name 타입 이름
     */
    public AtlasEntityDef(String name) {
        super(TypeCategory.ENTITY, name);
    }

    /** 이 타입이 상속하는 상위 엔티티 타입 이름들을 돌려줍니다(예: {@code DataSet}, {@code Process}). */
    public Set<String> getSuperTypes() {
        return superTypes;
    }

    public void setSuperTypes(Set<String> superTypes) {
        this.superTypes = superTypes;
    }

    /**
     * 상위 엔티티 타입을 하나 추가합니다. 집합이 없으면 새로 만듭니다.
     *
     * @param superType 상위 타입 이름
     */
    public void addSuperType(String superType) {
        if (superTypes == null) {
            superTypes = new LinkedHashSet<>();
        }
        superTypes.add(superType);
    }

    /** 이 타입을 상속하는 하위 타입 이름들을 돌려줍니다. Atlas가 계산하는 읽기 전용 값입니다. */
    public Set<String> getSubTypes() {
        return subTypes;
    }

    public void setSubTypes(Set<String> subTypes) {
        this.subTypes = subTypes;
    }

    /** relationship 타입이 이 엔티티 타입에 더해 주는 속성 정의들입니다. Atlas가 계산하는 읽기 전용 값입니다. */
    public List<AtlasAttributeDef> getRelationshipAttributeDefs() {
        return relationshipAttributeDefs;
    }

    public void setRelationshipAttributeDefs(List<AtlasAttributeDef> relationshipAttributeDefs) {
        this.relationshipAttributeDefs = relationshipAttributeDefs;
    }

    /**
     * 이 엔티티 타입에 적용할 수 있는 business metadata 속성 정의들입니다. 키는 business metadata 타입 이름입니다.
     * Atlas가 계산하는 읽기 전용 값입니다.
     */
    public Map<String, List<AtlasAttributeDef>> getBusinessAttributeDefs() {
        return businessAttributeDefs;
    }

    public void setBusinessAttributeDefs(Map<String, List<AtlasAttributeDef>> businessAttributeDefs) {
        this.businessAttributeDefs = businessAttributeDefs;
    }
}
