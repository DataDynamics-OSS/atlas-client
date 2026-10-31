package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.PropagateTags;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.RelationshipCategory;

/**
 * relationship 타입을 만드는 빌더입니다. {@link TypeDefs#relationship(String)}으로 생성합니다.
 * <pre>{@code
 * relationship("my_table_columns")
 *     .composition()
 *     .end1(end("my_table", "columns").set().container())
 *     .end2(end("my_column", "table"))
 *     .propagateTags(PropagateTags.ONE_TO_TWO)
 * }</pre>
 * 기본값은 범주 {@code ASSOCIATION}, {@code propagateTags NONE}입니다.
 * <p>
 * 빌더는 스레드 안전하지 않으므로 한 스레드에서만 사용해야 합니다.
 */
public final class RelationshipDefBuilder extends StructLikeDefBuilder<RelationshipDefBuilder, AtlasRelationshipDef> {

    /** 패키지 밖에서는 {@link TypeDefs#relationship(String)}을 통해서만 만들 수 있습니다. */
    RelationshipDefBuilder(String name) {
        super(new AtlasRelationshipDef(), name);
        def.setRelationshipCategory(RelationshipCategory.ASSOCIATION);
        def.setPropagateTags(PropagateTags.NONE);
    }

    /**
     * 대등한 관계로 지정합니다. 어느 끝점도 container가 아닙니다.
     *
     * @return 이 빌더
     */
    public RelationshipDefBuilder association() {
        def.setRelationshipCategory(RelationshipCategory.ASSOCIATION);
        return this;
    }

    /**
     * 포함 관계(aggregation)로 지정합니다. container가 삭제되어도 포함된 엔티티는 남아 있습니다.
     * 한쪽 끝점에 {@link RelationshipEndBuilder#container()}를 지정해야 합니다.
     *
     * @return 이 빌더
     */
    public RelationshipDefBuilder aggregation() {
        def.setRelationshipCategory(RelationshipCategory.AGGREGATION);
        return this;
    }

    /**
     * 구성 관계(composition)로 지정합니다. container가 삭제되면 포함된 엔티티도 함께 삭제됩니다.
     * 한쪽 끝점에 {@link RelationshipEndBuilder#container()}를 지정해야 합니다.
     *
     * @return 이 빌더
     */
    public RelationshipDefBuilder composition() {
        def.setRelationshipCategory(RelationshipCategory.COMPOSITION);
        return this;
    }

    /**
     * 첫 번째 끝점(endDef1)을 지정합니다. 끝점 빌더는 이 시점에 복사되므로 이후 끝점 빌더를 바꿔도 영향이 없습니다.
     *
     * @param end 끝점 빌더
     * @return 이 빌더
     */
    public RelationshipDefBuilder end1(RelationshipEndBuilder end) {
        def.setEndDef1(end.build());
        return this;
    }

    /**
     * 두 번째 끝점(endDef2)을 지정합니다. 끝점 빌더는 이 시점에 복사되므로 이후 끝점 빌더를 바꿔도 영향이 없습니다.
     *
     * @param end 끝점 빌더
     * @return 이 빌더
     */
    public RelationshipDefBuilder end2(RelationshipEndBuilder end) {
        def.setEndDef2(end.build());
        return this;
    }

    /**
     * 이 관계를 따라 classification이 전파되는 방향을 지정합니다(ONE_TO_TWO는 end1에서 end2 방향입니다).
     *
     * @param propagateTags 전파 방향
     * @return 이 빌더
     */
    public RelationshipDefBuilder propagateTags(PropagateTags propagateTags) {
        def.setPropagateTags(propagateTags);
        return this;
    }

    /**
     * 그래프에서 쓰이는 엣지 레이블을 지정합니다. 지정하지 않으면 Atlas가 {@code r:<name>} 형태로 만듭니다.
     *
     * @param relationshipLabel 엣지 레이블
     * @return 이 빌더
     */
    public RelationshipDefBuilder label(String relationshipLabel) {
        def.setRelationshipLabel(relationshipLabel);
        return this;
    }
}
