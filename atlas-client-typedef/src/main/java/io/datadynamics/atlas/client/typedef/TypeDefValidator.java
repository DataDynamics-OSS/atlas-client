package io.datadynamics.atlas.client.typedef;

import com.fasterxml.jackson.core.type.TypeReference;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBusinessMetadataDef;
import io.datadynamics.atlas.client.model.typedef.AtlasClassificationDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEnumDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.AtlasRelationshipEndDef;
import io.datadynamics.atlas.client.model.typedef.AtlasRelationshipDef.RelationshipCategory;
import io.datadynamics.atlas.client.model.typedef.AtlasStructDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.model.typedef.Cardinality;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks type definitions before they are sent to Atlas, turning Atlas' terse server-side
 * rejections into readable messages up front.
 */
public final class TypeDefValidator {

    /** A type name referenced by a definition, and where it is referenced from. */
    public record Reference(String typeName, String via) {
    }

    private TypeDefValidator() {
    }

    /** Checks that do not need to know about other types. */
    public static List<TypeIssue> validateStructure(AtlasBaseTypeDef def) {
        List<TypeIssue> issues = new ArrayList<>();
        String name = def.getName();
        if (name == null || name.isBlank()) {
            issues.add(TypeIssue.error(String.valueOf(name), "type name must not be blank"));
            return issues;
        }
        if (def instanceof AtlasStructDef) {
            validateAttributes(name, (AtlasStructDef) def, issues);
        }
        if (def instanceof AtlasEnumDef) {
            validateEnum(name, (AtlasEnumDef) def, issues);
        } else if (def instanceof AtlasRelationshipDef) {
            validateRelationship(name, (AtlasRelationshipDef) def, issues);
        } else if (def instanceof AtlasBusinessMetadataDef) {
            validateBusinessMetadata(name, (AtlasBusinessMetadataDef) def, issues);
        }
        return issues;
    }

    /**
     * Structure checks for every definition, duplicate names, and references to types that exist
     * neither in {@code typesDef} nor in {@code existingTypeNames}.
     */
    public static List<TypeIssue> validate(AtlasTypesDef typesDef, Set<String> existingTypeNames) {
        List<TypeIssue> issues = new ArrayList<>();
        Set<String> known = new HashSet<>(existingTypeNames);
        Set<String> seen = new HashSet<>();
        typesDef.stream().forEach(def -> {
            issues.addAll(validateStructure(def));
            if (def.getName() != null && !seen.add(def.getName())) {
                issues.add(TypeIssue.error(def.getName(), "defined more than once"));
            }
            known.add(def.getName());
        });
        typesDef.stream().forEach(def -> {
            for (Reference reference : references(def)) {
                if (!known.contains(reference.typeName()) && !AttributeTypes.isBuiltIn(reference.typeName())) {
                    issues.add(TypeIssue.error(def.getName(),
                            reference.via() + " refers to unknown type '" + reference.typeName() + "'"));
                }
            }
        });
        return issues;
    }

    /** Every type name the definition depends on. */
    public static List<Reference> references(AtlasBaseTypeDef def) {
        List<Reference> references = new ArrayList<>();
        if (def instanceof AtlasEntityDef) {
            nullSafe(((AtlasEntityDef) def).getSuperTypes())
                    .forEach(t -> references.add(new Reference(t, "superType")));
        }
        if (def instanceof AtlasClassificationDef) {
            AtlasClassificationDef classification = (AtlasClassificationDef) def;
            nullSafe(classification.getSuperTypes()).forEach(t -> references.add(new Reference(t, "superType")));
            nullSafe(classification.getEntityTypes()).forEach(t -> references.add(new Reference(t, "entityTypes")));
        }
        if (def instanceof AtlasRelationshipDef) {
            AtlasRelationshipDef relationship = (AtlasRelationshipDef) def;
            if (relationship.getEndDef1() != null && relationship.getEndDef1().getType() != null) {
                references.add(new Reference(relationship.getEndDef1().getType(), "endDef1"));
            }
            if (relationship.getEndDef2() != null && relationship.getEndDef2().getType() != null) {
                references.add(new Reference(relationship.getEndDef2().getType(), "endDef2"));
            }
        }
        if (def instanceof AtlasStructDef) {
            for (AtlasAttributeDef attribute : nullSafe(((AtlasStructDef) def).getAttributeDefs())) {
                if (attribute.getTypeName() != null) {
                    AttributeTypes.referencedTypeNames(attribute.getTypeName()).forEach(t ->
                            references.add(new Reference(t, "attribute '" + attribute.getName() + "'")));
                }
                if (def instanceof AtlasBusinessMetadataDef) {
                    applicableEntityTypes(attribute).forEach(t -> references.add(new Reference(t,
                            "applicableEntityTypes of attribute '" + attribute.getName() + "'")));
                }
            }
        }
        return references;
    }

