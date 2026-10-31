package io.datadynamics.atlas.client.model.typedef;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * classification(태그) 타입 정의입니다.
 * <p>
 * 예를 들어 {@code PII} 같은 분류를 정의하고, 이를 엔티티에 붙여 민감 정보임을 표시할 때 씁니다.
 */
public class AtlasClassificationDef extends AtlasStructDef {

    private Set<String> superTypes;
    private Set<String> entityTypes;
    private Set<String> subTypes;

    /** 빈 classification 타입 정의를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasClassificationDef() {
        super(TypeCategory.CLASSIFICATION);
    }

    /**
     * 이름을 지정해 classification 타입 정의를 만듭니다.
     *
     * @param name 타입 이름
     */
    public AtlasClassificationDef(String name) {
        super(TypeCategory.CLASSIFICATION, name);
    }

    /** 이 classification이 상속하는 상위 classification 타입 이름들을 돌려줍니다. */
    public Set<String> getSuperTypes() {
        return superTypes;
    }

    public void setSuperTypes(Set<String> superTypes) {
        this.superTypes = superTypes;
    }

    /**
     * 상위 classification 타입을 하나 추가합니다. 집합이 없으면 새로 만듭니다.
     *
     * @param superType 상위 타입 이름
     */
    public void addSuperType(String superType) {
        if (superTypes == null) {
            superTypes = new LinkedHashSet<>();
        }
        superTypes.add(superType);
    }

    /** 이 classification을 붙일 수 있는 엔티티 타입 이름들을 돌려줍니다. 비어 있으면 모든 엔티티 타입에 붙일 수 있습니다. */
    public Set<String> getEntityTypes() {
        return entityTypes;
    }

    public void setEntityTypes(Set<String> entityTypes) {
        this.entityTypes = entityTypes;
    }

    /**
     * 이 classification을 붙일 수 있는 엔티티 타입을 하나 추가합니다. 집합이 없으면 새로 만듭니다.
     *
     * @param entityType 엔티티 타입 이름
     */
    public void addEntityType(String entityType) {
        if (entityTypes == null) {
            entityTypes = new LinkedHashSet<>();
        }
        entityTypes.add(entityType);
    }

    /** 이 classification을 상속하는 하위 타입 이름들을 돌려줍니다. Atlas가 계산해 조회 응답에 채워 주는 값입니다. */
    public Set<String> getSubTypes() {
        return subTypes;
    }

    public void setSubTypes(Set<String> subTypes) {
        this.subTypes = subTypes;
    }
}
