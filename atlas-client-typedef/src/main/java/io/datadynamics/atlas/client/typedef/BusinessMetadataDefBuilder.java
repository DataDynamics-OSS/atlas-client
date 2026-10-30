package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBusinessMetadataDef;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder of business metadata types. Created with {@link TypeDefs#businessMetadata(String)}.
 * <pre>{@code
 * businessMetadata("data_quality")
 *     .applicableTo("hive_table", "hive_column")
 *     .attribute(attr("score", INT))
 *     .attribute(attr("steward", STRING).maxStringLength(100))
 * }</pre>
 * Attributes without their own {@code applicableEntityTypes} get the ones from
 * {@link #applicableTo(String...)}, and string attributes without {@code maxStrLength}
 * get {@value #DEFAULT_MAX_STRING_LENGTH} (Atlas rejects string attributes without it).
 */
public final class BusinessMetadataDefBuilder
        extends StructLikeDefBuilder<BusinessMetadataDefBuilder, AtlasBusinessMetadataDef> {

    public static final int DEFAULT_MAX_STRING_LENGTH = 50;

    private String applicableEntityTypes;

    BusinessMetadataDefBuilder(String name) {
        super(new AtlasBusinessMetadataDef(), name);
    }

    /** Default entity types for all attributes of this business metadata. */
    public BusinessMetadataDefBuilder applicableTo(String... entityTypes) {
        this.applicableEntityTypes = AtlasJson.toJson(Arrays.asList(entityTypes));
        return this;
    }

    @Override
    protected void beforeBuild(AtlasBusinessMetadataDef definition) {
        if (definition.getAttributeDefs() == null) {
            return;
        }
        for (AtlasAttributeDef attribute : definition.getAttributeDefs()) {
            Map<String, String> options = attribute.getOptions() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(attribute.getOptions());
            if (applicableEntityTypes != null) {
                options.putIfAbsent(AtlasBusinessMetadataDef.OPT_APPLICABLE_ENTITY_TYPES, applicableEntityTypes);
            }
            if (AttributeTypes.STRING.equals(attribute.getTypeName())) {
                options.putIfAbsent(AtlasBusinessMetadataDef.OPT_MAX_STRING_LENGTH,
                        Integer.toString(DEFAULT_MAX_STRING_LENGTH));
            }
            attribute.setOptions(options.isEmpty() ? null : options);
        }
    }
}
