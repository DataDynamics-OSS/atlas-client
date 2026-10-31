package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBusinessMetadataDef;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * business metadata 타입을 만드는 빌더입니다. {@link TypeDefs#businessMetadata(String)}으로 생성합니다.
 * <pre>{@code
 * businessMetadata("data_quality")
 *     .applicableTo("hive_table", "hive_column")
 *     .attribute(attr("score", INT))
 *     .attribute(attr("steward", STRING).maxStringLength(100))
 * }</pre>
 * 자체 {@code applicableEntityTypes}가 없는 속성은 {@link #applicableTo(String...)}로 지정한 값을 받습니다.
 * {@code maxStrLength}가 없는 string 속성은 {@value #DEFAULT_MAX_STRING_LENGTH}를 받습니다
 * (Atlas는 이 옵션이 없는 string 속성을 거부합니다).
 * <p>
 * 빌더는 스레드 안전하지 않으므로 한 스레드에서만 사용해야 합니다.
 */
public final class BusinessMetadataDefBuilder
        extends StructLikeDefBuilder<BusinessMetadataDefBuilder, AtlasBusinessMetadataDef> {

    /** string 속성에 {@code maxStrLength}를 지정하지 않았을 때 적용하는 기본 최대 길이입니다. */
    public static final int DEFAULT_MAX_STRING_LENGTH = 50;

    /** {@link #applicableTo(String...)}로 받은 엔티티 타입 목록을 JSON 배열 문자열로 보관합니다. */
    private String applicableEntityTypes;

    /** 패키지 밖에서는 {@link TypeDefs#businessMetadata(String)}을 통해서만 만들 수 있습니다. */
    BusinessMetadataDefBuilder(String name) {
        super(new AtlasBusinessMetadataDef(), name);
    }

    /**
     * 이 business metadata의 모든 속성에 적용할 기본 엔티티 타입을 지정합니다.
     * 속성에 {@link AttributeDefBuilder#applicableEntityTypes(String...)}가 따로 있으면 그 값이 우선합니다.
     *
     * @param entityTypes 엔티티 타입 이름들
     * @return 이 빌더
     */
    public BusinessMetadataDefBuilder applicableTo(String... entityTypes) {
        this.applicableEntityTypes = AtlasJson.toJson(Arrays.asList(entityTypes));
        return this;
    }

    /**
     * 각 속성에 기본 옵션을 채웁니다. 이미 있는 옵션은 덮어쓰지 않습니다({@code putIfAbsent}).
     * 옵션이 하나도 없으면 빈 맵 대신 {@code null}로 두어 원래 정의와 같은 모양을 유지합니다.
     */
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
