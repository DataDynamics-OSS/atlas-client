package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;

/**
 * Entry point of the type definition DSL. Import statically:
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
 */
public final class TypeDefs {

    public static final String DATA_SET = "DataSet";
    public static final String PROCESS = "Process";
    public static final String REFERENCEABLE = "Referenceable";
    public static final String ASSET = "Asset";

    private TypeDefs() {
    }

    public static EntityDefBuilder entity(String name) {
        return new EntityDefBuilder(name);
    }

    public static ClassificationDefBuilder classification(String name) {
        return new ClassificationDefBuilder(name);
    }

    public static StructDefBuilder struct(String name) {
        return new StructDefBuilder(name);
    }

    public static EnumDefBuilder enumType(String name) {
        return new EnumDefBuilder(name);
    }

    public static RelationshipDefBuilder relationship(String name) {
        return new RelationshipDefBuilder(name);
    }

    public static BusinessMetadataDefBuilder businessMetadata(String name) {
        return new BusinessMetadataDefBuilder(name);
    }

    /** An optional, single-valued attribute; refine with {@code .required()}, {@code .indexable()}, ... */
    public static AttributeDefBuilder attr(String name, String typeName) {
        return new AttributeDefBuilder(name, typeName);
    }

    /** A relationship end: entities of {@code entityType} get relationship attribute {@code attributeName}. */
    public static RelationshipEndBuilder end(String entityType, String attributeName) {
        return new RelationshipEndBuilder(entityType, attributeName);
    }

    /** Builds every builder and groups the results. */
    public static AtlasTypesDef typesDef(TypeDefBuilder<?, ?>... builders) {
        AtlasTypesDef typesDef = new AtlasTypesDef();
        for (TypeDefBuilder<?, ?> builder : builders) {
            typesDef.add(builder.build());
        }
        return typesDef;
    }

    public static AtlasTypesDef typesDef(AtlasBaseTypeDef... defs) {
        return AtlasTypesDef.of(defs);
    }
}
