package io.datadynamics.atlas.client.typedef.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasAttributeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasEntityDef;
import io.datadynamics.atlas.client.model.typedef.AtlasStructDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.typedef.TypeDefValidator;
import io.datadynamics.atlas.client.typedef.TypeIssue;
import io.datadynamics.atlas.client.typedef.plan.TypeChange.Action;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 원하는 타입 정의를 서버의 정의와 비교하여 {@link TypePlan}을 계산합니다.
 * <p>
 * 원하는 정의에 <em>설정된</em> 필드만 비교합니다. 그래서 Atlas가 채워 넣는 값(기본값, guid, 타임스탬프 등)은
 * 변경으로 나타나지 않습니다. 수정(update) payload는 서버 정의 위에 원하는 필드를 덮어쓴 것입니다. 이렇게 하면
 * 원하는 정의에 언급되지 않은 속성도 그대로 유지됩니다(Atlas는 속성을 삭제할 수 없고, 기존 속성이 빠진
 * 수정 요청은 거부합니다).
 * <p>
 * Atlas가 거부하는 것으로 알려진 변경(Atlas 2.3에서 확인, {@code atlas-client-it} 참고)은 오류로 보고하여
 * 아무것도 보내기 전에 실패하게 합니다. 기존 타입에 필수 속성 추가, 속성의 타입 변경, relationship의 범주 변경이나
 * 끝점의 type, name, cardinality 변경, 기존 타입을 다른 범주로 다시 정의하는 경우가 이에 해당합니다.
 * <p>
 * 모든 메서드는 static이고 공유 상태를 바꾸지 않으므로 여러 스레드에서 동시에 호출해도 안전합니다.
 */
public final class TypePlanner {

    /** Atlas가 채워 넣는 필드입니다. 비교하지 않습니다. */
    private static final Set<String> SERVER_MANAGED = Set.of("guid", "createdBy", "updatedBy", "createTime",
            "updateTime", "version", "category", "subTypes", "relationshipAttributeDefs", "businessAttributeDefs");
    /** 읽기 전용 필드입니다. 수정 payload에서 제거합니다. */
    private static final Set<String> READ_ONLY = Set.of("subTypes", "relationshipAttributeDefs", "businessAttributeDefs");
    /** 일반 필드 비교에서 빼고 전용 로직으로 비교하는 필드입니다. */
    private static final Set<String> SPECIAL = Set.of("name", "attributeDefs", "elementDefs", "endDef1", "endDef2");

    private static final ObjectMapper MAPPER = AtlasJson.mapper();

    /** Atlas가 수정을 거부하는 relationship 끝점 속성입니다. */
    private static final Set<String> END_IMMUTABLE = Set.of("type", "name", "cardinality");

    /**
     * 필드가 없을 때 Atlas가 가정하는 값입니다. 서버 정의에 필드가 없으면 이 값과 같은 것으로 비교합니다.
     * 타입 수준, 속성 수준, relationship 끝점 수준으로 나누어 둡니다.
     */
    private static final Map<String, JsonNode> TYPE_DEFAULTS = Map.of(
            "relationshipCategory", MAPPER.getNodeFactory().textNode("ASSOCIATION"),
            "propagateTags", MAPPER.getNodeFactory().textNode("NONE"));
    private static final Map<String, JsonNode> ATTRIBUTE_DEFAULTS = Map.of(
            "cardinality", MAPPER.getNodeFactory().textNode("SINGLE"),
            "isOptional", MAPPER.getNodeFactory().booleanNode(false),
            "isUnique", MAPPER.getNodeFactory().booleanNode(false),
            "isIndexable", MAPPER.getNodeFactory().booleanNode(false));
    private static final Map<String, JsonNode> END_DEFAULTS = Map.of(
            "cardinality", MAPPER.getNodeFactory().textNode("SINGLE"),
            "isContainer", MAPPER.getNodeFactory().booleanNode(false),
            "isLegacyAttribute", MAPPER.getNodeFactory().booleanNode(false));

    /** 인스턴스를 만들지 않는 유틸리티 클래스이므로 생성자를 막아 둡니다. */
    private TypePlanner() {
    }

