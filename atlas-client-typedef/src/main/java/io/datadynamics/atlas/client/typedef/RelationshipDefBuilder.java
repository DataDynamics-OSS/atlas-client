package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.PropagateTags;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.RelationshipCategory;

/**
 * Builder of relationship types. Created with {@link TypeDefs#relationship(String)}.
 * <pre>{@code
 * relationship("my_table_columns")
 *     .composition()
 *     .end1(end("my_table", "columns").set().container())
 *     .end2(end("my_column", "table"))
 *     .propagateTags(PropagateTags.ONE_TO_TWO)
 * }</pre>
 * Defaults: {@code ASSOCIATION}, {@code propagateTags NONE}.
 */
public final class RelationshipDefBuilder extends StructLikeDefBuilder<RelationshipDefBuilder, AtlasRelationshipDef> {

    RelationshipDefBuilder(String name) {
        super(new AtlasRelationshipDef(), name);
        def.setRelationshipCategory(RelationshipCategory.ASSOCIATION);
        def.setPropagateTags(PropagateTags.NONE);
    }

    /** Peer relationship; neither end is a container. */
    public RelationshipDefBuilder association() {
        def.setRelationshipCategory(RelationshipCategory.ASSOCIATION);
        return this;
    }

    /** Container relationship; contained entities survive deletion of the container. */
    public RelationshipDefBuilder aggregation() {
        def.setRelationshipCategory(RelationshipCategory.AGGREGATION);
        return this;
    }

    /** Container relationship; contained entities are deleted with the container. */
    public RelationshipDefBuilder composition() {
        def.setRelationshipCategory(RelationshipCategory.COMPOSITION);
        return this;
    }

    public RelationshipDefBuilder end1(RelationshipEndBuilder end) {
        def.setEndDef1(end.build());
        return this;
    }

    public RelationshipDefBuilder end2(RelationshipEndBuilder end) {
        def.setEndDef2(end.build());
        return this;
    }

    /** Direction in which classifications flow along this relationship (ONE_TO_TWO = from end1 to end2). */
    public RelationshipDefBuilder propagateTags(PropagateTags propagateTags) {
        def.setPropagateTags(propagateTags);
        return this;
    }

    /** Edge label in the graph; Atlas generates one ({@code r:<name>}) when unset. */
    public RelationshipDefBuilder label(String relationshipLabel) {
        def.setRelationshipLabel(relationshipLabel);
        return this;
    }
}
