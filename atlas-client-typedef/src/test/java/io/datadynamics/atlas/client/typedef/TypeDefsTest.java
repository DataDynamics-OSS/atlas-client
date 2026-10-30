package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBusinessMetadataDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEnumDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.PropagateTags;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.RelationshipCategory;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.model.typedef.Cardinality;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.datadynamics.atlas.client.typedef.AttributeTypes.INT;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.LONG;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.STRING;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.array;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.map;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.businessMetadata;
import static io.datadynamics.atlas.client.typedef.TypeDefs.classification;
import static io.datadynamics.atlas.client.typedef.TypeDefs.end;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static io.datadynamics.atlas.client.typedef.TypeDefs.enumType;
import static io.datadynamics.atlas.client.typedef.TypeDefs.relationship;
import static io.datadynamics.atlas.client.typedef.TypeDefs.typesDef;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeDefsTest {

    @Test
    void entityWithAttributes() {
        AtlasEntityDef def = entity("my_table").dataSet()
                .description("A table")
                .serviceType("my_company")
                .attribute(attr("rowCount", LONG).indexable())
                .attribute(attr("tableType", STRING).required().defaultValue("MANAGED"))
                .attribute("comment", STRING)
                .build();

        assertThat(def.getSuperTypes()).containsExactly("DataSet");
        assertThat(def.getServiceType()).isEqualTo("my_company");
        AtlasAttributeDef rowCount = def.findAttributeDef("rowCount").orElseThrow();
        assertThat(rowCount.getOptional()).isTrue();
        assertThat(rowCount.getIndexable()).isTrue();
        assertThat(rowCount.getCardinality()).isEqualTo(Cardinality.SINGLE);
        AtlasAttributeDef tableType = def.findAttributeDef("tableType").orElseThrow();
        assertThat(tableType.getOptional()).isFalse();
        assertThat(tableType.getDefaultValue()).isEqualTo("MANAGED");

        assertThat(AtlasJson.toJson(def))
                .contains("\"category\":\"ENTITY\"")
                .contains("\"isOptional\":true")
                .contains("\"isIndexable\":true");
    }

    @Test
    void buildReturnsIndependentCopies() {
        EntityDefBuilder builder = entity("t").dataSet();
        AtlasEntityDef first = builder.build();
        builder.attribute("x", INT);
        AtlasEntityDef second = builder.build();

        assertThat(first.getAttributeDefs()).isNull();
        assertThat(second.getAttributeDefs()).hasSize(1);
    }

    @Test
    void compositionRelationship() {
        AtlasRelationshipDef def = relationship("my_table_columns")
                .composition()
                .end1(end("my_table", "columns").set().container())
                .end2(end("my_column", "table"))
                .propagateTags(PropagateTags.ONE_TO_TWO)
                .build();

        assertThat(def.getRelationshipCategory()).isEqualTo(RelationshipCategory.COMPOSITION);
        assertThat(def.getEndDef1().getContainer()).isTrue();
        assertThat(def.getEndDef1().getCardinality()).isEqualTo(Cardinality.SET);
        assertThat(def.getEndDef2().getCardinality()).isEqualTo(Cardinality.SINGLE);
        assertThat(AtlasJson.toJson(def)).contains("\"isContainer\":true").contains("\"propagateTags\":\"ONE_TO_TWO\"");
    }

    @Test
    void relationshipRulesAreCheckedOnBuild() {
        assertThatThrownBy(() -> relationship("r").composition()
                .end1(end("a", "bs").set()).end2(end("b", "a")).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPOSITION needs one end marked as container()");

        assertThatThrownBy(() -> relationship("r")
                .end1(end("a", "bs").set().container()).end2(end("b", "a")).build())
                .hasMessageContaining("ASSOCIATION cannot have a container end");

        assertThatThrownBy(() -> relationship("r").end1(end("a", "bs")).build())
                .hasMessageContaining("needs both endDef1 and endDef2");
    }

    @Test
    void enumWithOrdinalsAndDefault() {
        AtlasEnumDef def = enumType("tier").values("BRONZE", "SILVER").value("GOLD", "best").defaultValue("BRONZE").build();

        assertThat(def.getElementDefs()).extracting(AtlasEnumDef.AtlasEnumElementDef::getOrdinal).containsExactly(0, 1, 2);
        assertThat(def.getElementDefs().get(2).getDescription()).isEqualTo("best");

        assertThatThrownBy(() -> enumType("tier").values("A").defaultValue("B").build())
                .hasMessageContaining("default value 'B' is not an enum value");
        assertThatThrownBy(() -> enumType("tier").build()).hasMessageContaining("enum has no values");
    }

    @Test
    void businessMetadataGetsAtlasRequiredOptions() {
        AtlasBusinessMetadataDef def = businessMetadata("data_quality")
                .applicableTo("hive_table", "hive_column")
                .attribute(attr("score", INT))
                .attribute(attr("steward", STRING))
                .attribute(attr("note", STRING).maxStringLength(500).applicableEntityTypes("hive_db"))
                .build();

        AtlasAttributeDef score = def.findAttributeDef("score").orElseThrow();
        assertThat(score.getOptions()).containsEntry("applicableEntityTypes", "[\"hive_table\",\"hive_column\"]")
                .doesNotContainKey("maxStrLength");
        assertThat(def.findAttributeDef("steward").orElseThrow().getOptions()).containsEntry("maxStrLength", "50");
        assertThat(def.findAttributeDef("note").orElseThrow().getOptions())
                .containsEntry("maxStrLength", "500")
                .containsEntry("applicableEntityTypes", "[\"hive_db\"]");

        assertThatThrownBy(() -> businessMetadata("bm").attribute(attr("x", INT)).build())
                .hasMessageContaining("has no applicableEntityTypes");
    }

    @Test
    void duplicateAttributesAreRejected() {
        assertThatThrownBy(() -> entity("t").attribute("x", INT).attribute("x", STRING).build())
                .hasMessageContaining("attribute 'x' defined more than once");
    }

    @Test
    void typesDefGroupsByCategory() {
        AtlasTypesDef types = typesDef(
                enumType("tier").values("A"),
                entity("t").dataSet().attribute("tier", "tier"),
                classification("PII").entityTypes("t"));

        assertThat(types.getEnumDefs()).hasSize(1);
        assertThat(types.getEntityDefs()).hasSize(1);
        assertThat(types.getClassificationDefs().get(0).getEntityTypes()).isEqualTo(Set.of("t"));
    }

    @Test
    void containerTypeNames() {
        assertThat(array(STRING)).isEqualTo("array<string>");
        assertThat(map(STRING, array("my_column"))).isEqualTo("map<string,array<my_column>>");
        assertThat(AttributeTypes.referencedTypeNames("map<string,array<my_column>>"))
                .containsExactly("string", "my_column");
        assertThat(AttributeTypes.referencedTypeNames("map<map<string,int>,my_struct>"))
                .containsExactly("string", "int", "my_struct");
    }
}
