package io.datadynamics.atlas.demo.events;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.kafka.AtlasEntityChangeListener;
import io.datadynamics.atlas.client.kafka.message.EntityChangeEvent;
import io.datadynamics.atlas.demo.config.DemoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link AtlasEntityChangeListener}로 {@code ATLAS_ENTITIES} 토픽을 구독하고, 최근 이벤트를 보관하며,
 * 새 이벤트를 브라우저로 밀어 보냅니다(server-sent events, SSE).
 * <p>
 * Kafka 주소({@code atlas.kafka.bootstrap-servers})가 설정된 경우에만 빈으로 등록됩니다.
 * {@link SmartLifecycle}이므로 애플리케이션이 시작될 때 구독을 시작하고 종료될 때 멈춥니다.
 * 리스너 스레드와 웹 요청 스레드가 함께 쓰므로, 이력은 {@code synchronized}로, 구독자 목록은
 * {@link CopyOnWriteArrayList}로 보호합니다.
 */
@Component
@ConditionalOnExpression("'${atlas.kafka.bootstrap-servers:}' != ''")
public class EntityEventHub implements SmartLifecycle {

    /** 보관하는 최근 이벤트의 최대 개수입니다. 넘으면 오래된 것부터 버립니다. */
    static final int HISTORY_SIZE = 200;

    private static final Logger LOG = LoggerFactory.getLogger(EntityEventHub.class);

    private final DemoProperties properties;
    private final Deque<Event> history = new ArrayDeque<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private volatile AtlasEntityChangeListener listener;
    private volatile Instant startedAt;

    /**
     * 허브를 만듭니다. 구독은 {@link #start()}에서 시작합니다.
     *
     * @param properties Kafka 주소를 담은 데모 설정
     */
    public EntityEventHub(DemoProperties properties) {
        this.properties = properties;
    }

    /**
     * 데모 화면에 보여 줄 형태로 바꾼 엔티티 변경 이벤트입니다.
     *
     * @param time          이벤트 시각. 알림에 시각이 없으면 받은 시각
     * @param operation     변경 종류(알림의 operationType)
     * @param typeName      변경된 엔티티의 typeName
     * @param qualifiedName 변경된 엔티티의 qualifiedName
     * @param guid          변경된 엔티티의 guid
     * @param json          알림 전체를 보기 좋게 들여쓰기한 JSON
     */
    public record Event(Instant time, String operation, String typeName, String qualifiedName, String guid,
                        String json) {

        /** Kafka 알림을 화면용 이벤트로 바꿉니다. */
        static Event of(EntityChangeEvent event) {
            return new Event(event.time() != null ? event.time() : Instant.now(),
                    String.valueOf(event.getOperationType()), event.typeName(), event.qualifiedName(), event.guid(),
                    AtlasJson.toPrettyJson(event));
        }

        /** SSE로 보낼 JSON을 만들기 위해 필드를 순서가 유지되는 맵으로 바꿉니다. */
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("time", time.toString());
            map.put("operation", operation);
            map.put("typeName", typeName);
            map.put("qualifiedName", qualifiedName);
            map.put("guid", guid);
            map.put("json", json);
            return map;
        }
    }

    /**
     * {@code ATLAS_ENTITIES} 구독을 시작합니다. 받은 알림은 {@link #publish}로 넘깁니다.
     */
    @Override
    public void start() {
        // 매번 무작위 이름의 전용 consumer group을 씁니다. 그래서 데모 인스턴스마다 다른 인스턴스와 메시지를
        // 나눠 받지 않고 모든 이벤트를 받으며, 지금 이후의 이벤트부터 받습니다.
        listener = AtlasEntityChangeListener.builder()
                .bootstrapServers(properties.kafka().bootstrapServers())
                .groupId("atlas-client-demo-" + UUID.randomUUID())
                .handler(this::publish)
                .build()
                .start();
        startedAt = Instant.now();
        LOG.info("Listening to Atlas entity changes on {}", properties.kafka().bootstrapServers());
    }

    /** 리스너를 닫고, 열려 있는 모든 SSE 연결을 정상 종료합니다. */
    @Override
    public void stop() {
        AtlasEntityChangeListener current = listener;
        if (current != null) {
            current.close();
        }
        emitters.forEach(SseEmitter::complete);
    }

    /**
     * 리스너가 실행 중인지 알려 줍니다.
     *
     * @return 리스너가 만들어졌고 아직 멈추지 않았으면 {@code true}
     */
    @Override
    public boolean isRunning() {
        AtlasEntityChangeListener current = listener;
        return current != null && current.isRunning();
    }

    /**
     * 구독을 시작한 시각을 돌려줍니다.
     *
     * @return 시작 시각. 아직 시작하지 않았으면 null
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * 리스너가 실패로 멈췄다면 그 이유를 돌려줍니다.
     *
     * @return 실패 원인 예외의 {@code toString()}. 멈추지 않았거나 시작 전이면 null
     */
    public String failure() {
        AtlasEntityChangeListener current = listener;
        return current == null ? null : current.failure().map(Throwable::toString).orElse(null);
    }

    /**
     * 보관 중인 최근 이벤트의 복사본을 돌려줍니다.
     *
     * @return 최근 이벤트가 앞에 오는 목록(최대 {@link #HISTORY_SIZE}개)
     */
    public List<Event> recent() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    /**
     * 브라우저 하나를 새 이벤트 구독자로 등록합니다.
     * <p>
     * 제한 시간 없는({@code 0L}) {@link SseEmitter}를 만들고, 연결이 끝나거나 시간 초과·오류가 나면
     * 구독자 목록에서 뺍니다.
     *
     * @return 컨트롤러가 응답으로 돌려줄 SSE emitter
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        return emitter;
    }

    /**
     * 받은 알림을 이력 맨 앞에 넣고, 모든 구독자에게 {@code entity} 이름의 SSE 이벤트로 보냅니다.
     * 보내기에 실패한 구독자(브라우저가 닫힌 경우 등)는 목록에서 뺍니다.
     */
    void publish(EntityChangeEvent change) {
        Event event = Event.of(change);
        synchronized (history) {
            history.addFirst(event);
            while (history.size() > HISTORY_SIZE) {
                history.removeLast();
            }
        }
        String data = AtlasJson.toJson(event.toMap());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("entity").data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
