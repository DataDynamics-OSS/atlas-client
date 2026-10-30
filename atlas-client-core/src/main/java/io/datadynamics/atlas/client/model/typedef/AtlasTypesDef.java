package io.datadynamics.atlas.client.model.typedef;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.datadynamics.atlas.client.model.AtlasModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Container of type definitions, the payload of {@code /v2/types/typedefs}.
 */
public class AtlasTypesDef extends AtlasModel {

    private List<AtlasEnumDef> enumDefs = new ArrayList<>();
    private List<AtlasStructDef> structDefs = new ArrayList<>();
    private List<AtlasClassificationDef> classificationDefs = new ArrayList<>();
    private List<AtlasEntityDef> entityDefs = new ArrayList<>();
    private List<AtlasRelationshipDef> relationshipDefs = new ArrayList<>();
    private List<AtlasBusinessMetadataDef> businessMetadataDefs = new ArrayList<>();

    public static AtlasTypesDef of(AtlasBaseTypeDef... typeDefs) {
        AtlasTypesDef result = new AtlasTypesDef();
        for (AtlasBaseTypeDef typeDef : typeDefs) {
            result.add(typeDef);
        }
        return result;
    }

    /** Adds the definition to the list matching its runtime type. */
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

    /** All definitions, in the dependency-friendly order Atlas itself uses. */
    public Stream<AtlasBaseTypeDef> stream() {
        return Stream.of(enumDefs, structDefs, classificationDefs, entityDefs, relationshipDefs, businessMetadataDefs)
                .flatMap(List::stream);
    }

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

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
