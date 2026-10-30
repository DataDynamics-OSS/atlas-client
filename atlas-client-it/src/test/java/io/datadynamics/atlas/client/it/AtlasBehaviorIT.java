package io.datadynamics.atlas.client.it;

import io.datadynamics.atlas.client.exception.AtlasServerException;
import io.datadynamics.atlas.client.model.instance.AtlasEntitiesWithExtInfo;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBusinessMetadataDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEnumDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.model.typedef.Cardinality;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.datadynamics.atlas.client.typedef.AttributeTypes.INT;
import static io.datadynamics.atlas.client.typedef.AttributeTypes.STRING;
import static io.datadynamics.atlas.client.typedef.TypeDefs.attr;
import static io.datadynamics.atlas.client.typedef.TypeDefs.end;
import static io.datadynamics.atlas.client.typedef.TypeDefs.entity;
import static io.datadynamics.atlas.client.typedef.TypeDefs.enumType;
import static io.datadynamics.atlas.client.typedef.TypeDefs.relationship;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down how Atlas itself reacts to changes the planner only warns about, so the planner's
 * severities stay accurate and Atlas upgrades that change the behaviour are noticed.
 * Each test sends the raw change (bypassing the planner) and asserts whether Atlas accepts it.
 */
class AtlasBehaviorIT extends AtlasIT {

    private static final Logger LOG = LoggerFactory.getLogger(AtlasBehaviorIT.class);

    private static String base, other, table, column, rel, tier;

    @BeforeAll
    static void createTypes() {
        base = name("base");
        other = name("other");
        table = name("table");
        column = name("column");
        rel = name("rel");
        tier = name("tier");
        cleanupTypes(rel, column, table, other, base, tier);
        new TypeDefManager(atlas).sync(
                enumType(tier).values("BRONZE", "SILVER", "GOLD"),
                entity(base).dataSet(),
                entity(other).dataSet(),
                entity(table).superTypes(base).attribute(attr("note", STRING)).attribute(attr("level", tier)),
                entity(column).dataSet(),
                relationship(rel).end1(end(table, "cols").set()).end2(end(column, "tbl")));
    }

    private static boolean accepted(String change, Runnable update) {
        try {
            update.run();
            LOG.info("Atlas ACCEPTED: {}", change);
            return true;
        } catch (AtlasServerException e) {
            LOG.info("Atlas REJECTED: {} -> {}", change, e.getMessage());
            return false;
        }
    }

    @Test
    void addingSuperTypeToExistingType() {
        AtlasEntityDef def = atlas.types().get(table, AtlasEntityDef.class);
        def.addSuperType(other);
        assertThat(accepted("add superType", () -> atlas.types().update(AtlasTypesDef.of(def)))).isTrue();
    }

    @Test
    void makingOptionalAttributeMandatoryWithoutInstances() {
        AtlasEntityDef def = atlas.types().get(table, AtlasEntityDef.class);
        def.findAttributeDef("note").orElseThrow().setOptional(false);
        assertThat(accepted("optional -> mandatory", () -> atlas.types().update(AtlasTypesDef.of(def)))).isTrue();
    }

    @Test
    void removingUnusedEnumValue() {
        AtlasEnumDef def = atlas.types().get(tier, AtlasEnumDef.class);
        def.getElementDefs().removeIf(e -> e.getValue().equals("GOLD"));
        assertThat(accepted("remove enum value", () -> atlas.types().update(AtlasTypesDef.of(def)))).isTrue();
    }

    @Test
    void changingRelationshipEndDescription() {
        AtlasRelationshipDef def = atlas.types().get(rel, AtlasRelationshipDef.class);
        def.getEndDef1().setDescription("columns of the table");
        assertThat(accepted("relationship end description", () -> atlas.types().update(AtlasTypesDef.of(def)))).isTrue();
    }

    @Test
    void changingRelationshipEndCardinality() {
        AtlasRelationshipDef def = atlas.types().get(rel, AtlasRelationshipDef.class);
        def.getEndDef2().setCardinality(Cardinality.SET);
        assertThat(accepted("relationship end cardinality", () -> atlas.types().update(AtlasTypesDef.of(def)))).isFalse();
    }

    @Test
    void businessMetadataStringWithoutMaxLength() {
        String name = name("bm");
        cleanupTypes(name);
        AtlasBusinessMetadataDef def = new AtlasBusinessMetadataDef(name);
        AtlasAttributeDef attribute = new AtlasAttributeDef("steward", STRING);
        attribute.setOptional(true);
        attribute.setOptions(Map.of(AtlasBusinessMetadataDef.OPT_APPLICABLE_ENTITY_TYPES, "[\"" + table + "\"]"));
        def.addAttributeDef(attribute);
        AtlasAttributeDef score = new AtlasAttributeDef("score", INT);
        score.setOptional(true);
        score.setOptions(Map.of(AtlasBusinessMetadataDef.OPT_APPLICABLE_ENTITY_TYPES, "[\"" + table + "\"]"));
        def.addAttributeDef(score);
        assertThat(accepted("BM string without maxStrLength", () -> atlas.types().create(AtlasTypesDef.of(def)))).isFalse();
    }

    @Test
    void bulkLookupWithMissingQualifiedName() {
        String existing = name("exists@it");
        String guid = atlas.entities().createOrUpdate(new AtlasEntity(column,
                Map.of("qualifiedName", existing, "name", "exists"))).firstGuid().orElseThrow();
        cleanupEntity(guid);

        AtlasEntitiesWithExtInfo[] result = new AtlasEntitiesWithExtInfo[1];
        boolean ok = accepted("bulk uniqueAttribute with a missing entity", () ->
                result[0] = atlas.entities().getBulkByQualifiedNames(column, List.of(existing, name("missing@it"))));
        assertThat(ok).isTrue();
        assertThat(result[0].getEntities()).extracting(AtlasEntity::getGuid).containsExactly(guid);
    }
}
