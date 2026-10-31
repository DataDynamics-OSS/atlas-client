package io.datadynamics.atlas.demo.web;

import com.fasterxml.jackson.core.type.TypeReference;
import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.cli.EntityFiles;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.instance.AtlasClassification;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.client.model.instance.AtlasEntityHeader;
import io.datadynamics.atlas.client.model.instance.AtlasEntityWithExtInfo;
import io.datadynamics.atlas.client.model.instance.EntityMutationResponse;
import io.datadynamics.atlas.client.model.instance.EntityStatus;
import io.datadynamics.atlas.client.model.search.AtlasSearchResult;
import io.datadynamics.atlas.client.model.search.FilterCriteria;
import io.datadynamics.atlas.client.model.search.SearchParameters;
import io.datadynamics.atlas.client.model.typedef.AtlasTypeDefHeader;
import io.datadynamics.atlas.client.model.typedef.TypeCategory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 엔티티 페이지입니다. 검색, 엔티티 상세 보기와 변경(속성, classification, 레이블, business metadata, 삭제·purge),
 * 파일에서 엔티티 가져오기를 제공합니다.
 */
@Controller
@RequestMapping("/entities")
class EntitiesController {

    /** 상세 페이지에 보여 줄 최근 audit 이벤트 수입니다. */
    static final int AUDIT_EVENTS = 25;
    /** 가져오기에서 한 번의 요청으로 보내는 엔티티 수입니다. */
    static final int IMPORT_BATCH_SIZE = 100;

    /** 가져오기 폼에 미리 채워 두는 예제 엔티티(YAML)입니다. */
    static final String SAMPLE_ENTITIES = """
            # Same format as `atlas entities import` (YAML/JSON list, or CSV with format=csv).
            - typeName: DataSet
              attributes:
                qualifiedName: console.sample_a@demo
                name: sample_a
                description: imported from the demo app
              labels: [console]
            - typeName: DataSet
              attributes:
                qualifiedName: console.sample_b@demo
                name: sample_b
            """;

    private final AtlasClient client;

    EntitiesController(AtlasClient client) {
        this.client = client;
    }

    // ---------------------------------------------------------------- 검색

    /**
     * {@code GET /entities}: 검색 폼을 보여 주고, 폼이 제출된 경우에만 검색을 실행해 결과를 함께 보여 줍니다.
     * 검색 종류는 {@link SearchForm#mode}를 참고하세요. 검색이 실패하면 같은 페이지에 실패 내용을 보여 줍니다.
     */
    @GetMapping
    String search(@ModelAttribute("form") SearchForm form, Model model) {
        if (form.submitted()) {
            AtlasSearchResult result = Actions.call(model, () -> runSearch(form));
            if (result != null) {
                model.addAttribute("result", result);
                model.addAttribute("entities", entities(result));
                if (result.getAttributes() != null) {
                    model.addAttribute("attributeResult", result.getAttributes());
                }
            }
        }
        model.addAttribute("operators", FilterCriteria.Operator.values());
        return "entities/search";
    }

    /**
     * 폼의 검색 종류에 맞는 Atlas 검색 API를 부릅니다.
     * {@code dsl}과 {@code fulltext}는 질의 문자열만 쓰고, 그 밖(basic)은 타입, classification, 질의,
     * 속성 필터 하나를 조합한 basic search 요청을 만듭니다. 빈 입력은 null로 바꿔 조건에서 뺍니다.
     */
    private AtlasSearchResult runSearch(SearchForm form) {
        return switch (form.mode()) {
            case "dsl" -> client.search().dsl(form.query(), form.limit(), form.offset());
            case "fulltext" -> client.search().fullText(form.query(), form.limit(), form.offset(), form.excludeDeleted());
            default -> {
                SearchParameters params = new SearchParameters();
                params.setTypeName(blankToNull(form.typeName()));
                params.setClassification(blankToNull(form.classification()));
                params.setQuery(blankToNull(form.query()));
                params.setExcludeDeletedEntities(form.excludeDeleted());
                params.setLimit(form.limit());
                params.setOffset(form.offset());
                if (form.filterAttribute() != null && !form.filterAttribute().isBlank()) {
                    FilterCriteria.Operator operator = FilterCriteria.Operator.valueOf(form.filterOperator());
                    params.setEntityFilters(FilterCriteria.of(form.filterAttribute().trim(), operator,
                            form.filterValue()));
                }
                yield client.search().basic(params);
            }
        };
    }

