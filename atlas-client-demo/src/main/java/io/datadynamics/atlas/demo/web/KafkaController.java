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

/**
 * atlas-client-kafka 모듈 데모 페이지입니다. {@code ATLAS_HOOK} 토픽으로 hook 메시지를 보내고,
 * {@code ATLAS_ENTITIES} 토픽의 엔티티 변경 이벤트를 보여 줍니다.
 * <p>
 * Kafka가 설정되지 않으면 {@link AtlasHookPublisher}와 {@link EntityEventHub} 빈이 없으므로
 * {@link ObjectProvider}로 있을 때만 꺼내 씁니다.
 */
@Controller
class KafkaController {

    /** 생성 폼에 미리 채워 두는 예제 엔티티(YAML)입니다. */
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

    /** {@code GET /kafka}: 예제 값을 채운 발행 폼과 최근 엔티티 이벤트를 보여 줍니다. */
    @GetMapping("/kafka")
    String page(Model model) {
        model.addAttribute("entities", SAMPLE_ENTITIES);
        model.addAttribute("typeName", "DataSet");
        model.addAttribute("qualifiedName", "console.from_kafka@demo");
        model.addAttribute("attributes", "{\"description\": \"partially updated through ATLAS_HOOK\"}");
        return show(model);
    }

    /**
     * {@code POST /kafka/publish}: 입력한 내용을 hook 메시지로 {@code ATLAS_HOOK}에 보냅니다.
     * <p>
     * 입력 값은 결과와 함께 폼에 다시 채워 보여 줍니다. Kafka가 설정되지 않았으면
     * {@link IllegalArgumentException}이 나서 오류 페이지(400)로 갑니다. 입력을 해석하지 못했거나
     * {@code op}를 모르거나 발행이 실패하면 같은 페이지에 실패 내용을 보여 줍니다.
     *
     * @param op            {@code create}(엔티티를 YAML/JSON으로 입력), {@code partial}(속성을 JSON으로 입력)
     *                      또는 {@code delete}
     * @param entities      {@code create}일 때 보낼 엔티티 목록
     * @param typeName      {@code partial}/{@code delete} 대상 엔티티의 typeName
     * @param qualifiedName {@code partial}/{@code delete} 대상 엔티티의 qualifiedName
     * @param attributes    {@code partial}일 때 바꿀 속성(JSON 객체)
     * @param model         뷰 모델
     * @return 뷰 이름 {@code kafka}
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

    /** 이벤트 허브 상태와 최근 이벤트를 모델에 넣고 {@code kafka} 뷰를 고릅니다. 허브가 없으면 빈 목록을 넣습니다. */
    private String show(Model model) {
        EntityEventHub hub = events.getIfAvailable();
        model.addAttribute("hub", hub);
        model.addAttribute("events", hub == null ? List.of() : hub.recent());
        return "kafka";
    }

    /**
     * {@code GET /events/stream}: 새 엔티티 이벤트를 받는 SSE 스트림을 엽니다. 페이지의 스크립트가 구독합니다.
     *
     * @throws IllegalArgumentException Kafka가 설정되지 않은 경우
     */
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