    private static void validateAttributes(String typeName, AtlasStructDef def, List<TypeIssue> issues) {
        Set<String> names = new HashSet<>();
        for (AtlasAttributeDef attribute : nullSafe(def.getAttributeDefs())) {
            if (attribute.getName() == null || attribute.getName().isBlank()) {
                issues.add(TypeIssue.error(typeName, "attribute without name"));
                continue;
            }
            if (!names.add(attribute.getName())) {
                issues.add(TypeIssue.error(typeName, "attribute '" + attribute.getName() + "' defined more than once"));
            }
            if (attribute.getTypeName() == null || attribute.getTypeName().isBlank()) {
                issues.add(TypeIssue.error(typeName, "attribute '" + attribute.getName() + "' has no typeName"));
            }
        }
    }

    private static void validateEnum(String typeName, AtlasEnumDef def, List<TypeIssue> issues) {
        List<AtlasEnumDef.AtlasEnumElementDef> elements = nullSafe(def.getElementDefs());
        if (elements.isEmpty()) {
            issues.add(TypeIssue.error(typeName, "enum has no values"));
        }
        Set<String> values = new HashSet<>();
        for (AtlasEnumDef.AtlasEnumElementDef element : elements) {
            if (!values.add(element.getValue())) {
                issues.add(TypeIssue.error(typeName, "enum value '" + element.getValue() + "' defined more than once"));
            }
        }
        if (def.getDefaultValue() != null && !values.contains(def.getDefaultValue())) {
            issues.add(TypeIssue.error(typeName, "default value '" + def.getDefaultValue() + "' is not an enum value"));
        }
    }

    private static void validateRelationship(String typeName, AtlasRelationshipDef def, List<TypeIssue> issues) {
        AtlasRelationshipEndDef end1 = def.getEndDef1();
        AtlasRelationshipEndDef end2 = def.getEndDef2();
        if (end1 == null || end2 == null) {
            issues.add(TypeIssue.error(typeName, "relationship needs both endDef1 and endDef2"));
            return;
        }
        for (AtlasRelationshipEndDef end : List.of(end1, end2)) {
            if (end.getType() == null || end.getName() == null) {
                issues.add(TypeIssue.error(typeName, "relationship end needs a type and a name"));
            }
            if (end.getCardinality() == Cardinality.LIST) {
                issues.add(TypeIssue.error(typeName, "relationship end '" + end.getName()
                        + "' uses LIST cardinality, which Atlas does not support (use SET)"));
            }
        }
        boolean container1 = Boolean.TRUE.equals(end1.getContainer());
        boolean container2 = Boolean.TRUE.equals(end2.getContainer());
        RelationshipCategory category = def.getRelationshipCategory() == null
                ? RelationshipCategory.ASSOCIATION : def.getRelationshipCategory();
        if (container1 && container2) {
            issues.add(TypeIssue.error(typeName, "both relationship ends are containers"));
        } else if (category == RelationshipCategory.ASSOCIATION && (container1 || container2)) {
            issues.add(TypeIssue.error(typeName, "an ASSOCIATION cannot have a container end; "
                    + "use aggregation() or composition()"));
        } else if (category != RelationshipCategory.ASSOCIATION && !container1 && !container2) {
            issues.add(TypeIssue.error(typeName, category + " needs one end marked as container()"));
        }
        if (end1.getType() != null && end1.getType().equals(end2.getType())
                && end1.getName() != null && end1.getName().equals(end2.getName())) {
            issues.add(TypeIssue.error(typeName, "both ends define attribute '" + end1.getName()
                    + "' on type " + end1.getType()));
        }
    }

    private static void validateBusinessMetadata(String typeName, AtlasBusinessMetadataDef def, List<TypeIssue> issues) {
        for (AtlasAttributeDef attribute : nullSafe(def.getAttributeDefs())) {
            if (applicableEntityTypes(attribute).isEmpty()) {
                issues.add(TypeIssue.error(typeName, "attribute '" + attribute.getName()
                        + "' has no applicableEntityTypes option"));
            }
            if (AttributeTypes.STRING.equals(attribute.getTypeName())
                    && optionOf(attribute, AtlasBusinessMetadataDef.OPT_MAX_STRING_LENGTH) == null) {
                issues.add(TypeIssue.error(typeName, "string attribute '" + attribute.getName()
                        + "' has no maxStrLength option, which Atlas requires"));
            }
        }
    }

    static List<String> applicableEntityTypes(AtlasAttributeDef attribute) {
        String value = optionOf(attribute, AtlasBusinessMetadataDef.OPT_APPLICABLE_ENTITY_TYPES);
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return AtlasJson.fromJson(value, new TypeReference<List<String>>() {
            });
        } catch (RuntimeException e) {
            return List.of(value);
        }
    }

    private static String optionOf(AtlasAttributeDef attribute, String key) {
        Map<String, String> options = attribute.getOptions();
        return options == null ? null : options.get(key);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private static <T> Set<T> nullSafe(Set<T> set) {
        return set == null ? Collections.emptySet() : set;
    }
}
