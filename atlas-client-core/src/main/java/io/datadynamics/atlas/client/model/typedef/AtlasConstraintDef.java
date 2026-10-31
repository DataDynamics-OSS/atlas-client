package io.datadynamics.atlas.client.model.typedef;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.Map;

/**
 * {@code ownedRef}, {@code inverseRef} 같은 속성 제약 조건입니다.
 * <p>
 * relationship 타입이 도입되기 전, 예전 방식으로 엔티티 사이의 관계를 표현할 때 쓰던 구조입니다.
 */
public class AtlasConstraintDef extends AtlasModel {

    /** 참조된 엔티티를 이 엔티티가 소유함을 나타내는 제약 종류입니다. */
    public static final String CONSTRAINT_TYPE_OWNED_REF = "ownedRef";
    /** 상대 타입의 속성과 역방향 참조 관계임을 나타내는 제약 종류입니다. */
    public static final String CONSTRAINT_TYPE_INVERSE_REF = "inverseRef";
    /** {@code inverseRef} 제약에서 상대 쪽 속성 이름을 담는 파라미터 키입니다. */
    public static final String CONSTRAINT_PARAM_ATTRIBUTE = "attribute";

    private String type;
    private Map<String, Object> params;

    /** 빈 제약 조건을 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasConstraintDef() {
    }

    /**
     * 종류와 파라미터를 지정해 제약 조건을 만듭니다.
     *
     * @param type   제약 종류(예: {@link #CONSTRAINT_TYPE_INVERSE_REF})
     * @param params 제약 파라미터(예: {@link #CONSTRAINT_PARAM_ATTRIBUTE} 키에 상대 속성 이름), 없으면 {@code null}
     */
    public AtlasConstraintDef(String type, Map<String, Object> params) {
        this.type = type;
        this.params = params;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
