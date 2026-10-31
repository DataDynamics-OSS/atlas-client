package io.datadynamics.atlas.client.typedef;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.AtlasRelationshipEndDef;
import io.datadynamics.atlas.client.model.typedef.Cardinality;

/**
 * relationship 끝점 하나를 만드는 빌더입니다. {@link TypeDefs#end(String, String)}으로 생성합니다.
 * {@code end("my_table", "columns")}는 "{@code my_table} 타입의 엔티티가 반대쪽 끝점을 가리키는
 * {@code columns}라는 relationship 속성을 갖는다"는 뜻입니다.
 * <p>
 * 빌더는 스레드 안전하지 않으므로 한 스레드에서만 사용해야 합니다.
 */
public final class RelationshipEndBuilder {

    private final AtlasRelationshipEndDef def;

    /**
     * 패키지 밖에서는 {@link TypeDefs#end(String, String)}을 통해서만 만들 수 있습니다. cardinality 기본값은
     * {@code SINGLE}입니다.
     *
     * @throws IllegalArgumentException 엔티티 타입이나 속성 이름이 {@code null}이거나 비어 있을 때
     */
    RelationshipEndBuilder(String entityType, String attributeName) {
        if (entityType == null || entityType.isBlank() || attributeName == null || attributeName.isBlank()) {
            throw new IllegalArgumentException("Relationship end needs an entity type and an attribute name");
        }
        def = new AtlasRelationshipEndDef(entityType, attributeName, Cardinality.SINGLE);
    }

    /**
     * 속성이 참조 하나를 담도록 합니다(기본값).
     *
     * @return 이 빌더
     */
    public RelationshipEndBuilder single() {
        def.setCardinality(Cardinality.SINGLE);
        return this;
    }

    /**
     * 속성이 여러 참조를 담도록 합니다. Atlas의 relationship 끝점은 LIST를 지원하지 않으므로 SET을 씁니다.
     *
     * @return 이 빌더
     */
    public RelationshipEndBuilder set() {
        def.setCardinality(Cardinality.SET);
        return this;
    }

    /**
     * 이 끝점이 반대쪽 끝점을 소유한다고 표시합니다. aggregation과 composition에서는 정확히 한쪽 끝점에
     * 지정해야 합니다.
     *
     * @return 이 빌더
     */
    public RelationshipEndBuilder container() {
        def.setContainer(true);
        return this;
    }

    /**
     * 이 속성이 엔티티 타입의 {@code attributeDefs}에도 선언되어 있다고 표시합니다(relationship 도입 이전 모델용).
     *
     * @return 이 빌더
     */
    public RelationshipEndBuilder legacyAttribute() {
        def.setLegacyAttribute(true);
        return this;
    }

    /**
     * 끝점 설명을 지정합니다.
     *
     * @param description 설명 문구
     * @return 이 빌더
     */
    public RelationshipEndBuilder description(String description) {
        def.setDescription(description);
        return this;
    }

    /** 끝점 정의의 깊은 복사본을 만듭니다. {@link RelationshipDefBuilder#end1}/{@code end2}에서 호출합니다. */
    AtlasRelationshipEndDef build() {
        return AtlasJson.deepCopy(def);
    }
}
