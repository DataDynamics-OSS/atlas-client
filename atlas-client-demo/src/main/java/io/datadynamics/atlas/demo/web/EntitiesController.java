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

/** Search, entity detail and changes, file import. */
@Controller
@RequestMapping("/entities")
class EntitiesController {

    static final int AUDIT_EVENTS = 25;
    static final int IMPORT_BATCH_SIZE = 100;

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

    // ---------------------------------------------------------------- search

    /** Search form; see {@link SearchForm#mode}. Runs the search only when the form was submitted. */
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

    /** Entity headers of any search kind; full-text results wrap them in {@code {entity, score}}. */
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
     * @param search non-null when the form was submitted
     * @param mode   basic, dsl or fulltext
     */
    public record SearchForm(String search, String mode, String typeName, String classification, String query,
                      String filterAttribute, String filterOperator, String filterValue, Boolean includeDeleted,
                      Integer limit, Integer offset) {

        public SearchForm {
            mode = mode == null ? "basic" : mode;
            includeDeleted = includeDeleted != null && includeDeleted;
            filterOperator = filterOperator == null ? FilterCriteria.Operator.EQ.name() : filterOperator;
            limit = limit == null ? 25 : limit;
            offset = offset == null ? 0 : offset;
        }

        public static SearchForm empty() {
            return new SearchForm(null, null, null, null, null, null, null, null, false, null, null);
        }

        public boolean submitted() {
            return search != null;
        }

        public boolean excludeDeleted() {
            return !includeDeleted;
        }

        /** Query string of the same search at another offset. */
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

        private static void append(StringBuilder sb, String name, String value) {
            if (value != null && !value.isBlank()) {
                sb.append('&').append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
    }

    // ---------------------------------------------------------------- detail and changes

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

    /** Sets one attribute; the value is parsed as JSON when possible ({@code 42}, {@code true}, {@code ["a"]}). */
    @PostMapping("/{guid}/attribute")
    String updateAttribute(@PathVariable String guid, @RequestParam String name, @RequestParam String value,
                           Model model) {
        Actions.run(model, "속성 '" + name + "'을 수정했습니다.",
                () -> client.entities().updateAttribute(guid, name, jsonOrString(value)));
        return detail(guid, model);
    }

    @PostMapping("/{guid}/classifications")
    String addClassification(@PathVariable String guid, @RequestParam String name,
                             @RequestParam(defaultValue = "false") boolean propagate, Model model) {
        AtlasClassification classification = new AtlasClassification(name);
        classification.setPropagate(propagate);
        Actions.run(model, "classification '" + name + "'을 추가했습니다.",
                () -> client.entities().addClassification(guid, classification));
        return detail(guid, model);
    }

    @PostMapping("/{guid}/classifications/{name}/delete")
    String removeClassification(@PathVariable String guid, @PathVariable String name, Model model) {
        Actions.run(model, "classification '" + name + "'을 제거했습니다.",
                () -> client.entities().removeClassification(guid, name));
        return detail(guid, model);
    }

    /** {@code op}: add, remove or set. */
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

    /** Business metadata as JSON: {@code {"bmName": {"attr": value}}}; merged unless {@code overwrite}. */
    @PostMapping("/{guid}/business-metadata")
    String businessMetadata(@PathVariable String guid, @RequestParam String json,
                            @RequestParam(defaultValue = "false") boolean overwrite, Model model) {
        Actions.run(model, "business metadata를 저장했습니다.", () -> client.entities().setBusinessMetadata(guid,
                AtlasJson.fromJson(json, new TypeReference<Map<String, Map<String, Object>>>() {
                }), overwrite));
        return detail(guid, model);
    }

    @PostMapping("/{guid}/delete")
    String delete(@PathVariable String guid, Model model) {
        Actions.run(model, "삭제했습니다 (soft delete). purge하면 완전히 지워집니다.", () -> client.entities().delete(guid));
        return detail(guid, model);
    }

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

    // ---------------------------------------------------------------- import

    @GetMapping("/import")
    String importForm(Model model) {
        model.addAttribute("text", SAMPLE_ENTITIES);
        model.addAttribute("format", "yaml");
        return "entities/import";
    }

    /**
     * Imports like {@code atlas entities import}: upsert by qualifiedName in batches, linking
     * references between entities of the same batch.
     *
     * @param format csv, json or yaml; ignored when a file is uploaded (its extension decides)
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

    // ---------------------------------------------------------------- helpers

    private static List<String> typeNames(List<AtlasTypeDefHeader> headers, TypeCategory category) {
        return headers.stream().filter(h -> h.getCategory() == category).map(AtlasTypeDefHeader::getName).sorted()
                .collect(Collectors.toList());
    }

    static Object jsonOrString(String value) {
        try {
            return AtlasJson.fromJson(value, Object.class);
        } catch (RuntimeException e) {
            return value;
        }
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String read(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
