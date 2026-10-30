package io.datadynamics.atlas.client.typedef.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads and writes type definitions as JSON or YAML files.
 * <p>
 * The format is Atlas' own {@code AtlasTypesDef} JSON ({@code enumDefs}, {@code entityDefs}, ...),
 * so the model files shipped with Atlas ({@code models/1000-Hadoop/1030-hive_model.json}, ...)
 * can be read as-is, and YAML is the same structure written as YAML.
 */
public final class TypeDefFiles {

    private static final Set<String> SERVER_FIELDS = Set.of("guid", "createdBy", "updatedBy", "createTime",
            "updateTime", "version", "subTypes", "relationshipAttributeDefs", "businessAttributeDefs");

    private static final ObjectMapper JSON_WRITER = AtlasJson.configure(new ObjectMapper())
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final YAMLMapper YAML = AtlasJson.configure(new YAMLMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .build()));

    static {
        YAML.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    private TypeDefFiles() {
    }

    /** Reads one {@code .json}, {@code .yaml} or {@code .yml} file. */
    public static AtlasTypesDef read(Path file) {
        try {
            String content = Files.readString(file);
            return isYaml(file) ? fromYaml(content) : AtlasJson.fromJson(content, AtlasTypesDef.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read type definitions from " + file, e);
        } catch (AtlasClientException e) {
            throw new AtlasClientException("Invalid type definition file " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads every definition file under a directory (recursively) in path order and merges them,
     * matching Atlas' {@code 0000-...}, {@code 1000-...} model file naming.
     */
    public static AtlasTypesDef readAll(Path directory) {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile).filter(TypeDefFiles::isDefinitionFile).sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + directory, e);
        }
        AtlasTypesDef merged = new AtlasTypesDef();
        for (Path file : files) {
            read(file).stream().forEach(merged::add);
        }
        return merged;
    }

    /** Writes YAML or JSON depending on the file extension, without server-managed fields. */
    public static void write(AtlasTypesDef typesDef, Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, isYaml(file) ? toYaml(typesDef) : toJson(typesDef));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write type definitions to " + file, e);
        }
    }

    public static AtlasTypesDef fromYaml(String yaml) {
        try {
            AtlasTypesDef typesDef = YAML.readValue(yaml, AtlasTypesDef.class);
            return typesDef == null ? new AtlasTypesDef() : typesDef;
        } catch (IOException e) {
            throw new AtlasClientException("Failed to parse type definitions YAML: " + e.getMessage(), e);
        }
    }

    public static String toYaml(AtlasTypesDef typesDef) {
        try {
            return YAML.writeValueAsString(stripServerFields(typesDef));
        } catch (IOException e) {
            throw new AtlasClientException("Failed to write type definitions as YAML", e);
        }
    }

    public static String toJson(AtlasTypesDef typesDef) {
        try {
            return JSON_WRITER.writeValueAsString(stripServerFields(typesDef));
        } catch (IOException e) {
            throw new AtlasClientException("Failed to write type definitions as JSON", e);
        }
    }

    /**
     * Copy without the fields Atlas assigns (guid, timestamps, version, computed sub-types and
     * relationship attributes), so exported definitions can be kept in version control and
     * re-applied to another Atlas instance.
     */
    public static AtlasTypesDef stripServerFields(AtlasTypesDef typesDef) {
        ObjectNode root = AtlasJson.mapper().valueToTree(typesDef);
        root.fields().forEachRemaining(entry -> {
            if (entry.getValue() instanceof ArrayNode) {
                entry.getValue().forEach(def -> {
                    if (def instanceof ObjectNode) {
                        ((ObjectNode) def).remove(SERVER_FIELDS);
                    }
                });
            }
        });
        return AtlasJson.mapper().convertValue(root, AtlasTypesDef.class);
    }

    public static <T extends AtlasBaseTypeDef> T stripServerFields(T def) {
        ObjectNode node = AtlasJson.mapper().valueToTree(def);
        node.remove(SERVER_FIELDS);
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) def.getClass();
        return AtlasJson.mapper().convertValue(node, type);
    }

    private static boolean isYaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private static boolean isDefinitionFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml");
    }
}
