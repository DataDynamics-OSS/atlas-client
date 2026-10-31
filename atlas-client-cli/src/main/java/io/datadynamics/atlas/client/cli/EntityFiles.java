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
 * 가져올(import) 엔티티를 파일에서 읽습니다.
 * <p>
 * <b>CSV</b>: 한 행이 엔티티 하나, 한 열이 속성 하나입니다. 열 머리글 문법은 다음과 같습니다.
 * <pre>
 * name               문자열 속성
 * tags[]             문자열 배열. 값은 '|'로 구분합니다
 * db@hive_db         해당 qualifiedName을 가진 hive_db 엔티티에 대한 참조
 * columns[]@hive_col 참조의 배열. '|'로 구분합니다
 * typeName           그 행의 엔티티 타입(기본값: --type)
 * classifications    classification 이름. '|'로 구분합니다
 * labels             레이블. '|'로 구분합니다
 * </pre>
 * 빈 셀은 속성에서 빠집니다. 문자열을 속성 타입(숫자, 불리언, epoch 밀리초로 표현한 날짜)으로 바꾸는 일은
 * Atlas가 합니다. 참조는 같은 파일 안의 엔티티를 가리켜도 됩니다. 같은 배치 안에서는 placeholder guid로
 * 연결되고, 배치가 다르면 참조되는 엔티티가 앞쪽 배치에 있어야 합니다.
 * <p>
 * <b>YAML/JSON</b>: 엔티티({@code typeName}, {@code attributes}, ...)의 목록이거나, REST API 응답처럼
 * {@code entities} 목록을 가진 객체입니다.
 * <p>
 * 애플리케이션에서도 {@code atlas entities import}와 같은 파일을 가져올 수 있도록 public으로 공개합니다.
 * <pre>{@code
 * List<AtlasEntity> entities = EntityFiles.parse("tables.csv", text, "hive_table");
 * client.entities().createOrUpdate(EntityFiles.linkWithinBatch(entities));
 * }</pre>
 */
public final class EntityFiles {

    /** Atlas 모델에 맞게 설정한 YAML 파서입니다. */
    private static final ObjectMapper YAML = AtlasJson.configure(new YAMLMapper());
    /** CSV 셀 안에서 여러 값을 구분하는 문자입니다. */
    static final String SEPARATOR = "|";

    /** 정적 메서드만 가진 유틸리티 클래스이므로 인스턴스를 만들지 않습니다. */
    private EntityFiles() {
    }

    /**
     * {@code .csv}, {@code .json}, {@code .yaml}, {@code .yml} 파일을 UTF-8로 읽어 엔티티 목록으로 바꿉니다.
     * 형식은 파일 확장자로 정합니다({@link #parse(String, String, String)} 참고).
     *
     * @param file        읽을 파일
     * @param defaultType 타입을 지정하지 않은 엔티티에 쓸 타입. {@code null}이어도 됩니다
     * @return 파일 순서대로의 엔티티 목록
     * @throws UncheckedIOException     파일을 읽지 못한 경우
     * @throws IllegalArgumentException 지원하지 않는 형식이거나 엔티티가 올바르지 않은 경우
     */
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
     * 파일 내용을 엔티티 목록으로 바꿉니다. 형식은 {@code fileName}의 확장자(대소문자 무시)로 정합니다.
     * <p>
     * 모든 엔티티는 typeName과 qualifiedName이 있어야 합니다. qualifiedName이 없으면 기존 엔티티를 수정하지 못하고
     * 중복 엔티티를 만들게 되므로 오류로 처리합니다.
     *
     * @param fileName    형식을 판단할 파일 이름. 경로는 보지 않습니다
     * @param text        파일 내용
     * @param defaultType 타입을 지정하지 않은 엔티티에 쓸 타입. {@code null}이어도 됩니다
     * @return 파일 순서대로의 엔티티 목록. 내용이 비어 있으면 빈 목록
     * @throws IllegalArgumentException 지원하지 않는 형식이거나 엔티티가 올바르지 않은 경우
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

    /**
     * CSV 열 머리글 하나를 해석한 결과입니다.
     *
     * @param header        원래 머리글(오류 메시지용)
     * @param attribute     속성 이름({@code []}와 {@code @타입}을 뗀 부분)
     * @param array         {@code []}가 붙어 값이 여러 개인 열인지 여부
     * @param referenceType {@code @} 뒤에 적은 참조 대상 타입. 참조가 아니면 {@code null}
     */
    private record Column(String header, String attribute, boolean array, String referenceType) {

        /**
         * {@code name[]@type} 형식의 머리글을 해석합니다. {@code @}를 먼저 떼고 그다음 {@code []}를 뗍니다.
         * {@code @}가 맨 앞에 있으면 참조로 보지 않습니다.
         *
         * @throws IllegalArgumentException 속성 이름 부분이 비어 있는 경우
         */
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

