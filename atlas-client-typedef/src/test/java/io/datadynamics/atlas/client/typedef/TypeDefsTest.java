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

/**
 * 타입 정의 DSL({@link TypeDefs}와 각 빌더)이 올바른 Atlas 정의를 만들고, 잘못된 정의를 {@code build()}에서
 * 거부하는지 검증합니다. Atlas 서버 없이 실행되는 단위 테스트입니다.
 */
class TypeDefsTest {

    /**
     * 엔티티 빌더로 상위 타입, serviceType, 여러 속성을 지정하면 정의에 그대로 반영되고,
     * 속성은 기본적으로 선택·SINGLE이며 {@code required()}와 기본값이 적용되는지, JSON 직렬화 결과에
     * Atlas 필드 이름({@code isOptional}, {@code isIndexable})이 쓰이는지 확인합니다.
     */
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

    /**
     * {@code build()}가 매번 독립된 복사본을 돌려주는지 확인합니다. 첫 빌드 뒤에 속성을 추가해도
     * 이미 만든 정의에는 영향이 없어야 합니다.
     */
    @Test
    void buildReturnsIndependentCopies() {
        EntityDefBuilder builder = entity("t").dataSet();
        AtlasEntityDef first = builder.build();
        builder.attribute("x", INT);
        AtlasEntityDef second = builder.build();

        assertThat(first.getAttributeDefs()).isNull();
        assertThat(second.getAttributeDefs()).hasSize(1);
    }

    /**
     * composition relationship을 만들면 범주, container 끝점, 끝점별 cardinality, propagateTags가
     * 정의와 JSON에 올바르게 들어가는지 확인합니다.
     */
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

    /**
     * relationship 규칙 위반이 {@code build()}에서 {@link IllegalStateException}으로 거부되는지 확인합니다.
     * container 없는 COMPOSITION, container가 있는 ASSOCIATION, 끝점이 하나뿐인 경우를 차례로 시험합니다.
     */
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

    /**
     * enum 값의 ordinal이 선언 순서(0, 1, 2)를 따르고 값별 설명이 붙는지 확인합니다.
     * 목록에 없는 기본값과 값이 없는 enum은 오류여야 합니다.
     */
    @Test
    void enumWithOrdinalsAndDefault() {
        AtlasEnumDef def = enumType("tier").values("BRONZE", "SILVER").value("GOLD", "best").defaultValue("BRONZE").build();

        assertThat(def.getElementDefs()).extracting(AtlasEnumDef.AtlasEnumElementDef::getOrdinal).containsExactly(0, 1, 2);
        assertThat(def.getElementDefs().get(2).getDescription()).isEqualTo("best");

        assertThatThrownBy(() -> enumType("tier").values("A").defaultValue("B").build())
                .hasMessageContaining("default value 'B' is not an enum value");
        assertThatThrownBy(() -> enumType("tier").build()).hasMessageContaining("enum has no values");
    }

    /**
     * business metadata 빌더가 Atlas 필수 옵션을 채우는지 확인합니다. {@code applicableTo}의 값이 속성 기본값이 되고,
     * string 속성에는 maxStrLength 50이 붙으며, 속성에 직접 지정한 값은 덮어쓰지 않아야 합니다.
     * applicableEntityTypes가 전혀 없으면 오류여야 합니다.
     */
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

    /**
     * 같은 이름의 속성을 두 번 추가하면 {@code build()}가 거부하는지 확인합니다.
     */
    @Test
    void duplicateAttributesAreRejected() {
        assertThatThrownBy(() -> entity("t").attribute("x", INT).attribute("x", STRING).build())
                .hasMessageContaining("attribute 'x' defined more than once");
    }

    /**
     * {@link TypeDefs#typesDef(TypeDefBuilder[])}가 빌드 결과를 범주별 목록(enumDefs, entityDefs,
     * classificationDefs)으로 나누어 담는지 확인합니다.
     */
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

    /**
     * {@code array}/{@code map} 타입 이름 생성과, 중첩된 컨테이너 타입에서 안쪽 타입 이름을 순서대로 뽑아내는지
     * 확인합니다. map의 키 자리에 쉼표를 포함한 map 타입이 와도 최상위 쉼표로 나누어야 합니다.
     */
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