    /**
     * 검색 종류와 관계없이 결과에서 엔티티 헤더 목록을 꺼냅니다.
     * full-text 검색 결과는 엔티티를 {@code {entity, score}} 형태로 한 번 감싸서 주므로 {@code entity}만 꺼내 변환합니다.
     */
    private static List<AtlasEntityHeader> entities(AtlasSearchResult result) {
        if (result.getEntities() != null) {
            return result.getEntities();
        }
        if (result.getFullTextResult() != null) {
            return result.getFullTextResult().stream()
                    .map(m -> m.get("entity"))
                    .filter(Objects::nonNull)
                    .map(e -> AtlasJson.convert(e, AtlasEntityHeader.class))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /**
     * 검색 폼 입력값입니다. 요청 파라미터에서 바인딩되며, 빈 값에는 생성자에서 기본값을 채웁니다.
     *
     * @param search          폼을 제출했을 때만 null이 아닌 값
     * @param mode            검색 종류: {@code basic}(기본값), {@code dsl}, {@code fulltext}
     * @param typeName        basic search의 엔티티 타입
     * @param classification  basic search의 classification
     * @param query           검색어(basic) 또는 DSL·full-text 질의
     * @param filterAttribute basic search 속성 필터의 속성 이름. 비어 있으면 필터를 쓰지 않습니다.
     * @param filterOperator  속성 필터 연산자({@link FilterCriteria.Operator} 이름, 기본값 {@code EQ})
     * @param filterValue     속성 필터 값
     * @param includeDeleted  삭제된 엔티티도 포함할지(기본값 {@code false})
     * @param limit           한 페이지 결과 수(기본값 25)
     * @param offset          결과 시작 위치(기본값 0)
     */
    public record SearchForm(String search, String mode, String typeName, String classification, String query,
                      String filterAttribute, String filterOperator, String filterValue, Boolean includeDeleted,
                      Integer limit, Integer offset) {

        /** 비어 있는 값에 기본값을 채웁니다. */
        public SearchForm {
            mode = mode == null ? "basic" : mode;
            includeDeleted = includeDeleted != null && includeDeleted;
            filterOperator = filterOperator == null ? FilterCriteria.Operator.EQ.name() : filterOperator;
            limit = limit == null ? 25 : limit;
            offset = offset == null ? 0 : offset;
        }

        /**
         * 아무것도 입력하지 않은(제출되지 않은) 폼을 만듭니다.
         *
         * @return 기본값만 채운 폼
         */
        public static SearchForm empty() {
            return new SearchForm(null, null, null, null, null, null, null, null, false, null, null);
        }

        /**
         * 폼이 제출되었는지 알려 줍니다.
         *
         * @return {@code search} 파라미터가 있으면 {@code true}
         */
        public boolean submitted() {
            return search != null;
        }

        /**
         * 삭제된 엔티티를 결과에서 뺄지 알려 줍니다. Atlas API가 쓰는 방향으로 {@code includeDeleted}를 뒤집은 값입니다.
         *
         * @return 삭제된 엔티티를 빼야 하면 {@code true}
         */
        public boolean excludeDeleted() {
            return !includeDeleted;
        }

        /**
         * 같은 검색을 다른 위치(페이지)에서 다시 하는 쿼리 문자열을 만듭니다. 이전·다음 페이지 링크에 씁니다.
         * 빈 값은 빼고, 값은 URL 인코딩합니다.
         *
         * @param newOffset 새 시작 위치. 음수면 0으로 바꿉니다.
         * @return {@code search=1}로 시작하는 쿼리 문자열({@code ?} 제외)
         */
        public String pageQuery(int newOffset) {
            StringBuilder sb = new StringBuilder("search=1");
            append(sb, "mode", mode);
            append(sb, "typeName", typeName);
            append(sb, "classification", classification);
            append(sb, "query", query);
            append(sb, "filterAttribute", filterAttribute);
            append(sb, "filterOperator", filterOperator);
            append(sb, "filterValue", filterValue);
            append(sb, "includeDeleted", String.valueOf(includeDeleted));
            append(sb, "limit", String.valueOf(limit));
            append(sb, "offset", String.valueOf(Math.max(0, newOffset)));
            return sb.toString();
        }

        /** 값이 비어 있지 않을 때만 {@code &name=value}를 URL 인코딩해 덧붙입니다. */
        private static void append(StringBuilder sb, String name, String value) {
            if (value != null && !value.isBlank()) {
                sb.append('&').append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
    }

    // ---------------------------------------------------------------- 상세 보기와 변경

    /**
     * {@code GET /entities/{guid}}: 엔티티 상세 페이지를 보여 줍니다.
     * <p>
     * 참조 엔티티 정보까지 포함한 엔티티, 전체 JSON, business metadata, 최근 audit 이벤트
     * ({@link #AUDIT_EVENTS}개), 그리고 추가 폼에서 고를 classification·business metadata 타입 이름 목록을 넣습니다.
     * 변경 요청을 처리한 메서드들도 마지막에 이 메서드를 불러 바뀐 상태를 다시 보여 줍니다.
     * audit 조회만 실패하면 페이지는 그대로 보여 주고 실패 내용만 함께 표시합니다.
     */
    @GetMapping("/{guid}")
    String detail(@PathVariable String guid, Model model) {
        AtlasEntityWithExtInfo info = client.entities().get(guid);
        AtlasEntity entity = info.getEntity();
        model.addAttribute("info", info);
        model.addAttribute("entity", entity);
        model.addAttribute("json", AtlasJson.toPrettyJson(info));
        if (entity.getBusinessAttributes() != null && !entity.getBusinessAttributes().isEmpty()) {
            model.addAttribute("businessJson", AtlasJson.toPrettyJson(entity.getBusinessAttributes()));
        }
        model.addAttribute("deleted", entity.getStatus() == EntityStatus.DELETED);
        model.addAttribute("audits", Actions.call(model, () -> client.entities().getAuditEvents(guid, null, AUDIT_EVENTS)));
        List<AtlasTypeDefHeader> headers = client.types().getHeaders();
        model.addAttribute("classificationTypes", typeNames(headers, TypeCategory.CLASSIFICATION));
        model.addAttribute("businessMetadataTypes", typeNames(headers, TypeCategory.BUSINESS_METADATA));
        return "entities/detail";
    }

    /**
     * {@code POST /entities/{guid}/attribute}: 속성 하나를 바꿉니다.
     * 값은 가능하면 JSON으로 해석합니다({@code 42}, {@code true}, {@code ["a"]}). 해석할 수 없으면 문자열 그대로 씁니다.
     */
    @PostMapping("/{guid}/attribute")
    String updateAttribute(@PathVariable String guid, @RequestParam String name, @RequestParam String value,
                           Model model) {
        Actions.run(model, "속성 '" + name + "'을 수정했습니다.",
                () -> client.entities().updateAttribute(guid, name, jsonOrString(value)));
        return detail(guid, model);
    }

    /**
     * {@code POST /entities/{guid}/classifications}: 엔티티에 classification을 붙입니다.
     *
     * @param propagate classification을 리니지를 따라 하위 엔티티로 전파할지
     */
    @PostMapping("/{guid}/classifications")
    String addClassification(@PathVariable String guid, @RequestParam String name,
                             @RequestParam(defaultValue = "false") boolean propagate, Model model) {
        AtlasClassification classification = new AtlasClassification(name);
        classification.setPropagate(propagate);
        Actions.run(model, "classification '" + name + "'을 추가했습니다.",
                () -> client.entities().addClassification(guid, classification));
        return detail(guid, model);
    }

    /** {@code POST /entities/{guid}/classifications/{name}/delete}: 엔티티에서 classification을 뗍니다. */
    @PostMapping("/{guid}/classifications/{name}/delete")
    String removeClassification(@PathVariable String guid, @PathVariable String name, Model model) {
        Actions.run(model, "classification '" + name + "'을 제거했습니다.",
                () -> client.entities().removeClassification(guid, name));
        return detail(guid, model);
    }

    /**
     * {@code POST /entities/{guid}/labels}: 레이블을 바꿉니다. 레이블은 쉼표·공백으로 구분해 입력합니다.
     *
     * @param op {@code add}(추가), {@code remove}(제거), 그 밖의 값은 {@code set}(입력한 것으로 통째로 교체)
     */
    @PostMapping("/{guid}/labels")
    String labels(@PathVariable String guid, @RequestParam String labels, @RequestParam String op, Model model) {
        Set<String> values = Arrays.stream(labels.split("[,\\s]+")).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Actions.run(model, "label을 변경했습니다 (" + op + ").", () -> {
            switch (op) {
                case "add" -> client.entities().addLabels(guid, values);
                case "remove" -> client.entities().removeLabels(guid, values);
                default -> client.entities().setLabels(guid, values);
            }
        });
        return detail(guid, model);
    }

    /**
     * {@code POST /entities/{guid}/business-metadata}: business metadata를 JSON으로 받아 저장합니다.
     * 형식은 {@code {"bmName": {"attr": value}}}이며, {@code overwrite}가 아니면 기존 값에 합칩니다.
     */
    @PostMapping("/{guid}/business-metadata")
    String businessMetadata(@PathVariable String guid, @RequestParam String json,
                            @RequestParam(defaultValue = "false") boolean overwrite, Model model) {
        Actions.run(model, "business metadata를 저장했습니다.", () -> client.entities().setBusinessMetadata(guid,
                AtlasJson.fromJson(json, new TypeReference<Map<String, Map<String, Object>>>() {
                }), overwrite));
        return detail(guid, model);
    }

    /**
     * {@code POST /entities/{guid}/delete}: 엔티티를 삭제합니다. Atlas의 삭제는 soft delete라서 상태만 DELETED로 바뀌고
     * 엔티티는 남아 있으므로 상세 페이지를 다시 보여 줍니다.
     */
    @PostMapping("/{guid}/delete")
    String delete(@PathVariable String guid, Model model) {
        Actions.run(model, "삭제했습니다 (soft delete). purge하면 완전히 지워집니다.", () -> client.entities().delete(guid));
        return detail(guid, model);
    }

    /**
     * {@code POST /entities/{guid}/purge}: 삭제된 엔티티를 purge해 완전히 지웁니다.
     * 성공하면 엔티티가 더 이상 없으므로 빈 검색 페이지로 갑니다. 실패하면 상세 페이지에 실패 내용을 보여 줍니다.
     */
    @PostMapping("/{guid}/purge")
    String purge(@PathVariable String guid, Model model) {
        EntityMutationResponse response = Actions.call(model, () -> client.admin().purge(List.of(guid)));
        if (response == null) {
            return detail(guid, model);
        }
        model.addAttribute(Actions.MESSAGE, "purge했습니다: " + response.summary());
        model.addAttribute("form", SearchForm.empty());
        model.addAttribute("operators", FilterCriteria.Operator.values());
        return "entities/search";
    }

    // ---------------------------------------------------------------- 가져오기

    /** {@code GET /entities/import}: 예제 YAML을 채운 가져오기 폼을 보여 줍니다. */
    @GetMapping("/import")
    String importForm(Model model) {
        model.addAttribute("text", SAMPLE_ENTITIES);
        model.addAttribute("format", "yaml");
        return "entities/import";
    }

    /**
     * {@code POST /entities/import}: CLI의 {@code atlas entities import}처럼 엔티티를 가져옵니다.
     * qualifiedName 기준으로 있으면 갱신하고 없으면 만들며, {@link #IMPORT_BATCH_SIZE}개씩 나눠 보내고,
     * 같은 배치 안의 엔티티끼리의 참조는 서로 연결합니다.
     * <p>
     * 입력은 텍스트 또는 업로드 파일이며, 파일이 있으면 파일을 씁니다. {@code dryRun}이면 파싱 결과만 보여 주고
     * Atlas에는 보내지 않습니다. 파싱이나 등록이 실패하면 같은 페이지에 실패 내용을 보여 줍니다.
     *
     * @param text        직접 입력한 내용
     * @param format      {@code csv}, {@code json}, {@code yaml} 중 하나. 파일을 올리면 무시하고 파일 확장자로 정합니다.
     * @param defaultType 입력에 typeName이 없는 엔티티에 쓸 타입
     * @param file        업로드한 파일(선택)
     * @param dryRun      {@code true}면 Atlas에 보내지 않고 파싱 결과만 보여 줍니다.
     * @param model       뷰 모델
     * @return 뷰 이름 {@code entities/import}
     */
    @PostMapping("/import")
    String importEntities(@RequestParam(required = false) String text, @RequestParam String format,
                          @RequestParam(required = false) String defaultType,
                          @RequestParam(required = false) MultipartFile file,
                          @RequestParam(defaultValue = "false") boolean dryRun, Model model) {
        String fileName = "input." + format;
        String content = text;
        if (file != null && !file.isEmpty()) {
            fileName = Objects.requireNonNullElse(file.getOriginalFilename(), fileName);
            content = read(file);
        }
        model.addAttribute("text", content);
        model.addAttribute("format", format);
        model.addAttribute("defaultType", defaultType);
        String name = fileName;
        String input = content == null ? "" : content;
        List<AtlasEntity> entities = Actions.call(model, () -> EntityFiles.parse(name, input, blankToNull(defaultType)));
        if (entities == null) {
            return "entities/import";
        }
        model.addAttribute("parsed", entities);
        model.addAttribute("parsedJson", AtlasJson.toPrettyJson(entities));
        if (dryRun) {
            model.addAttribute(Actions.MESSAGE, entities.size() + "개 엔티티를 읽었습니다 (dry run, Atlas에 보내지 않음).");
            return "entities/import";
        }
        List<EntityMutationResponse> responses = new ArrayList<>();
        Actions.run(model, entities.size() + "개 엔티티를 등록했습니다.", () -> {
            for (int i = 0; i < entities.size(); i += IMPORT_BATCH_SIZE) {
                List<AtlasEntity> batch = EntityFiles.linkWithinBatch(
                        new ArrayList<>(entities.subList(i, Math.min(i + IMPORT_BATCH_SIZE, entities.size()))));
                responses.add(client.entities().createOrUpdate(batch));
            }
        });
        model.addAttribute("responses", responses);
        return "entities/import";
    }

    // ---------------------------------------------------------------- 도우미

    /** 헤더 중 주어진 카테고리의 타입 이름만 정렬해 돌려줍니다. */
    private static List<String> typeNames(List<AtlasTypeDefHeader> headers, TypeCategory category) {
        return headers.stream().filter(h -> h.getCategory() == category).map(AtlasTypeDefHeader::getName).sorted()
                .collect(Collectors.toList());
    }

    /**
     * 값을 JSON으로 해석해 보고, 해석할 수 없으면 원래 문자열을 돌려줍니다.
     * 그래서 {@code 42}는 숫자로, {@code hello}처럼 따옴표 없는 글자는 문자열로 저장됩니다.
     */
    static Object jsonOrString(String value) {
        try {
            return AtlasJson.fromJson(value, Object.class);
        } catch (RuntimeException e) {
            return value;
        }
    }

    /** null이거나 공백뿐인 값은 null로, 그 밖에는 앞뒤 공백을 지운 값으로 바꿉니다. */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 업로드 파일을 UTF-8 문자열로 읽습니다. 읽기 실패는 {@link UncheckedIOException}으로 감싸 던집니다. */
    private static String read(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
