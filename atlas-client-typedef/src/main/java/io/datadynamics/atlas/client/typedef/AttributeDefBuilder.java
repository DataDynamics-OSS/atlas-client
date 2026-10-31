package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBusinessMetadataDef;
import io.datadynamics.atlas.client.model.typedef.AtlasConstraintDef;
import io.datadynamics.atlas.client.model.typedef.Cardinality;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 속성 하나를 만드는 빌더입니다. {@link TypeDefs#attr(String, String)}으로 생성합니다.
 * <p>
 * 속성은 <b>기본적으로 선택(optional)</b>입니다. Atlas 원래 모델에서는 {@code isOptional}의 기본값이
 * {@code false}인 것과 다릅니다. 대부분의 메타데이터 속성은 선택 속성이고, 이미 존재하는 타입에 속성을
 * 추가할 때 Atlas는 선택 속성만 받아들이기 때문입니다. cardinality의 기본값은 {@code SINGLE}입니다.
 * <p>
 * 빌더는 스레드 안전하지 않으므로 한 스레드에서만 사용해야 합니다.
 */
public final class AttributeDefBuilder {

    private final AtlasAttributeDef def;

    /**
     * 패키지 밖에서는 {@link TypeDefs#attr(String, String)}을 통해서만 만들 수 있습니다.
     *
     * @throws IllegalArgumentException 이름이나 타입 이름이 {@code null}이거나 비어 있을 때
     */
    AttributeDefBuilder(String name, String typeName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Attribute name must not be blank");
        }
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("Type of attribute " + name + " must not be blank");
        }
        def = new AtlasAttributeDef(name, typeName);
        def.setOptional(true);
        def.setCardinality(Cardinality.SINGLE);
    }

    /**
     * 속성을 필수로 만듭니다. 타입을 처음 생성할 때만 가능합니다(기존 타입에 필수 속성을 추가하면 Atlas가 거부합니다).
     *
     * @return 이 빌더
     */
    public AttributeDefBuilder required() {
        def.setOptional(false);
        return this;
    }

    /**
     * 속성을 선택 속성으로 만듭니다. 기본값이므로 {@link #required()}를 되돌릴 때만 필요합니다.
     *
     * @return 이 빌더
     */
    public AttributeDefBuilder optional() {
        def.setOptional(true);
        return this;
    }

    /**
     * 같은 타입의 모든 엔티티에서 값이 유일해야 함을 표시합니다(Atlas는 unique 속성을 인덱싱합니다).
     *
     * @return 이 빌더
     */
    public AttributeDefBuilder unique() {
        def.setUnique(true);
        return this;
    }

    /**
     * 속성을 인덱싱하여 basic search에서 이 속성으로 거르는 검색이 빨라지게 합니다.
     *
     * @return 이 빌더
     */
    public AttributeDefBuilder indexable() {
        def.setIndexable(true);
        return this;
    }

    /**
     * 문자열 인덱스를 사용하도록 지정합니다. 문자열 속성에서 정확히 일치하는 검색과 정렬이 가능해집니다.
     *
     * @return 이 빌더
     */
    public AttributeDefBuilder stringIndex() {
        def.setIndexType(AtlasAttributeDef.IndexType.STRING);
        return this;
    }

    /**
     * 속성 값의 cardinality(단일 값, 목록, 집합)를 지정합니다.
     *
     * @param cardinality 사용할 cardinality
     * @return 이 빌더
     */
    public AttributeDefBuilder cardinality(Cardinality cardinality) {
        def.setCardinality(cardinality);
        return this;
    }

    /**
     * {@code cardinality(Cardinality.LIST)}의 줄임입니다. 순서가 있고 중복을 허용하는 여러 값을 담습니다.
     *
     * @return 이 빌더
     */
    public AttributeDefBuilder list() {
        return cardinality(Cardinality.LIST);
    }

    /**
     * {@code cardinality(Cardinality.SET)}의 줄임입니다. 중복 없는 여러 값을 담습니다.
     *
     * @return 이 빌더
     */
    public AttributeDefBuilder set() {
        return cardinality(Cardinality.SET);
    }

    /**
     * 속성 설명을 지정합니다.
     *
     * @param description 설명 문구
     * @return 이 빌더
     */
    public AttributeDefBuilder description(String description) {
        def.setDescription(description);
        return this;
    }

    /**
     * Atlas UI 등에 표시할 이름을 지정합니다.
     *
     * @param displayName 표시 이름
     * @return 이 빌더
     */
    public AttributeDefBuilder displayName(String displayName) {
        def.setDisplayName(displayName);
        return this;
    }

    /**
     * 속성의 기본값을 지정합니다. Atlas 모델은 기본값을 문자열로 저장하므로 {@code toString()} 결과를 씁니다.
     *
     * @param defaultValue 기본값. {@code null}이면 기본값을 지웁니다
     * @return 이 빌더
     */
    public AttributeDefBuilder defaultValue(Object defaultValue) {
        def.setDefaultValue(defaultValue == null ? null : defaultValue.toString());
        return this;
    }

    /**
     * 자유 텍스트 검색 가중치(1~10)를 지정합니다. 값이 클수록 이 속성에서 일치한 결과가 앞에 옵니다.
     *
     * @param searchWeight 1 이상 10 이하의 가중치
     * @return 이 빌더
     * @throws IllegalArgumentException 범위를 벗어난 값일 때
     */
    public AttributeDefBuilder searchWeight(int searchWeight) {
        if (searchWeight < 1 || searchWeight > 10) {
            throw new IllegalArgumentException("searchWeight must be between 1 and 10");
        }
        def.setSearchWeight(searchWeight);
        return this;
    }

    /**
     * 엔티티 변경 알림 메시지에 이 속성을 포함할지 지정합니다.
     *
     * @param include 포함하려면 {@code true}
     * @return 이 빌더
     */
    public AttributeDefBuilder includeInNotification(boolean include) {
        def.setIncludeInNotification(include);
        return this;
    }

    /**
     * 여러 값을 담는 속성의 최소/최대 값 개수를 지정합니다.
     *
     * @param min 최소 개수
     * @param max 최대 개수
     * @return 이 빌더
     */
    public AttributeDefBuilder valuesCount(int min, int max) {
        def.setValuesMinCount(min);
        def.setValuesMaxCount(max);
        return this;
    }

    /**
     * 속성 옵션을 하나 추가합니다. 같은 키가 이미 있으면 값을 덮어씁니다. 옵션은 추가한 순서를 유지합니다.
     *
     * @param key   옵션 키
     * @param value 옵션 값
     * @return 이 빌더
     */
    public AttributeDefBuilder option(String key, String value) {
        Map<String, String> options = def.getOptions() == null ? new LinkedHashMap<>() : def.getOptions();
        options.put(key, value);
        def.setOptions(options);
        return this;
    }

    /**
     * business metadata 전용입니다. 이 속성을 설정할 수 있는 엔티티 타입을 지정합니다.
     * 값은 JSON 배열 문자열로 applicableEntityTypes 옵션에 저장됩니다.
     *
     * @param entityTypes 엔티티 타입 이름들
     * @return 이 빌더
     */
    public AttributeDefBuilder applicableEntityTypes(String... entityTypes) {
        return option(AtlasBusinessMetadataDef.OPT_APPLICABLE_ENTITY_TYPES,
                AtlasJson.toJson(Arrays.asList(entityTypes)));
    }

    /**
     * business metadata 전용입니다. 문자열 값의 최대 길이(maxStrLength 옵션)를 지정합니다.
     *
     * @param maxLength 최대 길이
     * @return 이 빌더
     */
    public AttributeDefBuilder maxStringLength(int maxLength) {
        return option(AtlasBusinessMetadataDef.OPT_MAX_STRING_LENGTH, Integer.toString(maxLength));
    }

    /**
     * relationship 도입 이전의 레거시 모델링 방식입니다. 참조되는 엔티티를 이 엔티티가 소유한다고 표시합니다
     * ({@code ownedRef} 제약 조건). 새 모델에서는 composition 범주의 {@link TypeDefs#relationship(String)}을 쓰십시오.
     *
     * @return 이 빌더
     */
    public AttributeDefBuilder ownedRef() {
        def.addConstraint(new AtlasConstraintDef(AtlasConstraintDef.CONSTRAINT_TYPE_OWNED_REF, null));
        return this;
    }

    /**
     * relationship 도입 이전의 레거시 모델링 방식입니다. 이 속성이 참조되는 타입의 지정된 속성과
     * 서로 반대 방향 참조임을 표시합니다({@code inverseRef} 제약 조건).
     *
     * @param attributeName 참조되는 타입 쪽의 반대 방향 속성 이름
     * @return 이 빌더
     */
    public AttributeDefBuilder inverseRef(String attributeName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(AtlasConstraintDef.CONSTRAINT_PARAM_ATTRIBUTE, attributeName);
        def.addConstraint(new AtlasConstraintDef(AtlasConstraintDef.CONSTRAINT_TYPE_INVERSE_REF, params));
        return this;
    }

    /**
     * 속성 이름을 돌려줍니다.
     *
     * @return 속성 이름
     */
    public String getName() {
        return def.getName();
    }

    /**
     * 지금까지 설정한 내용으로 속성 정의를 만듭니다. 깊은 복사본을 돌려주므로 이후 빌더를 바꿔도
     * 이미 만든 정의에는 영향이 없고, 빌더를 계속 재사용할 수 있습니다.
     *
     * @return 새 속성 정의
     */
    public AtlasAttributeDef build() {
        return AtlasJson.deepCopy(def);
    }
}