        /**
         * 셀 문자열을 속성 값으로 바꿉니다. 단일 값은 문자열 그대로, 배열 열은 문자열 목록이 되며,
         * 참조 열은 qualifiedName으로 대상을 가리키는 {@link AtlasObjectId}(또는 그 목록)가 됩니다.
         */
        Object value(String cell) {
            if (!array) {
                return referenceType == null ? cell : AtlasObjectId.byQualifiedName(referenceType, cell);
            }
            List<String> values = split(cell);
            return referenceType == null ? values : values.stream()
                    .map(v -> AtlasObjectId.byQualifiedName(referenceType, v)).collect(Collectors.toList());
        }
    }

    /**
     * CSV 내용을 엔티티 목록으로 바꿉니다. 첫 행은 머리글입니다.
     * <p>
     * 머리글보다 필드가 많은 행은 오류이고, 적은 행은 나머지 열을 빈 셀로 봅니다.
     * 오류 메시지의 행 번호는 머리글을 1행으로 센 번호입니다(줄바꿈이 들어간 따옴표 필드가 있으면
     * 파일의 줄 번호와 다를 수 있습니다).
     */
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
                // typeName, classifications, labels는 속성이 아니라 엔티티 자체의 필드로 들어가는 특수 열입니다.
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

    /**
     * YAML 또는 JSON 내용을 엔티티 목록으로 바꿉니다.
     * <p>
     * 최상위가 {@code entities} 필드를 가진 객체이면 그 목록을 씁니다. 그 밖의 경우 최상위가 배열이면 각 원소를,
     * 객체 하나이면 그 객체를 엔티티로 봅니다. 내용이 비어 있거나 {@code null}이면 빈 목록입니다.
     *
     * @param mapper JSON 또는 YAML용 {@link ObjectMapper}
     * @throws IllegalArgumentException 문법이 잘못되었거나 엔티티가 올바르지 않은 경우
     */
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

    /**
     * typeName과 qualifiedName이 있는지 확인합니다.
     *
     * @param where 오류 메시지 앞에 붙일 위치(예: {@code "Row 3"})
     * @return 검증을 통과한 같은 엔티티
     */
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
     * 같은 배치 안의 엔티티를 qualifiedName으로 가리키는 참조를 placeholder guid 참조로 바꿉니다.
     * <p>
     * Atlas는 uniqueAttributes 참조를 이미 존재하는 엔티티로만 해석하고, 그렇지 않으면 요청 전체를
     * 거부합니다(ATLAS-404-00-00A). 그래서 이 변환이 없으면 테이블과 그 컬럼을 한 번에 가져올 수 없습니다.
     * <p>
     * guid가 없는 엔티티에는 placeholder guid를 새로 붙입니다. {@code attributes}와 {@code relationshipAttributes}
     * 안의 참조(목록 안의 참조, JSON에서 읽은 {@code typeName}/{@code uniqueAttributes} 맵 형태 포함)를
     * 타입과 qualifiedName으로 찾아 바꾸며, 배치 밖의 엔티티를 가리키는 참조는 그대로 둡니다.
     * 전달한 엔티티 객체를 직접 수정합니다.
     *
     * @param batch 한 번의 요청으로 보낼 엔티티
     * @return 같은 {@code batch} 목록(수정된 상태)
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

    /** 속성 맵의 값을 모두 {@link #link(Object, Map)}로 바꿉니다. 맵이 {@code null}이면 아무것도 하지 않습니다. */
    private static void link(Map<String, Object> attributes, Map<String, AtlasEntity> byKey) {
        if (attributes != null) {
            attributes.replaceAll((name, value) -> link(value, byKey));
        }
    }

    /**
     * 값 하나가 배치 안 엔티티에 대한 qualifiedName 참조이면 guid 참조로 바꾼 값을, 아니면 원래 값을 돌려줍니다.
     * 컬렉션이면 원소마다 재귀적으로 처리합니다. 이미 guid가 있는 참조와 qualifiedName이 없는 참조는 건드리지 않습니다.
     */
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

    /** 타입과 qualifiedName으로 조회 키를 만듭니다. 두 값 사이에 NUL 문자를 넣어 경계가 섞이지 않게 합니다. */
    private static String key(String typeName, Object qualifiedName) {
        return typeName + "\u0000" + qualifiedName;
    }

    /** 셀을 {@link #SEPARATOR}로 나누고, 각 값의 앞뒤 공백을 자른 뒤 빈 값은 버립니다. */
    private static List<String> split(String cell) {
        return Arrays.stream(cell.split("\\" + SEPARATOR)).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
