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
 * Compares desired type definitions with the server's and computes a {@link TypePlan}.
 * <p>
 * Only fields <em>set</em> in the desired definition are compared, so values Atlas fills in
 * (defaults, guids, timestamps) never show up as changes. Update payloads are the server
 * definition with the desired fields laid over it, which keeps attributes the desired
 * definition does not mention (Atlas cannot delete attributes and rejects updates without them).
 * <p>
 * Changes Atlas is known to reject (verified against Atlas 2.3, see {@code atlas-client-it}) are
 * reported as errors so they fail before anything is sent: adding a mandatory attribute, changing
 * an attribute's type, changing a relationship's category or the type, name or cardinality of its ends, and
 * redefining a type under another category.
 */
public final class TypePlanner {

    /** Filled in by Atlas; never compared. */
    private static final Set<String> SERVER_MANAGED = Set.of("guid", "createdBy", "updatedBy", "createTime",
            "updateTime", "version", "category", "subTypes", "relationshipAttributeDefs", "businessAttributeDefs");
    /** Read-only fields removed from update payloads. */
    private static final Set<String> READ_ONLY = Set.of("subTypes", "relationshipAttributeDefs", "businessAttributeDefs");
    /** Compared by dedicated logic. */
    private static final Set<String> SPECIAL = Set.of("name", "attributeDefs", "elementDefs", "endDef1", "endDef2");

    private static final ObjectMapper MAPPER = AtlasJson.mapper();

    /** Relationship end properties Atlas refuses to update. */
    private static final Set<String> END_IMMUTABLE = Set.of("type", "name", "cardinality");

    /** Values Atlas assumes when a field is absent; an absent server value compares equal to these. */
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

    private TypePlanner() {
    }

    /**
     * @param desired the definitions as they should be
     * @param current all definitions currently on the server ({@code GET /v2/types/typedefs})
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

    /** Diff of one type; collects details and issues and builds the merged payload. */
    private static final class Diff {

        private final String typeName;
        private final List<TypeIssue> issues;
        private final List<String> details = new ArrayList<>();

        Diff(String typeName, List<TypeIssue> issues) {
            this.typeName = typeName;
            this.issues = issues;
        }

        AtlasBaseTypeDef run(AtlasBaseTypeDef desired, AtlasBaseTypeDef existing) {
            ObjectNode want = MAPPER.valueToTree(desired);
            ObjectNode have = MAPPER.valueToTree(existing);
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
                    // Rejected by Atlas with ATLAS-400-00-038 (verified on 2.3).
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

    /** Equality where arrays of scalars compare as sets (superTypes, entityTypes, ...). */
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
            // Only keys present in the desired object matter, as for top-level fields.
            for (Iterator<String> it = want.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                if (!same(want.get(field), have.get(field))) {
                    return false;
                }
            }
            return true;
        }
        if (want.isValueNode() && have.isValueNode()) {
            // Atlas sometimes returns numbers as strings in options/defaults and vice versa.
            return Objects.equals(want.asText(), have.asText());
        }
        return want.equals(have);
    }

    private static JsonNode valueOrDefault(ObjectNode node, String field, Map<String, JsonNode> defaults) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaults.get(field) : value;
    }

    /** Desired value laid over the existing one; objects are merged key by key, anything else is replaced. */
    static JsonNode overlay(JsonNode have, JsonNode want) {
        if (have instanceof ObjectNode && want instanceof ObjectNode) {
            ObjectNode result = ((ObjectNode) have).deepCopy();
            want.fields().forEachRemaining(e -> result.set(e.getKey(), overlay(result.get(e.getKey()), e.getValue())));
            return result;
        }
        return want;
    }

    private static boolean isScalarArray(JsonNode node) {
        for (JsonNode element : node) {
            if (!element.isValueNode()) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> scalarSet(JsonNode node) {
        Set<String> values = new TreeSet<>();
        node.forEach(e -> values.add(e.asText()));
        return values;
    }

    private static ArrayNode arrayOrEmpty(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        return value instanceof ArrayNode ? (ArrayNode) value : MAPPER.createArrayNode();
    }

    private static Map<String, ObjectNode> byName(ArrayNode array, String keyField) {
        Map<String, ObjectNode> result = new LinkedHashMap<>();
        for (JsonNode element : array) {
            if (element instanceof ObjectNode) {
                result.put(element.path(keyField).asText(), (ObjectNode) element);
            }
        }
        return result;
    }

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
