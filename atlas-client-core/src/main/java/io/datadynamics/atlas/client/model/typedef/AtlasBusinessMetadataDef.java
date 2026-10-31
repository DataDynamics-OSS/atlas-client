package io.datadynamics.atlas.client.model.typedef;

/**
 * business metadata(네임스페이스) 타입 정의입니다.
 * <p>
 * 각 속성 정의({@link AtlasAttributeDef})에는 {@value #OPT_APPLICABLE_ENTITY_TYPES} 옵션을 넣어 이 속성을
 * 붙일 수 있는 엔티티 타입을 지정해야 합니다. 값은 JSON 배열 문자열이며 예를 들면 {@code "[\"hive_table\"]"}입니다.
 */
public class AtlasBusinessMetadataDef extends AtlasStructDef {

    /** 속성을 적용할 수 있는 엔티티 타입 목록을 지정하는 속성 옵션 키입니다. 값은 JSON 배열 문자열입니다. */
    public static final String OPT_APPLICABLE_ENTITY_TYPES = "applicableEntityTypes";
    /** 문자열 속성의 최대 길이를 지정하는 속성 옵션 키입니다. */
    public static final String OPT_MAX_STRING_LENGTH = "maxStrLength";

    /** 빈 정의를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasBusinessMetadataDef() {
        super(TypeCategory.BUSINESS_METADATA);
    }

    /**
     * 이름을 지정해 정의를 만듭니다.
     *
     * @param name business metadata 타입 이름
     */
    public AtlasBusinessMetadataDef(String name) {
        super(TypeCategory.BUSINESS_METADATA, name);
    }
}