    /**
     * 원하는 정의와 서버의 현재 정의를 비교하여 plan을 계산합니다. Atlas를 호출하지 않는 순수 계산입니다.
     * <p>
     * 먼저 {@link TypeDefValidator#validate}로 구조와 참조를 검사합니다. 그다음 원하는 정의마다 서버에 없으면
     * CREATE, 범주가 다르면 오류, 그 밖에는 필드를 비교하여 UPDATE 또는 UNCHANGED로 분류합니다.
     * 이 메서드는 DELETE 변경을 만들지 않습니다.
     *
     * @param desired 있어야 할 모습의 정의
     * @param current 지금 서버에 있는 모든 정의({@code GET /v2/types/typedefs} 결과)
     * @return 계산된 plan. 오류가 있어도 예외를 던지지 않고 plan 안의 문제 목록에 담습니다
     */
    public static TypePlan plan(AtlasTypesDef desired, AtlasTypesDef current) {
        Map<String, AtlasBaseTypeDef> currentByName = current.stream()
                .collect(Collectors.toMap(AtlasBaseTypeDef::getName, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<TypeIssue> issues = new ArrayList<>(TypeDefValidator.validate(desired, currentByName.keySet()));
        List<TypeChange> changes = new ArrayList<>();

        desired.stream().forEach(def -> {
            AtlasBaseTypeDef existing = currentByName.get(def.getName());
            if (existing == null) {
                changes.add(new TypeChange(def.getName(), def.getCategory(), Action.CREATE, createDetails(def), def));
            } else if (existing.getCategory() != def.getCategory()) {
                issues.add(TypeIssue.error(def.getName(), "already exists as " + existing.getCategory()
                        + ", cannot be defined as " + def.getCategory()));
            } else {
                Diff diff = new Diff(def.getName(), issues);
                AtlasBaseTypeDef merged = diff.run(def, existing);
                Action action = diff.details.isEmpty() ? Action.UNCHANGED : Action.UPDATE;
                changes.add(new TypeChange(def.getName(), def.getCategory(), action, diff.details,
                        action == Action.UPDATE ? merged : existing));
            }
        });
        return new TypePlan(changes, issues);
    }

    /** 새로 생성할 타입의 plan 출력용 세부 내용(superTypes와 속성 목록)을 만듭니다. */
    private static List<String> createDetails(AtlasBaseTypeDef def) {
        List<String> details = new ArrayList<>();
        if (def instanceof AtlasEntityDef && ((AtlasEntityDef) def).getSuperTypes() != null
                && !((AtlasEntityDef) def).getSuperTypes().isEmpty()) {
            details.add("superTypes: " + ((AtlasEntityDef) def).getSuperTypes());
        }
        if (def instanceof AtlasStructDef && ((AtlasStructDef) def).getAttributeDefs() != null) {
            for (AtlasAttributeDef attribute : ((AtlasStructDef) def).getAttributeDefs()) {
                details.add("+ attribute '" + attribute.getName() + "' (" + attribute.getTypeName()
                        + (Boolean.FALSE.equals(attribute.getOptional()) ? ", required" : "") + ")");
            }
        }
        return details;
    }

    /**
     * 타입 하나의 비교를 담당합니다. 세부 변경과 문제를 모으고, 서버로 보낼 병합된 payload를 만듭니다.
     * 비교는 정의를 JSON 트리로 바꿔 필드 단위로 합니다. 타입마다 새 인스턴스를 만들어 씁니다.
     */
    private static final class Diff {

        private final String typeName;
        /** plan 전체가 공유하는 문제 목록입니다. 이 타입에서 발견한 문제를 여기에 더합니다. */
        private final List<TypeIssue> issues;
        private final List<String> details = new ArrayList<>();

        Diff(String typeName, List<TypeIssue> issues) {
            this.typeName = typeName;
            this.issues = issues;
        }

        /**
         * 원하는 정의와 서버 정의를 비교하여 세부 변경을 기록하고, 서버 정의 위에 원하는 값을 덮어쓴 정의를 돌려줍니다.
         *
         * @param desired  원하는 정의
         * @param existing 서버의 현재 정의(같은 범주)
         * @return 수정 요청에 쓸 병합된 정의. 반환 타입은 {@code existing}과 같은 클래스입니다
         */
        AtlasBaseTypeDef run(AtlasBaseTypeDef desired, AtlasBaseTypeDef existing) {
            ObjectNode want = MAPPER.valueToTree(desired);
            ObjectNode have = MAPPER.valueToTree(existing);
            // 서버 정의를 바탕으로 시작해야 원하는 정의에 없는 필드와 속성이 유지됩니다.
            ObjectNode merged = have.deepCopy();
            READ_ONLY.forEach(merged::remove);

            for (Iterator<String> it = want.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                if (SERVER_MANAGED.contains(field) || SPECIAL.contains(field)) {
                    continue;
                }
                JsonNode w = want.get(field);
                JsonNode h = valueOrDefault(have, field, TYPE_DEFAULTS);
                if (same(w, h)) {
                    continue;
                }
                details.add("~ " + field + ": " + display(h) + " -> " + display(w));
                merged.set(field, overlay(h, w));
                if (field.equals("superTypes")) {
                    issues.add(TypeIssue.warning(typeName, "changing superTypes of an existing type affects all its "
                            + "entities and sub-types"));
                } else if (field.equals("relationshipCategory")) {
                    issues.add(TypeIssue.error(typeName, "relationshipCategory cannot be changed ("
                            + display(h) + " -> " + display(w) + ")"));
                }
            }

            // 아래는 SPECIAL 필드로, 원소 단위로 비교해야 하므로 전용 로직을 씁니다.
            if (desired instanceof AtlasStructDef) {
                merged.set("attributeDefs", attributes(arrayOrEmpty(want, "attributeDefs"), arrayOrEmpty(have, "attributeDefs")));
            }
            if (want.has("elementDefs")) {
                enumElements((ArrayNode) want.get("elementDefs"), arrayOrEmpty(have, "elementDefs"));
                merged.set("elementDefs", want.get("elementDefs"));
            }
            for (String end : List.of("endDef1", "endDef2")) {
                if (want.has(end)) {
                    merged.set(end, relationshipEnd(end, (ObjectNode) want.get(end), (ObjectNode) have.get(end)));
                }
            }
            return MAPPER.convertValue(merged, existing.getClass());
        }

        /**
         * 속성 목록을 이름 기준으로 비교하여 병합된 속성 목록을 만듭니다.
         * <ul>
         *   <li>서버에만 있는 속성은 Atlas가 속성 삭제를 지원하지 않으므로 경고와 함께 그대로 남깁니다.</li>
         *   <li>양쪽에 있는 속성은 원하는 정의에 설정된 필드만 비교합니다. 타입 변경은 오류이고,
         *       선택 속성을 필수로 바꾸면 경고합니다.</li>
         *   <li>새 속성은 선택 속성이어야 하며, 필수 속성이면 오류입니다.</li>
         * </ul>
         * 결과 순서는 서버의 기존 속성 순서 다음에 새 속성이 오는 순서입니다.
         */
        private ArrayNode attributes(ArrayNode want, ArrayNode have) {
            Map<String, ObjectNode> wantByName = byName(want, "name");
            Map<String, ObjectNode> haveByName = byName(have, "name");
            ArrayNode result = MAPPER.createArrayNode();

            haveByName.forEach((name, h) -> {
                ObjectNode w = wantByName.get(name);
                if (w == null) {
                    issues.add(TypeIssue.warning(typeName, "attribute '" + name + "' exists in Atlas but not in the "
                            + "definition; it is kept because Atlas does not support deleting attributes"));
                    result.add(h);
                    return;
                }
                ObjectNode mergedAttribute = h.deepCopy();
                for (Iterator<String> it = w.fieldNames(); it.hasNext(); ) {
                    String field = it.next();
                    if (same(w.get(field), valueOrDefault(h, field, ATTRIBUTE_DEFAULTS))) {
                        continue;
                    }
                    if (field.equals("typeName")) {
                        issues.add(TypeIssue.error(typeName, "attribute '" + name + "': type cannot be changed ("
                                + display(h.get(field)) + " -> " + display(w.get(field)) + ")"));
                        continue;
                    }
                    if (field.equals("isOptional") && !w.get(field).asBoolean()) {
                        issues.add(TypeIssue.warning(typeName, "attribute '" + name
                                + "': making an optional attribute mandatory fails if existing entities lack a value"));
                    }
                    details.add("~ attribute '" + name + "' " + field + ": " + display(h.get(field))
                            + " -> " + display(w.get(field)));
                    mergedAttribute.set(field, overlay(h.get(field), w.get(field)));
                }
                result.add(mergedAttribute);
            });

            wantByName.forEach((name, w) -> {
                if (haveByName.containsKey(name)) {
                    return;
                }
                if (!w.path("isOptional").asBoolean(false)) {
                    issues.add(TypeIssue.error(typeName, "new attribute '" + name
                            + "' must be optional: Atlas rejects adding mandatory attributes to an existing type"));
                }
                details.add("+ attribute '" + name + "' (" + w.path("typeName").asText() + ")");
                result.add(w);
            });
            return result;
        }

        /**
         * enum 값을 비교하여 추가, 삭제된 값을 기록합니다. 값을 삭제하면 그 값을 쓰는 엔티티가 깨지므로 경고합니다.
         * 값 집합이 같아도 ordinal이나 설명이 다르면 변경으로 기록합니다. enum은 원하는 정의를 그대로 보내므로
         * 병합 결과는 만들지 않습니다.
         */
        private void enumElements(ArrayNode want, ArrayNode have) {
            Map<String, ObjectNode> wantByValue = byName(want, "value");
            Map<String, ObjectNode> haveByValue = byName(have, "value");
            wantByValue.keySet().stream().filter(v -> !haveByValue.containsKey(v))
                    .forEach(v -> details.add("+ value '" + v + "'"));
            haveByValue.keySet().stream().filter(v -> !wantByValue.containsKey(v)).forEach(v -> {
                details.add("- value '" + v + "'");
                issues.add(TypeIssue.warning(typeName, "enum value '" + v
                        + "' is removed; entities still using it will fail to load or update"));
            });
            boolean sameValues = wantByValue.keySet().equals(haveByValue.keySet());
            if (sameValues && !want.equals(have)) {
                details.add("~ elementDefs (ordinals or descriptions)");
            }
        }

        /**
         * relationship 끝점 하나를 비교하여 병합된 끝점을 만듭니다. type, name, cardinality 변경은 오류이고,
         * isContainer나 isLegacyAttribute 변경은 Atlas가 거부할 수 있으므로 경고합니다.
         *
         * @param end  {@code endDef1} 또는 {@code endDef2}(메시지에 쓰입니다)
         * @param want 원하는 끝점
         * @param have 서버의 끝점. 없으면 {@code null}이며, 이때는 원하는 끝점을 그대로 씁니다
         */
        private ObjectNode relationshipEnd(String end, ObjectNode want, ObjectNode have) {
            if (have == null) {
                details.add("~ " + end + ": (none) -> " + display(want));
                return want;
            }
            ObjectNode merged = have.deepCopy();
            for (Iterator<String> it = want.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                if (same(want.get(field), valueOrDefault(have, field, END_DEFAULTS))) {
                    continue;
                }
                if (END_IMMUTABLE.contains(field)) {
                    // Atlas가 ATLAS-400-00-038 오류로 거부합니다(2.3에서 확인).
                    issues.add(TypeIssue.error(typeName, end + " " + field + " cannot be changed ("
                            + display(valueOrDefault(have, field, END_DEFAULTS)) + " -> " + display(want.get(field)) + ")"));
                    continue;
                }
                if (field.equals("isContainer") || field.equals("isLegacyAttribute")) {
                    issues.add(TypeIssue.warning(typeName, "changing " + end + " " + field
                            + " of an existing relationship may be rejected by Atlas"));
                }
                details.add("~ " + end + " " + field + ": " + display(have.get(field)) + " -> " + display(want.get(field)));
                merged.set(field, overlay(have.get(field), want.get(field)));
            }
            return merged;
        }
    }

    /**
     * 원하는 값과 서버 값이 같은지 비교합니다. 스칼라 배열(superTypes, entityTypes 등)은 순서와 중복을 무시하고
     * 집합으로 비교합니다. 객체는 원하는 쪽에 있는 키만 재귀적으로 비교합니다.
     *
     * @param want 원하는 값. {@code null}이거나 JSON null이면 서버 값도 없어야 같습니다
     * @param have 서버 값
     * @return 같으면 {@code true}
     */
    static boolean same(JsonNode want, JsonNode have) {
        if (want == null || want.isNull()) {
            return have == null || have.isNull();
        }
        if (have == null || have.isNull()) {
            return false;
        }
        if (want.isArray() && have.isArray() && isScalarArray(want) && isScalarArray(have)) {
            return scalarSet(want).equals(scalarSet(have));
        }
        if (want.isObject() && have.isObject()) {
            // 최상위 필드와 마찬가지로 원하는 객체에 있는 키만 비교합니다.
            for (Iterator<String> it = want.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                if (!same(want.get(field), have.get(field))) {
                    return false;
                }
            }
            return true;
        }
        if (want.isValueNode() && have.isValueNode()) {
            // Atlas는 옵션이나 기본값에서 숫자를 문자열로 돌려주기도 하고 그 반대도 있으므로 텍스트로 비교합니다.
            return Objects.equals(want.asText(), have.asText());
        }
        return want.equals(have);
    }

    /** 필드 값을 돌려주되, 없거나 JSON null이면 Atlas가 가정하는 기본값(없으면 {@code null})을 돌려줍니다. */
    private static JsonNode valueOrDefault(ObjectNode node, String field, Map<String, JsonNode> defaults) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaults.get(field) : value;
    }

    /**
     * 원하는 값을 기존 값 위에 덮어씁니다. 둘 다 객체이면 키 단위로 재귀 병합하고, 그 밖에는 원하는 값으로 바꿉니다.
     * 기존 값은 바꾸지 않고 복사본을 만듭니다.
     */
    static JsonNode overlay(JsonNode have, JsonNode want) {
        if (have instanceof ObjectNode && want instanceof ObjectNode) {
            ObjectNode result = ((ObjectNode) have).deepCopy();
            want.fields().forEachRemaining(e -> result.set(e.getKey(), overlay(result.get(e.getKey()), e.getValue())));
            return result;
        }
        return want;
    }

    /** 배열의 모든 원소가 스칼라 값(문자열, 숫자, 불리언 등)인지 확인합니다. */
    private static boolean isScalarArray(JsonNode node) {
        for (JsonNode element : node) {
            if (!element.isValueNode()) {
                return false;
            }
        }
        return true;
    }

    /** 스칼라 배열을 텍스트 값의 정렬된 집합으로 바꿉니다. */
    private static Set<String> scalarSet(JsonNode node) {
        Set<String> values = new TreeSet<>();
        node.forEach(e -> values.add(e.asText()));
        return values;
    }

    /** 필드가 배열이면 그대로, 아니면(없거나 null이면) 빈 배열을 돌려줍니다. */
    private static ArrayNode arrayOrEmpty(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        return value instanceof ArrayNode ? (ArrayNode) value : MAPPER.createArrayNode();
    }

    /**
     * 객체 배열을 {@code keyField} 값을 키로 하는 맵으로 바꿉니다. 원래 순서를 유지하며, 객체가 아닌 원소는 건너뜁니다.
     * 같은 키가 여러 번 나오면 마지막 원소가 남습니다.
     */
    private static Map<String, ObjectNode> byName(ArrayNode array, String keyField) {
        Map<String, ObjectNode> result = new LinkedHashMap<>();
        for (JsonNode element : array) {
            if (element instanceof ObjectNode) {
                result.put(element.path(keyField).asText(), (ObjectNode) element);
            }
        }
        return result;
    }

    /** plan 출력용으로 값을 문자열로 바꿉니다. 값이 없으면 {@code (none)}, 스칼라 배열은 중복을 뺀 목록으로 씁니다. */
    private static String display(JsonNode node) {
        if (node == null || node.isNull()) {
            return "(none)";
        }
        if (node.isArray() && isScalarArray(node)) {
            Set<String> values = new LinkedHashSet<>();
            node.forEach(e -> values.add(e.asText()));
            return values.toString();
        }
        return node.toString();
    }
}
