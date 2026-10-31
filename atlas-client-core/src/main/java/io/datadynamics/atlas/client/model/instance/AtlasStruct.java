package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * struct 타입의 인스턴스입니다. 타입 이름과 속성 값들로 이루어집니다.
 * <p>
 * 엔티티({@link AtlasEntity}), classification({@link AtlasClassification}) 인스턴스의 부모 클래스이기도 합니다.
 * 속성 맵은 처음 값을 넣을 때 만들어지므로, 아무 속성도 넣지 않았다면 {@link #getAttributes()}가
 * {@code null}을 돌려줄 수 있습니다.
 */
public class AtlasStruct extends AtlasModel {

    private String typeName;
    private Map<String, Object> attributes;

    /** 빈 인스턴스를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasStruct() {
    }

    /**
     * 타입 이름만 지정해 인스턴스를 만듭니다.
     *
     * @param typeName 타입 이름
     */
    public AtlasStruct(String typeName) {
        this.typeName = typeName;
    }

    /**
     * 타입 이름과 속성 값으로 인스턴스를 만듭니다. 넘긴 맵은 복사해서 보관하므로, 이후 원본 맵을 바꿔도
     * 이 객체에는 영향이 없습니다.
     *
     * @param typeName   타입 이름
     * @param attributes 속성 값, {@code null}이면 속성 없이 만듭니다
     */
    public AtlasStruct(String typeName, Map<String, Object> attributes) {
        this.typeName = typeName;
        this.attributes = attributes == null ? null : new LinkedHashMap<>(attributes);
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * 속성 값 하나를 돌려줍니다.
     *
     * @param name 속성 이름
     * @return 속성 값, 속성이 없거나 값이 {@code null}이면 {@code null}
     */
    public Object getAttribute(String name) {
        return attributes == null ? null : attributes.get(name);
    }

    /**
     * 속성이 맵에 들어 있는지 확인합니다. 값이 {@code null}로 들어 있어도 {@code true}입니다.
     *
     * @param name 속성 이름
     * @return 속성 키가 있으면 {@code true}
     */
    public boolean hasAttribute(String name) {
        return attributes != null && attributes.containsKey(name);
    }

    /**
     * 속성 값 하나를 설정합니다. 속성 맵이 없으면 새로 만듭니다.
     *
     * @param name  속성 이름
     * @param value 속성 값
     */
    public void setAttribute(String name, Object value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(name, value);
    }

    /**
     * 속성을 맵에서 제거합니다. 속성이 없으면 아무 일도 하지 않습니다.
     *
     * @param name 제거할 속성 이름
     */
    public void removeAttribute(String name) {
        if (attributes != null) {
            attributes.remove(name);
        }
    }
}
