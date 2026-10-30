package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.AtlasRelationshipEndDef;
import io.datadynamics.atlas.client.model.typedef.Cardinality;

/**
 * Builder of one relationship end. Created with {@link TypeDefs#end(String, String)}:
 * {@code end("my_table", "columns")} means "entities of type {@code my_table} get a relationship
 * attribute {@code columns} pointing to the other end".
 */
public final class RelationshipEndBuilder {

    private final AtlasRelationshipEndDef def;

    RelationshipEndBuilder(String entityType, String attributeName) {
        if (entityType == null || entityType.isBlank() || attributeName == null || attributeName.isBlank()) {
            throw new IllegalArgumentException("Relationship end needs an entity type and an attribute name");
        }
        def = new AtlasRelationshipEndDef(entityType, attributeName, Cardinality.SINGLE);
    }

    /** The attribute holds one reference (default). */
    public RelationshipEndBuilder single() {
        def.setCardinality(Cardinality.SINGLE);
        return this;
    }

    /** The attribute holds many references. */
    public RelationshipEndBuilder set() {
        def.setCardinality(Cardinality.SET);
        return this;
    }

    /** This end owns the other end (required on exactly one end of aggregations and compositions). */
    public RelationshipEndBuilder container() {
        def.setContainer(true);
        return this;
    }

    /** The attribute is also declared in the entity type's {@code attributeDefs} (pre-relationship models). */
    public RelationshipEndBuilder legacyAttribute() {
        def.setLegacyAttribute(true);
        return this;
    }

    public RelationshipEndBuilder description(String description) {
        def.setDescription(description);
        return this;
    }

    AtlasRelationshipEndDef build() {
        return AtlasJson.deepCopy(def);
    }
}
