package io.datadynamics.atlas.client.model.typedef;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.List;

/**
 * enum 타입 정의입니다. 속성이 가질 수 있는 값을 정해진 목록으로 제한할 때 씁니다.
 */
public class AtlasEnumDef extends AtlasBaseTypeDef {

    private List<AtlasEnumElementDef> elementDefs;
    private String defaultValue;

    /** 빈 enum 타입 정의를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasEnumDef() {
        super(TypeCategory.ENUM);
    }

    /**
     * 이름을 지정해 enum 타입 정의를 만듭니다.
     *
     * @param name 타입 이름
     */
    public AtlasEnumDef(String name) {
        super(TypeCategory.ENUM, name);
    }

    public List<AtlasEnumElementDef> getElementDefs() {
        return elementDefs;
    }

    public void setElementDefs(List<AtlasEnumElementDef> elementDefs) {
        this.elementDefs = elementDefs;
    }

    /**
     * 값 하나를 목록 끝에 추가합니다. ordinal은 목록에서의 위치(0부터 시작)로 정해지고 설명은 비워 둡니다.
     *
     * @param value 추가할 enum 값
     */
    public void addElement(String value) {
        if (elementDefs == null) {
            elementDefs = new ArrayList<>();
        }
        elementDefs.add(new AtlasEnumElementDef(value, null, elementDefs.size()));
    }

    /** 값이 지정되지 않았을 때 쓸 기본 enum 값을 돌려줍니다. 없으면 {@code null}입니다. */
    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /** enum 타입의 값 하나를 정의합니다. */
    public static class AtlasEnumElementDef extends AtlasModel {

        private String value;
        private String description;
        private Integer ordinal;

        /** 빈 값 정의를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
        public AtlasEnumElementDef() {
        }

        /**
         * 값 정의를 만듭니다.
         *
         * @param value       enum 값
         * @param description 값 설명, 없으면 {@code null}
         * @param ordinal     값의 순번
         */
        public AtlasEnumElementDef(String value, String description, Integer ordinal) {
            this.value = value;
            this.description = description;
            this.ordinal = ordinal;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Integer getOrdinal() {
            return ordinal;
        }

        public void setOrdinal(Integer ordinal) {
            this.ordinal = ordinal;
        }
    }
}
