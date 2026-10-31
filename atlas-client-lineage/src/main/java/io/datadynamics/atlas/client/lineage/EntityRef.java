package io.datadynamics.atlas.client.lineage;

import io.datadynamics.atlas.client.model.instance.AtlasObjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 데이터셋, 컬럼, 프로세스를 타입과 {@code qualifiedName}으로 가리키는 참조입니다.
 * <p>
 * {@code attributes}는 엔티티가 존재하지 않아 {@link MissingEntityPolicy#CREATE}가 새로 만들 때만 씁니다.
 * 그 타입의 필수 속성은 여기에 넣어 주십시오. 동일성 비교에는 {@link #key()}를 쓰십시오. record의
 * {@code equals}는 {@code attributes}까지 비교합니다.
 *
 * @param typeName      엔티티 타입 이름
 * @param qualifiedName 엔티티의 qualifiedName
 * @param attributes    엔티티를 새로 만들 때 쓸 속성. {@code null}이면 빈 맵으로 취급합니다.
 */
public record EntityRef(String typeName, String qualifiedName, Map<String, Object> attributes) {

    /**
     * 인자를 검증하고 속성 맵을 변경 불가능한 복사본으로 바꿉니다.
     *
     * @throws NullPointerException {@code typeName}이나 {@code qualifiedName}이 {@code null}인 경우
     */
    public EntityRef {
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 생성 속성 없이 참조를 만듭니다.
     *
     * @param typeName      엔티티 타입 이름
     * @param qualifiedName 엔티티의 qualifiedName
     * @return 새 참조
     */
    public static EntityRef of(String typeName, String qualifiedName) {
        return new EntityRef(typeName, qualifiedName, Map.of());
    }

    /**
     * 이 엔티티를 새로 만들어야 할 때 쓸 속성을 하나 더한 복사본을 돌려줍니다. 원래 참조는 바뀌지 않습니다.
     *
     * @param name  속성 이름
     * @param value 속성 값. {@link Map#copyOf(Map)}를 거치므로 {@code null}은 허용되지 않습니다.
     * @return 속성이 추가된 새 참조
     */
    public EntityRef withAttribute(String name, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(attributes);
        copy.put(name, value);
        return new EntityRef(typeName, qualifiedName, copy);
    }

    /**
     * typeName과 qualifiedName(uniqueAttributes)으로 엔티티를 가리키는 {@link AtlasObjectId}로 바꿉니다.
     *
     * @return 이 참조에 해당하는 객체 ID
     */
    public AtlasObjectId toObjectId() {
        return AtlasObjectId.byQualifiedName(typeName, qualifiedName);
    }

    /**
     * 생성 속성을 무시한, 참조 대상 엔티티의 식별 키({@code typeName|qualifiedName})를 돌려줍니다.
     *
     * @return 식별 키
     */
    public String key() {
        return typeName + "|" + qualifiedName;
    }

    /**
     * qualifiedName에서 만든 기본 표시 이름을 돌려줍니다. 클러스터 접미사({@code @prod})와
     * {@code db.table.} 같은 앞부분을 떼어 냅니다. 예를 들어 {@code mart.daily_sales@prod}는
     * {@code daily_sales}가 됩니다.
     *
     * @return 기본 표시 이름
     */
    public String defaultName() {
        String name = qualifiedName;
        // 맨 앞의 '@'(at == 0)는 접미사로 보지 않습니다. 그대로 자르면 이름이 비기 때문입니다.
        int at = name.lastIndexOf('@');
        if (at > 0) {
            name = name.substring(0, at);
        }
        // 마지막 '.' 뒤가 비어 있으면(이름이 '.'으로 끝나면) 자르지 않습니다.
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            name = name.substring(dot + 1);
        }
        return name;
    }

    /** {@code typeName:qualifiedName} 형식의 문자열을 돌려줍니다. */
    @Override
    public String toString() {
        return typeName + ":" + qualifiedName;
    }
}
