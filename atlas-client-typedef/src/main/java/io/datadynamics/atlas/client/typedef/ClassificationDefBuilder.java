package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasClassificationDef;

/**
 * classification(태그) 타입을 만드는 빌더입니다. {@link TypeDefs#classification(String)}으로 생성합니다.
 * <p>
 * 공통 설정(설명, 버전, 속성 등)은 {@link StructLikeDefBuilder}와 {@link TypeDefBuilder}에서 상속합니다.
 * 빌더는 스레드 안전하지 않으므로 한 스레드에서만 사용해야 합니다.
 */
public final class ClassificationDefBuilder
        extends StructLikeDefBuilder<ClassificationDefBuilder, AtlasClassificationDef> {

    /** 패키지 밖에서는 {@link TypeDefs#classification(String)}을 통해서만 만들 수 있습니다. */
    ClassificationDefBuilder(String name) {
        super(new AtlasClassificationDef(), name);
    }

    /**
     * 상위 classification 타입을 추가합니다. 상위 타입의 속성을 물려받습니다.
     * 여러 번 호출하면 상위 타입이 누적됩니다.
     *
     * @param superTypes 상위 classification 타입 이름들
     * @return 이 빌더
     */
    public ClassificationDefBuilder superTypes(String... superTypes) {
        for (String superType : superTypes) {
            def.addSuperType(superType);
        }
        return this;
    }

    /**
     * classification을 붙일 수 있는 엔티티 타입을 이 타입들(과 그 하위 타입)로 제한합니다.
     * 호출하지 않으면 어떤 엔티티에도 붙일 수 있습니다.
     *
     * @param entityTypes 허용할 엔티티 타입 이름들
     * @return 이 빌더
     */
    public ClassificationDefBuilder entityTypes(String... entityTypes) {
        for (String entityType : entityTypes) {
            def.addEntityType(entityType);
        }
        return this;
    }
}
