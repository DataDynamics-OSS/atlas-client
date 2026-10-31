package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;

/**
 * 엔티티 타입을 만드는 빌더입니다. {@link TypeDefs#entity(String)}으로 생성합니다.
 * <p>
 * 대부분의 엔티티 타입은 {@code DataSet}(테이블, 파일, 토픽 등)이나 {@code Process}(작업, 쿼리 등)를
 * 상속해야 합니다. 그래야 {@code qualifiedName}, {@code name}, {@code owner} 속성과 리니지 지원을 물려받습니다.
 * <p>
 * 빌더는 스레드 안전하지 않으므로 한 스레드에서만 사용해야 합니다.
 */
public final class EntityDefBuilder extends StructLikeDefBuilder<EntityDefBuilder, AtlasEntityDef> {

    /** 패키지 밖에서는 {@link TypeDefs#entity(String)}을 통해서만 만들 수 있습니다. */
    EntityDefBuilder(String name) {
        super(new AtlasEntityDef(), name);
    }

    /**
     * 상위 엔티티 타입을 추가합니다. 상위 타입의 속성을 물려받습니다. 여러 번 호출하면 상위 타입이 누적됩니다.
     *
     * @param superTypes 상위 엔티티 타입 이름들
     * @return 이 빌더
     */
    public EntityDefBuilder superTypes(String... superTypes) {
        for (String superType : superTypes) {
            def.addSuperType(superType);
        }
        return this;
    }

    /**
     * {@code superTypes("DataSet")}의 줄임입니다. 리니지에서 입력/출력이 되는 데이터셋 타입에 씁니다.
     *
     * @return 이 빌더
     */
    public EntityDefBuilder dataSet() {
        return superTypes(TypeDefs.DATA_SET);
    }

    /**
     * {@code superTypes("Process")}의 줄임입니다. 리니지에서 데이터셋을 읽고 쓰는 프로세스 타입에 씁니다.
     *
     * @return 이 빌더
     */
    public EntityDefBuilder process() {
        return superTypes(TypeDefs.PROCESS);
    }
}
