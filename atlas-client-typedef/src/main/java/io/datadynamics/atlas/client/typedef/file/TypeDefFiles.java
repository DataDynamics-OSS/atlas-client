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
 * 타입 정의를 JSON 또는 YAML 파일로 읽고 씁니다.
 * <p>
 * 파일 형식은 Atlas 자체의 {@code AtlasTypesDef} JSON({@code enumDefs}, {@code entityDefs}, ...)입니다.
 * 그래서 Atlas에 함께 배포되는 모델 파일({@code models/1000-Hadoop/1030-hive_model.json} 등)을 그대로 읽을 수 있고,
 * YAML은 같은 구조를 YAML로 쓴 것입니다. 형식은 파일 확장자({@code .json}, {@code .yaml}, {@code .yml})로 정합니다.
 * <p>
 * 모든 메서드는 static이며, 내부에서 쓰는 Jackson 매퍼는 설정이 끝난 뒤 공유되므로 여러 스레드에서 호출해도 안전합니다.
 */
public final class TypeDefFiles {

    /** Atlas가 부여하거나 계산하는 필드입니다. 파일로 내보낼 때 제거합니다. */
    private static final Set<String> SERVER_FIELDS = Set.of("guid", "createdBy", "updatedBy", "createTime",
            "updateTime", "version", "subTypes", "relationshipAttributeDefs", "businessAttributeDefs");

    /** 빈 값은 생략하고 들여쓰기하여 사람이 읽고 버전 관리하기 좋은 JSON을 씁니다. */
    private static final ObjectMapper JSON_WRITER = AtlasJson.configure(new ObjectMapper())
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** 문서 시작 표시({@code ---})를 쓰지 않고, 따옴표를 꼭 필요할 때만 쓰는 YAML 매퍼입니다. */
    private static final YAMLMapper YAML = AtlasJson.configure(new YAMLMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .build()));

    static {
        // JSON과 마찬가지로 null이나 빈 컬렉션 필드는 출력하지 않습니다.
        YAML.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    /** 인스턴스를 만들지 않는 유틸리티 클래스이므로 생성자를 막아 둡니다. */
    private TypeDefFiles() {
    }

    /**
     * {@code .json}, {@code .yaml}, {@code .yml} 파일 하나를 읽습니다. {@code .yaml}/{@code .yml}이 아니면 JSON으로 읽습니다.
     *
     * @param file 읽을 파일
     * @return 파일에 담긴 타입 정의
     * @throws UncheckedIOException 파일을 읽지 못했을 때
     * @throws AtlasClientException 내용을 해석하지 못했을 때. 메시지에 파일 경로가 들어갑니다
     */
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
     * 디렉터리 아래(하위 디렉터리 포함)의 모든 정의 파일을 경로 순서대로 읽어 하나로 합칩니다.
     * Atlas 모델 파일의 {@code 0000-...}, {@code 1000-...} 이름 규칙처럼 이름 순서가 곧 적용 순서가 되게 하기 위해서입니다.
     *
     * @param directory 정의 파일이 들어 있는 디렉터리
     * @return 모든 파일의 정의를 합친 결과
     * @throws UncheckedIOException 디렉터리를 탐색하거나 파일을 읽지 못했을 때
     * @throws AtlasClientException 어느 파일의 내용을 해석하지 못했을 때
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

    /**
     * 파일 확장자에 따라 YAML 또는 JSON으로 씁니다. 서버가 관리하는 필드는 빼고 씁니다.
     * 상위 디렉터리가 없으면 만들고, 파일이 있으면 덮어씁니다.
     *
     * @param typesDef 쓸 타입 정의
     * @param file     대상 파일
     * @throws UncheckedIOException 파일을 쓰지 못했을 때
     */
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

    /**
     * YAML 문자열을 타입 정의로 읽습니다. 빈 문서이면 빈 {@link AtlasTypesDef}를 돌려줍니다.
     *
     * @param yaml YAML 문자열
     * @return 읽은 타입 정의
     * @throws AtlasClientException YAML을 해석하지 못했을 때
     */
    public static AtlasTypesDef fromYaml(String yaml) {
        try {
            AtlasTypesDef typesDef = YAML.readValue(yaml, AtlasTypesDef.class);
            return typesDef == null ? new AtlasTypesDef() : typesDef;
        } catch (IOException e) {
            throw new AtlasClientException("Failed to parse type definitions YAML: " + e.getMessage(), e);
        }
    }

    /**
     * 타입 정의를 서버가 관리하는 필드와 빈 값 없이 YAML 문자열로 씁니다.
     *
     * @param typesDef 쓸 타입 정의
     * @return YAML 문자열
     * @throws AtlasClientException 직렬화에 실패했을 때
     */
    public static String toYaml(AtlasTypesDef typesDef) {
        try {
            return YAML.writeValueAsString(stripServerFields(typesDef));
        } catch (IOException e) {
            throw new AtlasClientException("Failed to write type definitions as YAML", e);
        }
    }

    /**
     * 타입 정의를 서버가 관리하는 필드와 빈 값 없이 들여쓰기한 JSON 문자열로 씁니다.
     *
     * @param typesDef 쓸 타입 정의
     * @return JSON 문자열
     * @throws AtlasClientException 직렬화에 실패했을 때
     */
    public static String toJson(AtlasTypesDef typesDef) {
        try {
            return JSON_WRITER.writeValueAsString(stripServerFields(typesDef));
        } catch (IOException e) {
            throw new AtlasClientException("Failed to write type definitions as JSON", e);
        }
    }

    /**
     * Atlas가 부여하는 필드(guid, 타임스탬프, version, 계산된 하위 타입과 relationship 속성 등)를 뺀 복사본을 돌려줍니다.
     * 이렇게 내보낸 정의는 버전 관리에 넣어 두었다가 다른 Atlas 인스턴스에 다시 적용할 수 있습니다.
     * 원본은 바꾸지 않습니다.
     *
     * @param typesDef 원본 타입 정의 묶음
     * @return 서버 필드를 제거한 새 묶음
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

    /**
     * 정의 하나에서 Atlas가 부여하는 필드를 뺀 복사본을 돌려줍니다. 원본은 바꾸지 않습니다.
     *
     * @param def 원본 정의
     * @param <T> 정의 타입. 결과도 원본과 같은 클래스입니다
     * @return 서버 필드를 제거한 새 정의
     */
    public static <T extends AtlasBaseTypeDef> T stripServerFields(T def) {
        ObjectNode node = AtlasJson.mapper().valueToTree(def);
        node.remove(SERVER_FIELDS);
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) def.getClass();
        return AtlasJson.mapper().convertValue(node, type);
    }

    /** 확장자가 {@code .yaml} 또는 {@code .yml}인지(대소문자 무시) 확인합니다. */
    private static boolean isYaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    /** {@link #readAll(Path)}이 읽을 정의 파일인지 확장자로 판단합니다. */
    private static boolean isDefinitionFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml");
    }
}
