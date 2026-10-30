package io.datadynamics.atlas.client.model.typedef;

/**
 * Business metadata (namespace) type definition. Each attribute def should carry the
 * {@value #OPT_APPLICABLE_ENTITY_TYPES} option, e.g. {@code "[\"hive_table\"]"}.
 */
public class AtlasBusinessMetadataDef extends AtlasStructDef {

    public static final String OPT_APPLICABLE_ENTITY_TYPES = "applicableEntityTypes";
    public static final String OPT_MAX_STRING_LENGTH = "maxStrLength";

    public AtlasBusinessMetadataDef() {
        super(TypeCategory.BUSINESS_METADATA);
    }

    public AtlasBusinessMetadataDef(String name) {
        super(TypeCategory.BUSINESS_METADATA, name);
    }
}
