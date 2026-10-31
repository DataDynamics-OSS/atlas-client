package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;

/**
 * 타입 정의 DSL의 진입점입니다. 아래처럼 static import해서 씁니다.
 * <pre>{@code
 * import static io.datadynamics.atlas.client.typedef.TypeDefs.*;
 * import static io.datadynamics.atlas.client.typedef.AttributeTypes.*;
 *
 * AtlasTypesDef types = typesDef(
 *     enumType("table_tier").values("BRONZE", "SILVER", "GOLD"),
 *     entity("my_table").dataSet()
 *         .attribute(attr("tier", "table_tier"))
 *         .attribute(attr("rowCount", LONG)),
 *     entity("my_column").dataSet()
 *         .attribute(attr("dataType", STRING).required()),
 *     relationship("my_table_columns").composition()
 *         .end1(end("my_table", "columns").set().container())
 *         .end2(end("my_column", "table")),
 *     classification("PII").entityTypes("my_column"));
 * }</pre>
 * 각 팩토리 메서드는 새 빌더를 돌려주며, 빌더는 {@code build()} 시점에 구조 검사를 수행합니다.
 */
public final class TypeDefs {

    /** Atlas 기본 모델의 데이터셋 상위 타입 이름입니다. */
    public static final String DATA_SET = "DataSet";
    /** Atlas 기본 모델의 프로세스 상위 타입 이름입니다. */
    public static final String PROCESS = "Process";
    /** Atlas 기본 모델의 {@code Referenceable} 타입 이름입니다. */
    public static final String REFERENCEABLE = "Referenceable";
    /** Atlas 기본 모델의 {@code Asset} 타입 이름입니다. */
    public static final String ASSET = "Asset";

    /** 인스턴스를 만들지 않는 유틸리티 클래스이므로 생성자를 막아 둡니다. */
    private TypeDefs() {
    }

    /**
     * 엔티티 타입 빌더를 만듭니다.
     *
     * @param name 타입 이름
     * @return 새 {@link EntityDefBuilder}
     * @throws IllegalArgumentException 이름이 {@code null}이거나 비어 있을 때
     */
    public static EntityDefBuilder entity(String name) {
        return new EntityDefBuilder(name);
    }

    /**
     * classification(태그) 타입 빌더를 만듭니다.
     *
     * @param name 타입 이름
     * @return 새 {@link ClassificationDefBuilder}
     * @throws IllegalArgumentException 이름이 {@code null}이거나 비어 있을 때
     */
    public static ClassificationDefBuilder classification(String name) {
        return new ClassificationDefBuilder(name);
    }

    /**
     * struct 타입 빌더를 만듭니다.
     *
     * @param name 타입 이름
     * @return 새 {@link StructDefBuilder}
     * @throws IllegalArgumentException 이름이 {@code null}이거나 비어 있을 때
     */
    public static StructDefBuilder struct(String name) {
        return new StructDefBuilder(name);
    }

    /**
     * enum 타입 빌더를 만듭니다. {@code enum}은 Java 예약어이므로 메서드 이름이 {@code enumType}입니다.
     *
     * @param name 타입 이름
     * @return 새 {@link EnumDefBuilder}
     * @throws IllegalArgumentException 이름이 {@code null}이거나 비어 있을 때
     */
    public static EnumDefBuilder enumType(String name) {
        return new EnumDefBuilder(name);
    }

    /**
     * relationship 타입 빌더를 만듭니다.
     *
     * @param name 타입 이름
     * @return 새 {@link RelationshipDefBuilder}
     * @throws IllegalArgumentException 이름이 {@code null}이거나 비어 있을 때
     */
    public static RelationshipDefBuilder relationship(String name) {
        return new RelationshipDefBuilder(name);
    }

    /**
     * business metadata 타입 빌더를 만듭니다.
     *
     * @param name 타입 이름
     * @return 새 {@link BusinessMetadataDefBuilder}
     * @throws IllegalArgumentException 이름이 {@code null}이거나 비어 있을 때
     */
    public static BusinessMetadataDefBuilder businessMetadata(String name) {
        return new BusinessMetadataDefBuilder(name);
    }

    /**
     * 선택(optional) 단일 값 속성 빌더를 만듭니다. {@code .required()}, {@code .indexable()} 등으로 다듬습니다.
     *
     * @param name     속성 이름
     * @param typeName 속성 타입 이름(예: {@link AttributeTypes#STRING}, {@code AttributeTypes.array("my_column")})
     * @return 새 {@link AttributeDefBuilder}
     * @throws IllegalArgumentException 이름이나 타입 이름이 {@code null}이거나 비어 있을 때
     */
    public static AttributeDefBuilder attr(String name, String typeName) {
        return new AttributeDefBuilder(name, typeName);
    }

    /**
     * relationship 끝점 빌더를 만듭니다. {@code entityType} 타입의 엔티티가 {@code attributeName}이라는
     * relationship 속성을 갖게 됩니다.
     *
     * @param entityType    끝점의 엔티티 타입 이름
     * @param attributeName 그 엔티티 타입에 생기는 relationship 속성 이름
     * @return 새 {@link RelationshipEndBuilder}
     * @throws IllegalArgumentException 둘 중 하나라도 {@code null}이거나 비어 있을 때
     */
    public static RelationshipEndBuilder end(String entityType, String attributeName) {
        return new RelationshipEndBuilder(entityType, attributeName);
    }

    /**
     * 모든 빌더를 {@code build()}하여 결과를 하나의 {@link AtlasTypesDef}로 묶습니다.
     *
     * @param builders 타입 정의 빌더들
     * @return 빌드된 정의를 담은 새 {@link AtlasTypesDef}
     * @throws IllegalStateException 어느 정의가 구조적으로 잘못되었을 때({@link TypeDefBuilder#build()} 참고)
     */
    public static AtlasTypesDef typesDef(TypeDefBuilder<?, ?>... builders) {
        AtlasTypesDef typesDef = new AtlasTypesDef();
        for (TypeDefBuilder<?, ?> builder : builders) {
            typesDef.add(builder.build());
        }
        return typesDef;
    }

    /**
     * 이미 만들어진 타입 정의들을 하나의 {@link AtlasTypesDef}로 묶습니다. 이 메서드는 검증하지 않습니다.
     *
     * @param defs 타입 정의들
     * @return 정의를 담은 {@link AtlasTypesDef}
     */
    public static AtlasTypesDef typesDef(AtlasBaseTypeDef... defs) {
        return AtlasTypesDef.of(defs);
    }
}
