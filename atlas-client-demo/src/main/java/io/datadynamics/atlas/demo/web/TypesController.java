package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.model.typedef.AtlasBaseTypeDef;
import io.datadynamics.atlas.client.model.typedef.AtlasTypeDefHeader;
import io.datadynamics.atlas.client.model.typedef.AtlasTypesDef;
import io.datadynamics.atlas.client.model.typedef.TypeCategory;
import io.datadynamics.atlas.client.typedef.file.TypeDefFiles;
import io.datadynamics.atlas.client.typedef.plan.TypeApplyResult;
import io.datadynamics.atlas.client.typedef.plan.TypeDefManager;
import io.datadynamics.atlas.client.typedef.plan.TypePlan;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 타입 정의 페이지입니다. 타입 목록·상세 보기, atlas-client-typedef 모듈의 plan/apply,
 * 미리 검사한 뒤 지우는 안전한 삭제, YAML 내보내기를 제공합니다.
 */
@Controller
@RequestMapping("/types")
class TypesController {

    /** plan 폼에 미리 채워 두는 예제 타입 정의(YAML)입니다. enum, 엔티티, classification을 하나씩 담고 있습니다. */
    static final String SAMPLE_DEFINITION = """
            enumDefs:
              - name: console_sample_tier
                elementDefs:
                  - { value: BRONZE, ordinal: 0 }
                  - { value: SILVER, ordinal: 1 }
                  - { value: GOLD, ordinal: 2 }
            entityDefs:
              - name: console_sample_table
                superTypes: [DataSet]
                attributeDefs:
                  - { name: tier, typeName: console_sample_tier, isOptional: true }
                  - { name: rowCount, typeName: long, isOptional: true }
            classificationDefs:
              - name: console_sample_PII
                entityTypes: [console_sample_table]
            """;

    private final AtlasClient client;
    private final TypeDefManager manager;

    TypesController(AtlasClient client, TypeDefManager manager) {
        this.client = client;
        this.manager = manager;
    }

    /**
     * {@code GET /types}: 타입 헤더 목록을 보여 줍니다.
     * <p>
     * 카테고리와 이름(대소문자를 구분하지 않는 부분 일치)으로 거르고, 카테고리 이름 다음 타입 이름 순으로 정렬합니다.
     *
     * @param category 보여 줄 타입 카테고리. 없으면 전체
     * @param q        타입 이름에 포함될 문자열. 없으면 거르지 않습니다.
     * @param model    뷰 모델
     * @return 뷰 이름 {@code types/list}
     */
    @GetMapping
    String list(@RequestParam(required = false) TypeCategory category,
                @RequestParam(required = false) String q, Model model) {
        String filter = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<AtlasTypeDefHeader> headers = client.types().getHeaders().stream()
                .filter(h -> category == null || h.getCategory() == category)
                .filter(h -> filter.isEmpty() || h.getName().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing((AtlasTypeDefHeader h) -> h.getCategory().name())
                        .thenComparing(AtlasTypeDefHeader::getName))
                .collect(Collectors.toList());
        model.addAttribute("headers", headers);
        model.addAttribute("categories", TypeCategory.values());
        model.addAttribute("category", category);
        model.addAttribute("q", q);
        return "types/list";
    }

