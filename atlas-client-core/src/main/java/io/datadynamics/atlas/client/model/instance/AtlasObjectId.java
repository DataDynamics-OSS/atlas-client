package io.datadynamics.atlas.client.model.instance;

import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 엔티티를 가리키는 참조입니다. {@code guid}로 가리키거나, {@code typeName}과 uniqueAttributes로 가리킵니다.
 * <p>
 * guid를 모르는 엔티티를 가리킬 때는 {@link #byQualifiedName(String, String)}를 씁니다. 대부분의 리니지나
 * relationship 요청 본문은 이 방법으로 만드는 것이 가장 쉽습니다.
 */
public class AtlasObjectId extends AtlasModel {

    /** 대부분의 Atlas 엔티티 타입이 유일 속성으로 쓰는 {@code qualifiedName} 속성 이름입니다. */
    public static final String QUALIFIED_NAME = "qualifiedName";

    private String guid;
    private String typeName;
    private Map<String, Object> uniqueAttributes;

    /** 빈 참조를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasObjectId() {
    }

    /**
     * guid로 엔티티를 가리키는 참조를 만듭니다.
     *
     * @param guid     엔티티 guid
     * @param typeName 엔티티 타입 이름, 모르면 {@code null}
     */
    public AtlasObjectId(String guid, String typeName) {
        this.guid = guid;
        this.typeName = typeName;
    }

    /**
     * 타입 이름과 uniqueAttributes로 엔티티를 가리키는 참조를 만듭니다. 맵은 복사해서 보관합니다.
     *
     * @param typeName         엔티티 타입 이름
     * @param uniqueAttributes 유일 속성 이름과 값(예: {@code qualifiedName → "db.table@cluster"})
     */
    public AtlasObjectId(String typeName, Map<String, Object> uniqueAttributes) {
        this.typeName = typeName;
        this.uniqueAttributes = uniqueAttributes == null ? null : new LinkedHashMap<>(uniqueAttributes);
    }

    /**
     * 타입 이름 없이 guid만으로 참조를 만듭니다.
     *
     * @param guid 엔티티 guid
     * @return 새 참조
     * @throws NullPointerException {@code guid}가 {@code null}일 때
     */
    public static AtlasObjectId byGuid(String guid) {
        return new AtlasObjectId(Objects.requireNonNull(guid, "guid"), (String) null);
    }

    /**
     * guid와 타입 이름으로 참조를 만듭니다.
     *
     * @param guid     엔티티 guid
     * @param typeName 엔티티 타입 이름, {@code null}이어도 됩니다
     * @return 새 참조
     * @throws NullPointerException {@code guid}가 {@code null}일 때
     */
    public static AtlasObjectId byGuid(String guid, String typeName) {
        return new AtlasObjectId(Objects.requireNonNull(guid, "guid"), typeName);
    }

    /**
     * 유일 속성 하나로 엔티티를 가리키는 참조를 만듭니다.
     *
     * @param typeName      엔티티 타입 이름
     * @param attributeName 유일 속성 이름
     * @param value         유일 속성 값
     * @return 새 참조
     * @throws NullPointerException {@code typeName}이 {@code null}일 때
     */
    public static AtlasObjectId byUniqueAttribute(String typeName, String attributeName, Object value) {
        Objects.requireNonNull(typeName, "typeName");
        Map<String, Object> unique = new LinkedHashMap<>();
        unique.put(attributeName, value);
        return new AtlasObjectId(typeName, unique);
    }

    /**
     * {@code qualifiedName}으로 엔티티를 가리키는 참조를 만듭니다. guid를 모를 때 가장 흔히 쓰는 방법입니다.
     *
     * @param typeName      엔티티 타입 이름(예: {@code hive_table})
     * @param qualifiedName 엔티티의 qualifiedName
     * @return 새 참조
     * @throws NullPointerException {@code typeName}이나 {@code qualifiedName}이 {@code null}일 때
     */
    public static AtlasObjectId byQualifiedName(String typeName, String qualifiedName) {
        return byUniqueAttribute(typeName, QUALIFIED_NAME, Objects.requireNonNull(qualifiedName, "qualifiedName"));
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Map<String, Object> getUniqueAttributes() {
        return uniqueAttributes;
    }

    public void setUniqueAttributes(Map<String, Object> uniqueAttributes) {
        this.uniqueAttributes = uniqueAttributes;
    }

    /**
     * guid가 Atlas가 부여한 실제 guid이면 {@code true}를 돌려줍니다. 일괄 요청에서 쓰는 임시(placeholder)
     * guid는 음수 형태({@code "-"}로 시작)이므로 {@code false}가 됩니다. guid가 없어도 {@code false}입니다.
     */
    public boolean hasAssignedGuid() {
        return guid != null && !guid.startsWith("-");
    }

    /** 이 참조가 {@code qualifiedName} 유일 속성을 쓰면 그 값을 문자열로 돌려주고, 아니면 {@code null}을 돌려줍니다. */
    public String qualifiedName() {
        Object value = uniqueAttributes == null ? null : uniqueAttributes.get(QUALIFIED_NAME);
        return value == null ? null : value.toString();
    }
}
