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
 * 타입 정의를 Atlas로 보내기 전에 미리 검사합니다.
 * <p>
 * Atlas 서버는 잘못된 타입 정의를 짧고 알아보기 어려운 오류로 거부합니다. 이 클래스는 같은 문제를
 * 전송 전에 찾아내어 어떤 타입의 어떤 부분이 잘못되었는지 읽기 쉬운 {@link TypeIssue} 목록으로 돌려줍니다.
 * <p>
 * 모든 메서드는 static이고 상태를 갖지 않으므로 여러 스레드에서 동시에 호출해도 안전합니다.
 */
public final class TypeDefValidator {

    /**
     * 정의가 참조하는 타입 이름과, 그 이름이 어디에서 참조되는지를 묶은 값입니다.
     *
     * @param typeName 참조되는 타입 이름
     * @param via      참조 위치 설명(예: {@code superType}, {@code endDef1}, {@code attribute 'columns'})으로,
     *                 오류 메시지에 그대로 들어갑니다
     */
    public record Reference(String typeName, String via) {
    }

    /** 인스턴스를 만들지 않는 유틸리티 클래스이므로 생성자를 막아 둡니다. */
    private TypeDefValidator() {
    }

    /**
     * 다른 타입을 알 필요 없이 정의 하나만 보고 할 수 있는 구조 검사를 수행합니다.
     * <p>
     * 타입 이름이 비어 있으면 그 오류 하나만 돌려주고 나머지 검사는 건너뜁니다. 그 밖에는 struct 계열 정의의
     * 속성(이름 누락, 중복, typeName 누락), enum 값, relationship 끝점과 범주, business metadata 옵션을 검사합니다.
     *
     * @param def 검사할 타입 정의
     * @return 발견된 문제 목록. 문제가 없으면 빈 목록입니다
     */
    public static List<TypeIssue> validateStructure(AtlasBaseTypeDef def) {
        List<TypeIssue> issues = new ArrayList<>();
        String name = def.getName();
        if (name == null || name.isBlank()) {
            issues.add(TypeIssue.error(String.valueOf(name), "type name must not be blank"));
            return issues;
        }
        // entity, classification, relationship, business metadata 모두 AtlasStructDef를 상속하므로
        // 속성 검사는 공통으로 먼저 하고, 아래에서 종류별 검사를 추가로 합니다.
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
     * 타입 정의 묶음 전체를 검사합니다.
     * <p>
     * 모든 정의에 대해 {@link #validateStructure(AtlasBaseTypeDef)}를 수행하고, 같은 이름이 두 번 이상
     * 정의되었는지, 그리고 {@code typesDef}에도 {@code existingTypeNames}에도 없는 타입을 참조하는지 확인합니다.
     * Atlas 내장 타입(예: {@code string}, {@code long})에 대한 참조는 문제로 보지 않습니다. {@code array<...>},
     * {@code map<...>} 같은 컨테이너 타입은 {@link #references(AtlasBaseTypeDef)}에서 이미 안쪽 타입 이름으로 풀려 있습니다.
     *
     * @param typesDef          검사할 타입 정의 묶음
     * @param existingTypeNames Atlas 서버에 이미 존재하는 타입 이름들. 이 이름에 대한 참조는 유효한 것으로 봅니다
     * @return 발견된 문제 목록. 문제가 없으면 빈 목록입니다
     */
    public static List<TypeIssue> validate(AtlasTypesDef typesDef, Set<String> existingTypeNames) {
        List<TypeIssue> issues = new ArrayList<>();
        Set<String> known = new HashSet<>(existingTypeNames);
        Set<String> seen = new HashSet<>();
        // 첫 번째 순회: 구조 검사와 중복 검사를 하면서, 묶음 안에서 정의되는 이름을 모두 known에 모읍니다.
        typesDef.stream().forEach(def -> {
            issues.addAll(validateStructure(def));
            if (def.getName() != null && !seen.add(def.getName())) {
                issues.add(TypeIssue.error(def.getName(), "defined more than once"));
            }
            known.add(def.getName());
        });
        // 두 번째 순회: 모든 이름을 모은 뒤에 참조를 확인해야 정의 순서와 상관없이 앞뒤 참조를 허용할 수 있습니다.
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

    /**
     * 정의가 의존하는 모든 타입 이름을 돌려줍니다.
     * <p>
     * entity의 superType, classification의 superType과 entityTypes, relationship의 두 끝점 타입,
     * 속성 타입 안에 들어 있는 타입 이름(예: {@code array<my_column>}의 {@code my_column}),
     * business metadata 속성의 applicableEntityTypes 옵션을 모읍니다. 내장 타입도 걸러내지 않고 포함합니다.
     *
     * @param def 의존 관계를 구할 타입 정의
     * @return 참조 목록. 같은 이름이 여러 번 나올 수 있습니다
     */
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

    /**
     * 속성 목록을 검사합니다. 이름이 없는 속성, 같은 이름으로 두 번 정의된 속성, typeName이 없는 속성을 찾습니다.
     * 이름이 없는 속성은 나머지 검사를 할 수 없으므로 오류만 기록하고 건너뜁니다.
     */
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

    /**
     * enum 정의를 검사합니다. 값이 하나도 없는 경우, 같은 값이 두 번 나오는 경우,
     * 기본값이 enum 값 목록에 없는 경우를 오류로 기록합니다.
     */
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

    /**
     * relationship 정의를 검사합니다.
     * <ul>
     *   <li>endDef1과 endDef2가 모두 있어야 합니다. 하나라도 없으면 나머지 검사는 하지 않습니다.</li>
     *   <li>각 끝점에는 type과 name이 있어야 하고, Atlas가 지원하지 않는 LIST cardinality를 쓰면 안 됩니다.</li>
     *   <li>두 끝점이 모두 container이면 안 됩니다.</li>
     *   <li>ASSOCIATION(범주를 지정하지 않으면 이것으로 봅니다)에는 container 끝점이 없어야 하고,
     *       AGGREGATION/COMPOSITION에는 container 끝점이 정확히 하나 있어야 합니다.</li>
     *   <li>두 끝점이 같은 타입에 같은 이름의 속성을 만들면 충돌하므로 오류입니다.</li>
     * </ul>
     */
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

    /**
     * business metadata 정의를 검사합니다. 모든 속성에는 applicableEntityTypes 옵션이 있어야 하고,
     * string 속성에는 Atlas가 요구하는 maxStrLength 옵션이 있어야 합니다.
     */
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

    /**
     * business metadata 속성의 applicableEntityTypes 옵션 값을 타입 이름 목록으로 읽습니다.
     * <p>
     * 옵션 값은 보통 JSON 배열 문자열(예: {@code ["hive_table","hive_column"]})입니다. JSON으로 읽을 수 없으면
     * 값 전체를 타입 이름 하나로 보고 돌려줍니다. 옵션이 없거나 비어 있으면 빈 목록을 돌려줍니다.
     */
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

    /** 속성의 옵션 맵에서 값을 꺼냅니다. 옵션 맵 자체가 없으면 {@code null}을 돌려줍니다. */
    private static String optionOf(AtlasAttributeDef attribute, String key) {
        Map<String, String> options = attribute.getOptions();
        return options == null ? null : options.get(key);
    }

    /** {@code null} 목록을 빈 목록으로 바꿔 반복문에서 null 검사를 하지 않아도 되게 합니다. */
    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /** {@code null} 집합을 빈 집합으로 바꿔 반복문에서 null 검사를 하지 않아도 되게 합니다. */
    private static <T> Set<T> nullSafe(Set<T> set) {
        return set == null ? Collections.emptySet() : set;
    }
}
