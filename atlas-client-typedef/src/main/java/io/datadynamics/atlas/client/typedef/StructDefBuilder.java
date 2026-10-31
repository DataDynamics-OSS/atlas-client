package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasStructDef;

/**
 * struct 타입(엔티티 안에 포함되는 값 객체)을 만드는 빌더입니다. {@link TypeDefs#struct(String)}으로 생성합니다.
 * <p>
 * 속성 추가 등 모든 설정은 {@link StructLikeDefBuilder}와 {@link TypeDefBuilder}에서 상속합니다.
 */
public final class StructDefBuilder extends StructLikeDefBuilder<StructDefBuilder, AtlasStructDef> {

    /** 패키지 밖에서는 {@link TypeDefs#struct(String)}을 통해서만 만들 수 있습니다. */
    StructDefBuilder(String name) {
        super(new AtlasStructDef(), name);
    }
}
