package io.datadynamics.atlas.client.model.typedef;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 여러 타입 정의를 분류별 목록으로 담는 컨테이너입니다. {@code /v2/types/typedefs}의 요청·응답 본문입니다.
 * <p>
 * 모든 목록은 빈 리스트로 초기화되어 있고 setter에 {@code null}을 넘겨도 빈 리스트로 바뀌므로, getter가
 * {@code null}을 돌려주지 않습니다. 정의를 넣을 때는 분류를 신경 쓰지 않도록 {@link #add(AtlasBaseTypeDef)}나
 * {@link #of(AtlasBaseTypeDef...)}를 쓰면 편합니다.
 */
public class AtlasTypesDef extends AtlasModel {

    private List<AtlasEnumDef> enumDefs = new ArrayList<>();
    private List<AtlasStructDef> structDefs = new ArrayList<>();
    private List<AtlasClassificationDef> classificationDefs = new ArrayList<>();
    private List<AtlasEntityDef> entityDefs = new ArrayList<>();
    private List<AtlasRelationshipDef> relationshipDefs = new ArrayList<>();
    private List<AtlasBusinessMetadataDef> businessMetadataDefs = new ArrayList<>();

    /**
     * 주어진 정의들을 담은 컨테이너를 만듭니다. 각 정의는 {@link #add(AtlasBaseTypeDef)}로 알맞은 목록에 들어갑니다.
     *
     * @param typeDefs 담을 타입 정의들
     * @return 새 컨테이너
     * @throws IllegalArgumentException 지원하지 않는 정의 클래스가 섞여 있을 때
     */
    public static AtlasTypesDef of(AtlasBaseTypeDef... typeDefs) {
        AtlasTypesDef result = new AtlasTypesDef();
        for (AtlasBaseTypeDef typeDef : typeDefs) {
            result.add(typeDef);
        }
        return result;
    }

    /**
     * 정의를 실행 시점의 클래스에 맞는 목록에 추가합니다.
     * <p>
     * entity, classification, business metadata 정의는 모두 {@link AtlasStructDef}의 하위 클래스이므로
     * 하위 클래스를 먼저 검사하고 {@link AtlasStructDef}는 마지막에 검사합니다. 순서를 바꾸면 모든 정의가
     * struct 목록으로 들어가 버립니다.
     *
     * @param typeDef 추가할 타입 정의
     * @return 메서드 체이닝을 위한 이 객체
     * @throws IllegalArgumentException 지원하지 않는 정의 클래스일 때
     */
    public AtlasTypesDef add(AtlasBaseTypeDef typeDef) {
        if (typeDef instanceof AtlasEnumDef) {
            enumDefs.add((AtlasEnumDef) typeDef);
        } else if (typeDef instanceof AtlasEntityDef) {
            entityDefs.add((AtlasEntityDef) typeDef);
        } else if (typeDef instanceof AtlasClassificationDef) {
            classificationDefs.add((AtlasClassificationDef) typeDef);
        } else if (typeDef instanceof AtlasRelationshipDef) {
            relationshipDefs.add((AtlasRelationshipDef) typeDef);
        } else if (typeDef instanceof AtlasBusinessMetadataDef) {
            businessMetadataDefs.add((AtlasBusinessMetadataDef) typeDef);
        } else if (typeDef instanceof AtlasStructDef) {
            structDefs.add((AtlasStructDef) typeDef);
        } else {
            throw new IllegalArgumentException("Unsupported type definition: " + typeDef.getClass().getName());
        }
        return this;
    }

    /**
     * 모든 정의를 하나의 스트림으로 돌려줍니다.
     * <p>
     * 순서는 Atlas 자신이 쓰는, 의존 관계를 고려한 순서입니다: enum → struct → classification → entity →
     * relationship → business metadata. 뒤에 오는 정의가 앞의 정의를 참조할 수 있는 순서입니다.
     */
    public Stream<AtlasBaseTypeDef> stream() {
        return Stream.of(enumDefs, structDefs, classificationDefs, entityDefs, relationshipDefs, businessMetadataDefs)
                .flatMap(List::stream);
    }

    /**
     * 담긴 정의가 하나도 없는지 확인합니다. JSON에 {@code empty} 속성으로 직렬화되지 않도록 {@link JsonIgnore}를
     * 붙였습니다.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return stream().findAny().isEmpty();
    }

    public List<AtlasEnumDef> getEnumDefs() {
        return enumDefs;
    }

    public void setEnumDefs(List<AtlasEnumDef> enumDefs) {
        this.enumDefs = nonNull(enumDefs);
    }

    public List<AtlasStructDef> getStructDefs() {
        return structDefs;
    }

    public void setStructDefs(List<AtlasStructDef> structDefs) {
        this.structDefs = nonNull(structDefs);
    }

    public List<AtlasClassificationDef> getClassificationDefs() {
        return classificationDefs;
    }

    public void setClassificationDefs(List<AtlasClassificationDef> classificationDefs) {
        this.classificationDefs = nonNull(classificationDefs);
    }

    public List<AtlasEntityDef> getEntityDefs() {
        return entityDefs;
    }

    public void setEntityDefs(List<AtlasEntityDef> entityDefs) {
        this.entityDefs = nonNull(entityDefs);
    }

    public List<AtlasRelationshipDef> getRelationshipDefs() {
        return relationshipDefs;
    }

    public void setRelationshipDefs(List<AtlasRelationshipDef> relationshipDefs) {
        this.relationshipDefs = nonNull(relationshipDefs);
    }

    public List<AtlasBusinessMetadataDef> getBusinessMetadataDefs() {
        return businessMetadataDefs;
    }

    public void setBusinessMetadataDefs(List<AtlasBusinessMetadataDef> businessMetadataDefs) {
        this.businessMetadataDefs = nonNull(businessMetadataDefs);
    }

    // setter에 null이 들어와도 getter와 stream()이 NullPointerException 없이 동작하도록 빈 리스트로 바꿉니다.
    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
