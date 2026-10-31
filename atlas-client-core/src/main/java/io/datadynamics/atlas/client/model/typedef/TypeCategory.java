package io.datadynamics.atlas.client.model.typedef;

/**
 * Atlas 타입의 분류입니다. typedef의 {@code category} 필드에 담겨 오는 값과 같습니다.
 * <p>
 * 예를 들어 {@link io.datadynamics.atlas.client.api.TypesApi#findByName(String)}는 응답의 이 값을 보고
 * 어떤 {@link AtlasBaseTypeDef} 하위 클래스로 역직렬화할지 정합니다.
 */
public enum TypeCategory {
    PRIMITIVE,
    OBJECT_ID_TYPE,
    ENUM,
    STRUCT,
    CLASSIFICATION,
    ENTITY,
    ARRAY,
    MAP,
    RELATIONSHIP,
    BUSINESS_METADATA
}
