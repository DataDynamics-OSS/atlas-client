package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasEnumDef;

import java.util.ArrayList;

/**
 * enum 타입을 만드는 빌더입니다. {@link TypeDefs#enumType(String)}으로 생성합니다.
 * 각 값의 ordinal은 선언한 순서를 따릅니다.
 * <p>
 * 빌더는 스레드 안전하지 않으므로 한 스레드에서만 사용해야 합니다.
 */
public final class EnumDefBuilder extends TypeDefBuilder<EnumDefBuilder, AtlasEnumDef> {

    /** 패키지 밖에서는 {@link TypeDefs#enumType(String)}을 통해서만 만들 수 있습니다. */
    EnumDefBuilder(String name) {
        super(new AtlasEnumDef(), name);
        def.setElementDefs(new ArrayList<>());
    }

    /**
     * enum 값을 선언 순서대로 추가합니다. 여러 번 호출하면 값이 뒤에 이어 붙습니다.
     *
     * @param values 추가할 값들
     * @return 이 빌더
     */
    public EnumDefBuilder values(String... values) {
        for (String value : values) {
            def.addElement(value);
        }
        return this;
    }

    /**
     * 설명이 붙은 enum 값 하나를 추가합니다.
     *
     * @param value       추가할 값
     * @param description 값에 대한 설명
     * @return 이 빌더
     */
    public EnumDefBuilder value(String value, String description) {
        def.addElement(value);
        // 방금 추가한 원소는 목록의 마지막에 있으므로 그 원소에 설명을 붙입니다.
        def.getElementDefs().get(def.getElementDefs().size() - 1).setDescription(description);
        return this;
    }

    /**
     * 기본값을 지정합니다. 선언된 enum 값 중 하나여야 하며, 아니면 {@code build()}에서 검증 오류가 납니다.
     *
     * @param defaultValue 기본값
     * @return 이 빌더
     */
    public EnumDefBuilder defaultValue(String defaultValue) {
        def.setDefaultValue(defaultValue);
        return this;
    }
}
