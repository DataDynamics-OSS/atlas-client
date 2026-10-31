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

/** Type definitions: browse, plan/apply (atlas-client-typedef), safe delete, export. */
@Controller
@RequestMapping("/types")
class TypesController {

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

    @GetMapping("/{name}")
    String detail(@PathVariable String name, Model model) {
        AtlasBaseTypeDef def = client.types().findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("No type named '" + name + "'"));
        model.addAttribute("def", def);
        model.addAttribute("json", AtlasJson.toPrettyJson(def));
        model.addAttribute("yaml", TypeDefFiles.toYaml(manager.export(List.of(name))));
        return "types/detail";
    }

    @GetMapping("/plan")
    String planForm(@RequestParam(required = false) String from, Model model) {
        String definition = SAMPLE_DEFINITION;
        if (from != null && !from.isBlank()) {
            definition = TypeDefFiles.toYaml(manager.export(names(from)));
        }
        model.addAttribute("definition", definition);
        return "types/plan";
    }

    /** {@code action=plan} only shows the plan; {@code action=apply} plans again and applies it. */
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

    @GetMapping("/delete")
    String deleteForm(@RequestParam(required = false) String names, Model model) {
        model.addAttribute("names", names);
        if (names != null && !names.isBlank()) {
            model.addAttribute("plan", Actions.call(model, () -> manager.planDelete(names(names))));
        }
        return "types/delete";
    }

    @PostMapping("/delete")
    String delete(@RequestParam String names, Model model) {
        model.addAttribute("names", names);
        model.addAttribute("result", Actions.call(model, () -> manager.delete(names(names))));
        return "types/delete";
    }

    /** Downloads definitions as YAML, without server fields, by service type or by names. */
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

    /** Names separated by commas, spaces or new lines. */
    static List<String> names(String text) {
        return Arrays.stream(text.split("[,\\s]+")).filter(s -> !s.isBlank()).distinct().collect(Collectors.toList());
    }
}
