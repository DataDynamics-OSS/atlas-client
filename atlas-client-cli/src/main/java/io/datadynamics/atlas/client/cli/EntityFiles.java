package io.datadynamics.atlas.client.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasClassification;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasObjectId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads entities to import.
 * <p>
 * <b>CSV</b>: one entity per row, one attribute per column. Column header syntax:
 * <pre>
 * name               string attribute
 * tags[]             array of strings, values separated by '|'
 * db@hive_db         reference to the hive_db entity with that qualifiedName
 * columns[]@hive_col array of references, separated by '|'
 * typeName           entity type of the row (default: --type)
 * classifications    classification names, separated by '|'
 * labels             labels, separated by '|'
 * </pre>
 * Empty cells are left out. Atlas converts strings to the attribute's type (numbers, booleans, dates as epoch millis).
 * References may point at entities of the same file: within a batch they are linked through
 * placeholder guids; across batches the referenced entity must come first.
 * <p>
 * <b>YAML/JSON</b>: a list of entities ({@code typeName}, {@code attributes}, ...), or an object with
 * an {@code entities} list as returned by the REST API.
 * <p>
 * Public so that applications can import the same files as {@code atlas entities import}:
 * <pre>{@code
 * List<AtlasEntity> entities = EntityFiles.parse("tables.csv", text, "hive_table");
 * client.entities().createOrUpdate(EntityFiles.linkWithinBatch(entities));
 * }</pre>
 */
public final class EntityFiles {

    private static final ObjectMapper YAML = AtlasJson.configure(new YAMLMapper());
    static final String SEPARATOR = "|";

    private EntityFiles() {
    }

