package io.datadynamics.atlas.client.model.typedef;

/**
 * Category of an Atlas type, as reported in the {@code category} field of a typedef.
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
