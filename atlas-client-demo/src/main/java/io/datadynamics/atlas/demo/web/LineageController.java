package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.kafka.AtlasHookPublisher;
import io.datadynamics.atlas.client.kafka.PublishResult;
import io.datadynamics.atlas.client.lineage.AtlasLineage;
import io.datadynamics.atlas.client.lineage.EntityRef;
import io.datadynamics.atlas.client.lineage.LineageRegistration;
import io.datadynamics.atlas.client.lineage.MissingEntityPolicy;
import io.datadynamics.atlas.client.lineage.ProcessTypeResolver;
import io.datadynamics.atlas.client.lineage.ProcessBuilder;
import io.datadynamics.atlas.client.lineage.graph.LineageGraph;
import io.datadynamics.atlas.client.model.lineage.AtlasLineageInfo.LineageDirection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

/** Lineage graph, impact analysis and registration (atlas-client-lineage). */
@Controller
@RequestMapping("/lineage")
class LineageController {

    private final AtlasClient client;
    private final ProcessTypeResolver processTypes;
    private final ObjectProvider<AtlasHookPublisher> hook;

    LineageController(AtlasClient client, ProcessTypeResolver processTypes, ObjectProvider<AtlasHookPublisher> hook) {
        this.client = client;
        this.processTypes = processTypes;
        this.hook = hook;
    }

    /**
     * Graph of an entity given by guid, or by type and qualifiedName.
     *
     * @param depth number of process hops in each direction
     */
    public record GraphForm(String guid, String typeName, String qualifiedName, LineageDirection direction, Integer depth,
                            Boolean includeDeleted) {

        public GraphForm {
            direction = direction == null ? LineageDirection.BOTH : direction;
            depth = depth == null ? 3 : depth;
            includeDeleted = includeDeleted != null && includeDeleted;
        }

        public boolean submitted() {
            return guid != null && !guid.isBlank() || qualifiedName != null && !qualifiedName.isBlank();
        }
    }

    @GetMapping
    String graph(@ModelAttribute("form") GraphForm form, Model model) {
        model.addAttribute("directions", LineageDirection.values());
        if (!form.submitted()) {
            return "lineage/graph";
        }
        AtlasLineage lineage = new AtlasLineage(client, processTypes).includeDeletedRelationships(form.includeDeleted());
        LineageGraph graph = Actions.call(model, () -> form.guid() != null && !form.guid().isBlank()
                ? lineage.graph(form.guid().trim(), form.direction(), form.depth())
                : lineage.graph(EntityRef.of(form.typeName().trim(), form.qualifiedName().trim()),
                form.direction(), form.depth()));
        if (graph != null) {
            model.addAttribute("graph", graph);
            model.addAttribute("mermaid", graph.toMermaid());
            model.addAttribute("dot", graph.toDot());
            if (graph.base() != null) {
                model.addAttribute("impacts", graph.impactOf(graph.base()));
                model.addAttribute("sources", graph.sourcesOf(graph.base()));
            }
        }
        return "lineage/graph";
    }

    /**
     * Entities are written one per line as {@code type:qualifiedName}; column lineage as
     * {@code outType:outQn <- inType:inQn, inType:inQn}.
     *
     * @param via rest (one bulk request) or kafka (ATLAS_HOOK message)
     */
    public record RegisterForm(String processType, String qualifiedName, String name, String description, String inputs,
                        String outputs, String columnLineage, MissingEntityPolicy missingEntities, String via) {

        public RegisterForm {
            missingEntities = missingEntities == null ? MissingEntityPolicy.FAIL : missingEntities;
            via = via == null ? "rest" : via;
        }

        public static RegisterForm sample() {
            return new RegisterForm("Process", "console.sample_etl@demo", "sample_etl", null,
                    "DataSet:console.sample_a@demo", "DataSet:console.sample_b@demo", null,
                    MissingEntityPolicy.CREATE, "rest");
        }
    }

    @GetMapping("/register")
    String registerForm(Model model) {
        model.addAttribute("form", RegisterForm.sample());
        model.addAttribute("policies", MissingEntityPolicy.values());
        return "lineage/register";
    }

    @PostMapping("/register")
    String register(@ModelAttribute("form") RegisterForm form, Model model) {
        model.addAttribute("policies", MissingEntityPolicy.values());
        AtlasLineage lineage = new AtlasLineage(client, processTypes).missingEntities(form.missingEntities());
        ProcessBuilder process = Actions.call(model, () -> build(lineage, form));
        if (process == null) {
            return "lineage/register";
        }
        if ("kafka".equals(form.via())) {
            AtlasHookPublisher publisher = hook.getIfAvailable();
            if (publisher == null) {
                model.addAttribute(Actions.FAILURE, Failure.of(new IllegalStateException("Kafka가 설정되지 않았습니다.")));
                return "lineage/register";
            }
            PublishResult result = Actions.call(model, () -> publisher.createOrUpdate(process.toEntities()));
            model.addAttribute("published", result);
            if (result != null) {
                model.addAttribute("payload", AtlasJson.toPrettyJson(process.toEntities()));
            }
        } else {
            LineageRegistration registration = Actions.call(model, process::register);
            model.addAttribute("registration", registration);
        }
        return "lineage/register";
    }

    private static ProcessBuilder build(AtlasLineage lineage, RegisterForm form) {
        require(form.processType(), "process type");
        require(form.qualifiedName(), "qualifiedName");
        ProcessBuilder process = lineage.process(form.processType().trim(), form.qualifiedName().trim())
                .inputs(refs(form.inputs()).toArray(EntityRef[]::new))
                .outputs(refs(form.outputs()).toArray(EntityRef[]::new));
        if (form.name() != null && !form.name().isBlank()) {
            process.name(form.name().trim());
        }
        if (form.description() != null && !form.description().isBlank()) {
            process.description(form.description().trim());
        }
        for (String line : lines(form.columnLineage())) {
            String[] parts = line.split("<-", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Column lineage must look like 'outType:outQn <- inType:inQn, ...': " + line);
            }
            process.columnLineage(ref(parts[0]), refs(parts[1].replace(',', '\n')).toArray(EntityRef[]::new));
        }
        return process;
    }

    static List<EntityRef> refs(String text) {
        List<EntityRef> refs = new ArrayList<>();
        for (String line : lines(text)) {
            refs.add(ref(line));
        }
        return refs;
    }

    /** {@code type:qualifiedName}; type names never contain ':' but qualified names may. */
    static EntityRef ref(String text) {
        String value = text.trim();
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            throw new IllegalArgumentException("Expected type:qualifiedName but got '" + value + "'");
        }
        return EntityRef.of(value.substring(0, colon).trim(), value.substring(colon + 1).trim());
    }

    private static List<String> lines(String text) {
        if (text == null) {
            return List.of();
        }
        return text.lines().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#")).toList();
    }

    private static void require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " is required");
        }
    }
}
