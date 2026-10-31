package io.datadynamics.atlas.client.model.typedef;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.datadynamics.atlas.client.model.AtlasModel;

/**
 * 두 엔티티 타입 사이의 relationship 타입 정의입니다.
 * <p>
 * 양 끝은 {@link #getEndDef1()}과 {@link #getEndDef2()}로 정의합니다. 각 끝에 지정한 속성 이름은 해당 엔티티의
 * {@code relationshipAttributes}에 나타납니다.
 */
public class AtlasRelationshipDef extends AtlasStructDef {

    /** relationship의 종류입니다. 양 끝 사이에 소유 관계가 있는지, 함께 삭제되는지를 결정합니다. */
    public enum RelationshipCategory {
        /** 대등한 관계입니다. 어느 쪽도 다른 쪽을 소유하지 않습니다. */
        ASSOCIATION,
        /** 포함 관계입니다. 포함된 쪽은 컨테이너가 삭제된 뒤에도 남을 수 있습니다. */
        AGGREGATION,
        /** 포함 관계입니다. 포함된 쪽은 컨테이너와 함께 삭제됩니다. */
        COMPOSITION
    }

    /** 이 relationship을 따라 classification(태그)을 어느 방향으로 전파할지 나타냅니다. */
    public enum PropagateTags {
        /** 전파하지 않습니다. */
        NONE,
        /** end1 쪽 엔티티의 classification을 end2 쪽으로 전파합니다. */
        ONE_TO_TWO,
        /** end2 쪽 엔티티의 classification을 end1 쪽으로 전파합니다. */
        TWO_TO_ONE,
        /** 양방향으로 전파합니다. */
        BOTH
    }

    private RelationshipCategory relationshipCategory;
    private String relationshipLabel;
    private PropagateTags propagateTags;
    private AtlasRelationshipEndDef endDef1;
    private AtlasRelationshipEndDef endDef2;

    /** 빈 relationship 타입 정의를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
    public AtlasRelationshipDef() {
        super(TypeCategory.RELATIONSHIP);
    }

    /**
     * 이름을 지정해 relationship 타입 정의를 만듭니다.
     *
     * @param name 타입 이름
     */
    public AtlasRelationshipDef(String name) {
        super(TypeCategory.RELATIONSHIP, name);
    }

    public RelationshipCategory getRelationshipCategory() {
        return relationshipCategory;
    }

    public void setRelationshipCategory(RelationshipCategory relationshipCategory) {
        this.relationshipCategory = relationshipCategory;
    }

    /** Atlas 그래프에서 이 relationship의 간선(edge)에 붙는 레이블을 돌려줍니다. */
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

    /** relationship의 첫 번째 끝 정의를 돌려줍니다. {@link PropagateTags}의 "ONE" 쪽입니다. */
    public AtlasRelationshipEndDef getEndDef1() {
        return endDef1;
    }

    public void setEndDef1(AtlasRelationshipEndDef endDef1) {
        this.endDef1 = endDef1;
    }

    /** relationship의 두 번째 끝 정의를 돌려줍니다. {@link PropagateTags}의 "TWO" 쪽입니다. */
    public AtlasRelationshipEndDef getEndDef2() {
        return endDef2;
    }

    public void setEndDef2(AtlasRelationshipEndDef endDef2) {
        this.endDef2 = endDef2;
    }

    /**
     * relationship의 한쪽 끝입니다. 엔티티 타입({@code type})과, 그 엔티티의 {@code relationshipAttributes}에
     * 나타날 속성 이름({@code name})으로 이루어집니다.
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

        /** 빈 끝 정의를 만듭니다. 주로 JSON 역직렬화에서 사용합니다. */
        public AtlasRelationshipEndDef() {
        }

        /**
         * 끝 정의를 만듭니다.
         *
         * @param type        이 끝의 엔티티 타입 이름
         * @param name        그 엔티티의 {@code relationshipAttributes}에 나타날 속성 이름
         * @param cardinality 이 끝 속성의 cardinality
         */
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

        /**
         * 이 끝이 컨테이너 쪽인지 돌려줍니다. AGGREGATION, COMPOSITION 관계에서 다른 쪽을 포함하는 끝이
         * {@code true}입니다. JSON 속성 이름은 {@code isContainer}입니다.
         */
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

        /**
         * relationship 도입 이전의 일반 속성을 relationship으로 대체한 경우인지 돌려줍니다.
         * JSON 속성 이름은 {@code isLegacyAttribute}입니다.
         */
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
