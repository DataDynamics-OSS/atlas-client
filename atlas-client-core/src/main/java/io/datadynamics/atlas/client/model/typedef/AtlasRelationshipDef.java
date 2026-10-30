package io.datadynamics.atlas.client.model.typedef;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.datadynamics.atlas.client.model.AtlasModel;

/**
 * Relationship type definition between two entity types.
 */
public class AtlasRelationshipDef extends AtlasStructDef {

    public enum RelationshipCategory {
        /** Peer relationship; neither end owns the other. */
        ASSOCIATION,
        /** Container relationship; the contained end may outlive the container. */
        AGGREGATION,
        /** Container relationship; the contained end is deleted with the container. */
        COMPOSITION
    }

    public enum PropagateTags {
        NONE,
        ONE_TO_TWO,
        TWO_TO_ONE,
        BOTH
    }

    private RelationshipCategory relationshipCategory;
    private String relationshipLabel;
    private PropagateTags propagateTags;
    private AtlasRelationshipEndDef endDef1;
    private AtlasRelationshipEndDef endDef2;

    public AtlasRelationshipDef() {
        super(TypeCategory.RELATIONSHIP);
    }

    public AtlasRelationshipDef(String name) {
        super(TypeCategory.RELATIONSHIP, name);
    }

    public RelationshipCategory getRelationshipCategory() {
        return relationshipCategory;
    }

    public void setRelationshipCategory(RelationshipCategory relationshipCategory) {
        this.relationshipCategory = relationshipCategory;
    }

    public String getRelationshipLabel() {
        return relationshipLabel;
    }

    public void setRelationshipLabel(String relationshipLabel) {
        this.relationshipLabel = relationshipLabel;
    }

    public PropagateTags getPropagateTags() {
        return propagateTags;
    }

    public void setPropagateTags(PropagateTags propagateTags) {
        this.propagateTags = propagateTags;
    }

    public AtlasRelationshipEndDef getEndDef1() {
        return endDef1;
    }

    public void setEndDef1(AtlasRelationshipEndDef endDef1) {
        this.endDef1 = endDef1;
    }

    public AtlasRelationshipEndDef getEndDef2() {
        return endDef2;
    }

    public void setEndDef2(AtlasRelationshipEndDef endDef2) {
        this.endDef2 = endDef2;
    }

    /**
     * One end of a relationship: the entity {@code type} and the attribute {@code name} that
     * appears in that entity's {@code relationshipAttributes}.
     */
    public static class AtlasRelationshipEndDef extends AtlasModel {

        private String type;
        private String name;
        @JsonProperty("isContainer")
        private Boolean container;
        private Cardinality cardinality;
        @JsonProperty("isLegacyAttribute")
        private Boolean legacyAttribute;
        private String description;

        public AtlasRelationshipEndDef() {
        }

        public AtlasRelationshipEndDef(String type, String name, Cardinality cardinality) {
            this.type = type;
            this.name = name;
            this.cardinality = cardinality;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Boolean getContainer() {
            return container;
        }

        public void setContainer(Boolean container) {
            this.container = container;
        }

        public Cardinality getCardinality() {
            return cardinality;
        }

        public void setCardinality(Cardinality cardinality) {
            this.cardinality = cardinality;
        }

        public Boolean getLegacyAttribute() {
            return legacyAttribute;
        }

        public void setLegacyAttribute(Boolean legacyAttribute) {
            this.legacyAttribute = legacyAttribute;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
