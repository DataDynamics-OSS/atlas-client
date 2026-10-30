package io.datadynamics.atlas.client.json;

import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import io.datadynamics.atlas.client.model.instance.EntityStatus;
import io.datadynamics.atlas.client.model.search.FilterCriteria;
import io.datadynamics.atlas.client.model.search.FilterCriteria.Operator;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.model.typedef.Cardinality;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AtlasJsonTest {

    @Test
    void attributeDefUsesAtlasBooleanNames() {
        AtlasAttributeDef def = new AtlasAttributeDef("owner", "string");
        def.setOptional(true);
        def.setUnique(false);
        def.setIndexable(true);
        def.setCardinality(Cardinality.SINGLE);

        String json = AtlasJson.toJson(def);

        assertThat(json)
                .contains("\"isOptional\":true")
                .contains("\"isUnique\":false")
                .contains("\"isIndexable\":true")
                .doesNotContain("\"optional\"")
                .doesNotContain("\"description\"");
    }

    @Test
    void typedefRoundTripKeepsUnknownFields() {
        String json = "{\"entityDefs\":[{\"category\":\"ENTITY\",\"name\":\"my_table\",\"superTypes\":[\"DataSet\"],"
                + "\"createTime\":1700000000123,\"futureField\":{\"a\":1},"
                + "\"attributeDefs\":[{\"name\":\"owner\",\"typeName\":\"string\",\"isOptional\":true,\"newFlag\":true}]}]}";

        AtlasTypesDef typesDef = AtlasJson.fromJson(json, AtlasTypesDef.class);
        AtlasEntityDef entityDef = typesDef.getEntityDefs().get(0);

        assertThat(entityDef.getName()).isEqualTo("my_table");
        assertThat(entityDef.getSuperTypes()).containsExactly("DataSet");
        assertThat(entityDef.getCreateTime()).isEqualTo(Instant.ofEpochMilli(1700000000123L));
        assertThat(entityDef.getAttributeDefs().get(0).getOptional()).isTrue();

        String written = AtlasJson.toJson(typesDef);
        assertThat(written)
                .contains("\"futureField\":{\"a\":1}")
                .contains("\"newFlag\":true")
                .contains("\"createTime\":1700000000123");
    }

    @Test
    void unknownEnumValuesDoNotFail() {
        AtlasEntity entity = AtlasJson.fromJson("{\"typeName\":\"t\",\"status\":\"SOMETHING_NEW\"}", AtlasEntity.class);
        assertThat(entity.getStatus()).isNull();

        AtlasEntity active = AtlasJson.fromJson("{\"typeName\":\"t\",\"status\":\"ACTIVE\"}", AtlasEntity.class);
        assertThat(active.getStatus()).isEqualTo(EntityStatus.ACTIVE);
    }

    @Test
    void filterOperatorsUseAtlasSymbols() {
        FilterCriteria criteria = FilterCriteria.and(
                FilterCriteria.of("owner", Operator.EQ, "kim"),
                FilterCriteria.of("name", Operator.STARTS_WITH, "sales_"));

        String json = AtlasJson.toJson(criteria);
        assertThat(json).contains("\"operator\":\"=\"").contains("\"operator\":\"startsWith\"")
                .contains("\"condition\":\"AND\"");

        FilterCriteria parsed = AtlasJson.fromJson("{\"attributeName\":\"x\",\"operator\":\"lt\",\"attributeValue\":\"1\"}",
                FilterCriteria.class);
        assertThat(parsed.getOperator()).isEqualTo(Operator.LT);
    }

    @Test
    void mutationResponseHelpers() {
        String json = "{\"mutatedEntities\":{"
                + "\"CREATE\":[{\"typeName\":\"hive_table\",\"guid\":\"g1\",\"attributes\":{\"qualifiedName\":\"db.t1@c\"}}],"
                + "\"UPDATE\":[{\"typeName\":\"hive_db\",\"guid\":\"g2\",\"attributes\":{\"qualifiedName\":\"db@c\"}}]},"
                + "\"guidAssignments\":{\"-100\":\"g1\"}}";

        EntityMutationResponse response = AtlasJson.fromJson(json, EntityMutationResponse.class);

        assertThat(response.created()).hasSize(1);
        assertThat(response.updated()).hasSize(1);
        assertThat(response.deleted()).isEmpty();
        assertThat(response.findGuid("hive_db", "db@c")).contains("g2");
        assertThat(response.getGuidAssignments()).containsEntry("-100", "g1");
        assertThat(response.summary()).containsEntry(EntityMutationResponse.EntityOperation.CREATE, 1);
        assertThat(AtlasJson.toJson(response)).doesNotContain("\"created\"");
    }

    @Test
    void entityReferencesByQualifiedName() {
        AtlasEntity table = new AtlasEntity("hive_table", Map.of("qualifiedName", "db.t@c", "name", "t"));

        assertThat(AtlasJson.toJson(table.toObjectId()))
                .isEqualTo("{\"typeName\":\"hive_table\",\"uniqueAttributes\":{\"qualifiedName\":\"db.t@c\"}}");

        table.assignPlaceholderGuid();
        assertThat(table.getGuid()).startsWith("-");
        assertThat(table.toObjectId().hasAssignedGuid()).isFalse();
    }
}
