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
 * planner가 경고만 하는 변경에 Atlas 자체가 어떻게 반응하는지 고정해 둡니다.
 * <p>
 * planner의 심각도 판단이 실제와 맞는지 유지하고, Atlas 업그레이드로 동작이 바뀌면 알아차리기 위한 테스트입니다.
 * 각 테스트는 planner를 거치지 않고 변경을 그대로 보내, Atlas가 받아들이는지 거부하는지를 검증합니다.
 */
class AtlasBehaviorIT extends AtlasIT {

    private static final Logger LOG = LoggerFactory.getLogger(AtlasBehaviorIT.class);

    /** 이 클래스의 테스트가 쓰는 타입 이름입니다. 실행마다 고유한 접두어가 붙습니다. */
    private static String base, other, table, column, rel, tier;

    /**
     * 테스트에 쓸 타입을 만듭니다: enum(tier), 데이터셋 타입 base와 other, base를 상속하고 note·level 속성을 가진
     * table, column, 그리고 table과 column을 잇는 relationship(rel)입니다. 모두 클래스가 끝난 뒤 정리됩니다.
     */
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

    /**
     * 변경을 실행해 Atlas가 받아들였는지 확인하고, 결과를 로그로 남깁니다.
     *
     * @param change 로그에 쓸 변경 설명
     * @param update Atlas에 변경을 보내는 동작
     * @return 예외 없이 끝나면 {@code true}, Atlas가 오류 응답({@link AtlasServerException})을 주면 {@code false}
     */
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

    /** 이미 있는 entity 타입에 superType을 추가하면 Atlas가 받아들여야 합니다. */
    @Test
    void addingSuperTypeToExistingType() {
        AtlasEntityDef def = atlas.types().get(table, AtlasEntityDef.class);
        def.addSuperType(other);
        assertThat(accepted("add superType", () -> atlas.types().update(AtlasTypesDef.of(def)))).isTrue();
    }

    /** 인스턴스가 없는 타입의 선택(optional) 속성을 필수로 바꾸면 Atlas가 받아들여야 합니다. */
    @Test
    void makingOptionalAttributeMandatoryWithoutInstances() {
        AtlasEntityDef def = atlas.types().get(table, AtlasEntityDef.class);
        def.findAttributeDef("note").orElseThrow().setOptional(false);
        assertThat(accepted("optional -> mandatory", () -> atlas.types().update(AtlasTypesDef.of(def)))).isTrue();
    }

    /** 아무도 쓰지 않는 enum 값(GOLD)을 제거하면 Atlas가 받아들여야 합니다. */
    @Test
    void removingUnusedEnumValue() {
        AtlasEnumDef def = atlas.types().get(tier, AtlasEnumDef.class);
        def.getElementDefs().removeIf(e -> e.getValue().equals("GOLD"));
        assertThat(accepted("remove enum value", () -> atlas.types().update(AtlasTypesDef.of(def)))).isTrue();
    }

    /** relationship end의 설명(description)만 바꾸면 Atlas가 받아들여야 합니다. */
    @Test
    void changingRelationshipEndDescription() {
        AtlasRelationshipDef def = atlas.types().get(rel, AtlasRelationshipDef.class);
        def.getEndDef1().setDescription("columns of the table");
        assertThat(accepted("relationship end description", () -> atlas.types().update(AtlasTypesDef.of(def)))).isTrue();
    }

    /** relationship end의 cardinality를 바꾸면(SINGLE → SET) Atlas가 거부해야 합니다. */
    @Test
    void changingRelationshipEndCardinality() {
        AtlasRelationshipDef def = atlas.types().get(rel, AtlasRelationshipDef.class);
        def.getEndDef2().setCardinality(Cardinality.SET);
        assertThat(accepted("relationship end cardinality", () -> atlas.types().update(AtlasTypesDef.of(def)))).isFalse();
    }

    /**
     * string 속성에 {@code maxStrLength} 옵션이 없는 business metadata 타입을 만들면 Atlas가 거부해야 합니다.
     * 비교를 위해 int 속성(score)도 함께 넣습니다.
     */
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

    /**
     * uniqueAttributes 기반 bulk 조회에 존재하지 않는 qualifiedName이 섞여 있어도 Atlas가 호출을 실패시키지 않고,
     * 존재하는 엔티티만 돌려줘야 합니다.
     */
    @Test
    void bulkLookupWithMissingQualifiedName() {
        String existing = name("exists@it");
        String guid = atlas.entities().createOrUpdate(new AtlasEntity(column,
                Map.of("qualifiedName", existing, "name", "exists"))).firstGuid().orElseThrow();
        cleanupEntity(guid);

        // 람다 안에서 결과를 넘겨받기 위해 길이 1짜리 배열을 씁니다.
        AtlasEntitiesWithExtInfo[] result = new AtlasEntitiesWithExtInfo[1];
        boolean ok = accepted("bulk uniqueAttribute with a missing entity", () ->
                result[0] = atlas.entities().getBulkByQualifiedNames(column, List.of(existing, name("missing@it"))));
        assertThat(ok).isTrue();
        assertThat(result[0].getEntities()).extracting(AtlasEntity::getGuid).containsExactly(guid);
    }
}
