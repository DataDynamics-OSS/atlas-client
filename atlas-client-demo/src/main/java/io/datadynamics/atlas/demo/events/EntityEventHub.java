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
 * Listens to {@code ATLAS_ENTITIES} with {@link AtlasEntityChangeListener}, keeps the latest
 * events and pushes new ones to the browsers (server-sent events).
 */
@Component
@ConditionalOnExpression("'${atlas.kafka.bootstrap-servers:}' != ''")
public class EntityEventHub implements SmartLifecycle {

    static final int HISTORY_SIZE = 200;

    private static final Logger LOG = LoggerFactory.getLogger(EntityEventHub.class);

    private final DemoProperties properties;
    private final Deque<Event> history = new ArrayDeque<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private volatile AtlasEntityChangeListener listener;
    private volatile Instant startedAt;

    public EntityEventHub(DemoProperties properties) {
        this.properties = properties;
    }

    /**
     * An entity change as shown in the demo.
     *
     * @param json the whole notification, pretty printed
     */
    public record Event(Instant time, String operation, String typeName, String qualifiedName, String guid,
                        String json) {

        static Event of(EntityChangeEvent event) {
            return new Event(event.time() != null ? event.time() : Instant.now(),
                    String.valueOf(event.getOperationType()), event.typeName(), event.qualifiedName(), event.guid(),
                    AtlasJson.toPrettyJson(event));
        }

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

    @Override
    public void start() {
        // A group of its own: every demo instance sees all events, starting from now.
        listener = AtlasEntityChangeListener.builder()
                .bootstrapServers(properties.kafka().bootstrapServers())
                .groupId("atlas-client-demo-" + UUID.randomUUID())
                .handler(this::publish)
                .build()
                .start();
        startedAt = Instant.now();
        LOG.info("Listening to Atlas entity changes on {}", properties.kafka().bootstrapServers());
    }

    @Override
    public void stop() {
        AtlasEntityChangeListener current = listener;
        if (current != null) {
            current.close();
        }
        emitters.forEach(SseEmitter::complete);
    }

    @Override
    public boolean isRunning() {
        AtlasEntityChangeListener current = listener;
        return current != null && current.isRunning();
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** Why the listener stopped, if it did. */
    public String failure() {
        AtlasEntityChangeListener current = listener;
        return current == null ? null : current.failure().map(Throwable::toString).orElse(null);
    }

    /** Most recent first. */
    public List<Event> recent() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        return emitter;
    }

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
