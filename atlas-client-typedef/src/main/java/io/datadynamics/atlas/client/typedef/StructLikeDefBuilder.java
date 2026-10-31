package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasStructDef;

/**
 * 속성을 갖는 정의(struct, 엔티티, classification, relationship, business metadata)를 만드는 빌더의 기반 클래스입니다.
 *
 * @param <B> 메서드 체이닝을 위한 구체 빌더 타입
 * @param <D> 만들어지는 정의 타입
 */
public abstract class StructLikeDefBuilder<B extends StructLikeDefBuilder<B, D>, D extends AtlasStructDef>
        extends TypeDefBuilder<B, D> {

    /**
     * 빈 정의 객체에 이름을 설정하여 빌더를 초기화합니다.
     *
     * @param def  채워 나갈 빈 정의 객체
     * @param name 타입 이름
     * @throws IllegalArgumentException 이름이 {@code null}이거나 비어 있을 때
     */
    protected StructLikeDefBuilder(D def, String name) {
        super(def, name);
    }

    /**
     * 속성을 추가합니다. 속성 빌더는 이 시점에 {@code build()}되어 복사되므로 이후 속성 빌더를 바꿔도 영향이 없습니다.
     *
     * @param attribute 속성 빌더
     * @return 이 빌더
     */
    public B attribute(AttributeDefBuilder attribute) {
        def.addAttributeDef(attribute.build());
        return self();
    }

    /**
     * 주어진 타입의 선택(optional) 속성을 추가합니다. {@code attribute(attr(name, typeName))}과 같습니다.
     *
     * @param name     속성 이름
     * @param typeName 속성 타입 이름
     * @return 이 빌더
     * @throws IllegalArgumentException 이름이나 타입 이름이 {@code null}이거나 비어 있을 때
     */
    public B attribute(String name, String typeName) {
        return attribute(TypeDefs.attr(name, typeName));
    }

    /**
     * 여러 속성을 순서대로 추가합니다.
     *
     * @param attributes 속성 빌더들
     * @return 이 빌더
     */
    public B attributes(AttributeDefBuilder... attributes) {
        for (AttributeDefBuilder attribute : attributes) {
            attribute(attribute);
        }
        return self();
    }
}