    /**
     * {@code GET /types/{name}}: 타입 하나의 정의를 Atlas가 돌려준 JSON과 내보내기용 YAML로 보여 줍니다.
     *
     * @throws IllegalArgumentException 그런 이름의 타입이 없는 경우(오류 페이지로 갑니다)
     */
    @GetMapping("/{name}")
    String detail(@PathVariable String name, Model model) {
        AtlasBaseTypeDef def = client.types().findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("No type named '" + name + "'"));
        model.addAttribute("def", def);
        model.addAttribute("json", AtlasJson.toPrettyJson(def));
        model.addAttribute("yaml", TypeDefFiles.toYaml(manager.export(List.of(name))));
        return "types/detail";
    }

    /**
     * {@code GET /types/plan}: 타입 정의 입력 폼을 보여 줍니다.
     * {@code from}에 타입 이름들을 주면 그 타입의 현재 정의를 YAML로 채우고, 없으면 예제 정의를 채웁니다.
     */
    @GetMapping("/plan")
    String planForm(@RequestParam(required = false) String from, Model model) {
        String definition = SAMPLE_DEFINITION;
        if (from != null && !from.isBlank()) {
            definition = TypeDefFiles.toYaml(manager.export(names(from)));
        }
        model.addAttribute("definition", definition);
        return "types/plan";
    }

    /**
     * {@code POST /types/plan}: 입력한 YAML 정의를 현재 Atlas 타입과 비교한 plan을 보여 주고, 원하면 적용합니다.
     * <p>
     * {@code action=plan}이면 plan만 보여 주고, {@code action=apply}이면 plan을 다시 계산해 적용합니다.
     * YAML 파싱이나 plan 계산이 실패하면 그 단계에서 멈추고 같은 페이지에 실패 내용을 보여 줍니다.
     */
    @PostMapping("/plan")
    String plan(@RequestParam String definition, @RequestParam(defaultValue = "plan") String action, Model model) {
        model.addAttribute("definition", definition);
        AtlasTypesDef desired = Actions.call(model, () -> TypeDefFiles.fromYaml(definition));
        if (desired == null) {
            return "types/plan";
        }
        TypePlan plan = Actions.call(model, () -> manager.plan(desired));
        model.addAttribute("plan", plan);
        if (plan != null && "apply".equals(action)) {
            TypeApplyResult result = Actions.call(model, () -> manager.apply(plan));
            model.addAttribute("result", result);
        }
        return "types/plan";
    }

    /**
     * {@code GET /types/delete}: 삭제할 타입 이름 폼을 보여 주고, 이름이 있으면 삭제 plan을 미리 보여 줍니다.
     * 삭제 plan은 남은 하위 타입, 다른 타입의 참조, 남아 있는 엔티티처럼 Atlas가 삭제를 거부할 원인을 오류로 알려 줍니다.
     */
    @GetMapping("/delete")
    String deleteForm(@RequestParam(required = false) String names, Model model) {
        model.addAttribute("names", names);
        if (names != null && !names.isBlank()) {
            model.addAttribute("plan", Actions.call(model, () -> manager.planDelete(names(names))));
        }
        return "types/delete";
    }

    /** {@code POST /types/delete}: 삭제 plan을 다시 계산해 문제가 없으면 타입을 삭제하고 결과를 보여 줍니다. */
    @PostMapping("/delete")
    String delete(@RequestParam String names, Model model) {
        model.addAttribute("names", names);
        model.addAttribute("result", Actions.call(model, () -> manager.delete(names(names))));
        return "types/delete";
    }

    /**
     * {@code GET /types/export}: 타입 정의를 서버가 관리하는 필드(guid, 버전 등)를 뺀 YAML 파일로 내려받게 합니다.
     * <p>
     * {@code serviceType}을 주면 그 서비스 타입(예: {@code hive})의 모든 정의를 {@code <serviceType>.yaml}로,
     * 아니면 {@code names}에 적은 타입들을 {@code types.yaml}로 내려줍니다.
     *
     * @throws IllegalArgumentException 두 파라미터가 모두 비어 있는 경우
     */
    @GetMapping("/export")
    ResponseEntity<String> export(@RequestParam(required = false) String serviceType,
                                  @RequestParam(required = false) String names) {
        AtlasTypesDef typesDef;
        String fileName;
        if (serviceType != null && !serviceType.isBlank()) {
            typesDef = manager.exportByServiceType(serviceType.trim());
            fileName = serviceType.trim() + ".yaml";
        } else if (names != null && !names.isBlank()) {
            typesDef = manager.export(names(names));
            fileName = "types.yaml";
        } else {
            throw new IllegalArgumentException("Give serviceType or names to export");
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("application", "yaml", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(TypeDefFiles.toYaml(typesDef));
    }

    /**
     * 쉼표, 공백, 줄바꿈으로 구분된 이름들을 목록으로 나눕니다. 빈 항목과 중복은 뺍니다.
     *
     * @param text 입력 문자열
     * @return 입력 순서를 유지한 이름 목록
     */
    static List<String> names(String text) {
        return Arrays.stream(text.split("[,\\s]+")).filter(s -> !s.isBlank()).distinct().collect(Collectors.toList());
    }
}
