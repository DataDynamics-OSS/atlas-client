package io.datadynamics.atlas.demo.web;

import io.datadynamics.atlas.client.cli.EntityFiles;
import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.kafka.AtlasHookPublisher;
import io.datadynamics.atlas.client.kafka.PublishResult;
import io.datadynamics.atlas.client.model.instance.AtlasEntity;
import io.datadynamics.atlas.demo.events.EntityEventHub;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** ATLAS_HOOK publishing and ATLAS_ENTITIES events (atlas-client-kafka). */
@Controller
class KafkaController {

    static final String SAMPLE_ENTITIES = """
            - typeName: DataSet
              attributes:
                qualifiedName: console.from_kafka@demo
                name: from_kafka
                description: sent through ATLAS_HOOK
            """;

    private final ObjectProvider<AtlasHookPublisher> hook;
    private final ObjectProvider<EntityEventHub> events;

    KafkaController(ObjectProvider<AtlasHookPublisher> hook, ObjectProvider<EntityEventHub> events) {
        this.hook = hook;
        this.events = events;
    }

    @GetMapping("/kafka")
    String page(Model model) {
        model.addAttribute("entities", SAMPLE_ENTITIES);
        model.addAttribute("typeName", "DataSet");
        model.addAttribute("qualifiedName", "console.from_kafka@demo");
        model.addAttribute("attributes", "{\"description\": \"partially updated through ATLAS_HOOK\"}");
        return show(model);
    }

    /**
     * @param op create (entities as YAML/JSON), partial (attributes as JSON) or delete
     */
    @PostMapping("/kafka/publish")
    String publish(@RequestParam String op, @RequestParam(required = false) String entities,
                   @RequestParam(required = false) String typeName, @RequestParam(required = false) String qualifiedName,
                   @RequestParam(required = false) String attributes, Model model) {
        model.addAttribute("op", op);
        model.addAttribute("entities", entities);
        model.addAttribute("typeName", typeName);
        model.addAttribute("qualifiedName", qualifiedName);
        model.addAttribute("attributes", attributes);
        AtlasHookPublisher publisher = hook.getIfAvailable();
        if (publisher == null) {
            throw new IllegalArgumentException("Kafka is not configured (atlas.kafka.bootstrap-servers)");
        }
        PublishResult result = Actions.call(model, () -> switch (op) {
            case "create" -> publisher.createOrUpdate(EntityFiles.parse("input.yaml", entities == null ? "" : entities, null));
            case "partial" -> {
                AtlasEntity patch = new AtlasEntity(typeName);
                Map<String, Object> values = AtlasJson.fromJson(attributes, new TypeReference<Map<String, Object>>() {
                });
                values.forEach(patch::setAttribute);
                yield publisher.partialUpdateByQualifiedName(typeName, qualifiedName, patch);
            }
            case "delete" -> publisher.deleteByQualifiedName(typeName, qualifiedName);
            default -> throw new IllegalArgumentException("Unknown operation: " + op);
        });
        model.addAttribute("published", result);
        return show(model);
    }

    private String show(Model model) {
        EntityEventHub hub = events.getIfAvailable();
        model.addAttribute("hub", hub);
        model.addAttribute("events", hub == null ? List.of() : hub.recent());
        return "kafka";
    }

    @GetMapping("/events/stream")
    @ResponseBody
    SseEmitter stream() {
        EntityEventHub hub = events.getIfAvailable();
        if (hub == null) {
            throw new IllegalArgumentException("Kafka is not configured (atlas.kafka.bootstrap-servers)");
        }
        return hub.subscribe();
    }
}