    /** Reads a {@code .csv}, {@code .json}, {@code .yaml} or {@code .yml} file. */
    public static List<AtlasEntity> read(Path file, String defaultType) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        return parse(file.getFileName().toString(), text, defaultType);
    }

    /**
     * Parses file contents; the format is chosen by the extension of {@code fileName}.
     *
     * @param defaultType type of entities that do not name one, may be {@code null}
     * @throws IllegalArgumentException if the format is unsupported or an entity is invalid
     */
    public static List<AtlasEntity> parse(String fileName, String text, String defaultType) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return fromCsv(text, defaultType);
        }
        if (name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml")) {
            return fromStructured(name.endsWith(".json") ? AtlasJson.mapper() : YAML, text, defaultType);
        }
        throw new IllegalArgumentException("Unsupported file type (use .csv, .json, .yaml or .yml): " + fileName);
    }

    private record Column(String header, String attribute, boolean array, String referenceType) {

        static Column parse(String header) {
            String spec = header.trim();
            String referenceType = null;
            int at = spec.indexOf('@');
            if (at > 0) {
                referenceType = spec.substring(at + 1).trim();
                spec = spec.substring(0, at).trim();
            }
            boolean array = spec.endsWith("[]");
            if (array) {
                spec = spec.substring(0, spec.length() - 2).trim();
            }
            if (spec.isEmpty()) {
                throw new IllegalArgumentException("Invalid CSV column header '" + header + "'");
            }
            return new Column(header, spec, array, referenceType);
        }

        Object value(String cell) {
            if (!array) {
                return referenceType == null ? cell : AtlasObjectId.byQualifiedName(referenceType, cell);
            }
            List<String> values = split(cell);
            return referenceType == null ? values : values.stream()
                    .map(v -> AtlasObjectId.byQualifiedName(referenceType, v)).collect(Collectors.toList());
        }
    }

    static List<AtlasEntity> fromCsv(String text, String defaultType) {
        List<List<String>> rows = Csv.parse(text);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Column> columns = rows.get(0).stream().map(Column::parse).collect(Collectors.toList());
        List<AtlasEntity> entities = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int line = r + 1;
            if (row.size() > columns.size()) {
                throw new IllegalArgumentException("Row " + line + " has " + row.size() + " fields but the header has "
                        + columns.size());
            }
            AtlasEntity entity = new AtlasEntity(defaultType);
            for (int c = 0; c < row.size(); c++) {
                String cell = row.get(c).trim();
                if (cell.isEmpty()) {
                    continue;
                }
                Column column = columns.get(c);
                switch (column.attribute()) {
                    case "typeName" -> entity.setTypeName(cell);
                    case "classifications" -> entity.setClassifications(split(cell).stream()
                            .map(AtlasClassification::new).collect(Collectors.toList()));
                    case "labels" -> entity.setLabels(new LinkedHashSet<>(split(cell)));
                    default -> entity.setAttribute(column.attribute(), column.value(cell));
                }
            }
            entities.add(validate(entity, "Row " + line));
        }
        return entities;
    }

    static List<AtlasEntity> fromStructured(ObjectMapper mapper, String text, String defaultType) {
        JsonNode root;
        try {
            root = mapper.readTree(text);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid entity file: " + e.getMessage(), e);
        }
        if (root != null && root.isObject() && root.has("entities")) {
            root = root.get("entities");
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(nodes::add);
        } else {
            nodes.add(root);
        }
        List<AtlasEntity> entities = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            AtlasEntity entity = AtlasJson.convert(nodes.get(i), AtlasEntity.class);
            if (entity.getTypeName() == null) {
                entity.setTypeName(defaultType);
            }
            entities.add(validate(entity, "Entity " + (i + 1)));
        }
        return entities;
    }

    private static AtlasEntity validate(AtlasEntity entity, String where) {
        if (entity.getTypeName() == null || entity.getTypeName().isBlank()) {
            throw new IllegalArgumentException(where + ": no typeName (use --type or a typeName column/field)");
        }
        Object qualifiedName = entity.getAttribute(AtlasObjectId.QUALIFIED_NAME);
        if (qualifiedName == null || qualifiedName.toString().isBlank()) {
            throw new IllegalArgumentException(where + ": qualifiedName is missing; it is needed to update "
                    + "existing entities instead of creating duplicates");
        }
        return entity;
    }

    /**
     * Replaces references by qualifiedName to entities of the same batch with placeholder guids.
     * Atlas only resolves unique-attribute references to entities that already exist, and rejects
     * the request otherwise (ATLAS-404-00-00A), so a table and its columns could not be imported together.
     */
    public static List<AtlasEntity> linkWithinBatch(List<AtlasEntity> batch) {
        Map<String, AtlasEntity> byKey = new HashMap<>();
        for (AtlasEntity entity : batch) {
            if (entity.getGuid() == null) {
                entity.assignPlaceholderGuid();
            }
            byKey.put(key(entity.getTypeName(), entity.getAttribute(AtlasObjectId.QUALIFIED_NAME)), entity);
        }
        for (AtlasEntity entity : batch) {
            link(entity.getAttributes(), byKey);
            link(entity.getRelationshipAttributes(), byKey);
        }
        return batch;
    }

    private static void link(Map<String, Object> attributes, Map<String, AtlasEntity> byKey) {
        if (attributes != null) {
            attributes.replaceAll((name, value) -> link(value, byKey));
        }
    }

    private static Object link(Object value, Map<String, AtlasEntity> byKey) {
        if (value instanceof Collection<?> values) {
            return values.stream().map(v -> link(v, byKey)).collect(Collectors.toList());
        }
        AtlasObjectId id = null;
        if (value instanceof AtlasObjectId objectId) {
            id = objectId;
        } else if (value instanceof Map<?, ?> map && map.containsKey("typeName") && map.containsKey("uniqueAttributes")) {
            id = AtlasJson.convert(map, AtlasObjectId.class);
        }
        if (id == null || id.getGuid() != null || id.qualifiedName() == null) {
            return value;
        }
        AtlasEntity target = byKey.get(key(id.getTypeName(), id.qualifiedName()));
        return target == null ? value : AtlasObjectId.byGuid(target.getGuid(), target.getTypeName());
    }

    private static String key(String typeName, Object qualifiedName) {
        return typeName + "\u0000" + qualifiedName;
    }

    private static List<String> split(String cell) {
        return Arrays.stream(cell.split("\\" + SEPARATOR)).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
