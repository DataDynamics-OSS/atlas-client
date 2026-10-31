package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.json.AtlasJson;
import io.datadynamics.atlas.client.kafka.AtlasEntityChangeListener;
import io.datadynamics.atlas.client.kafka.message.EntityChangeEvent;
import io.datadynamics.atlas.client.kafka.message.OperationType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@code atlas events} 명령 그룹입니다. Atlas가 Kafka에 발행하는 엔티티 변경 알림을 읽습니다.
 * <p>
 * REST가 아니라 Kafka로 접속하므로 {@link ClientCommand}가 아니라 {@link CliCommand}를 상속합니다.
 */
@Command(name = "events", description = "Reads the entity change notifications Atlas publishes to Kafka.",
        subcommands = EventsCommand.Tail.class)
final class EventsCommand {

    @Command(name = "tail", description = "Prints entity change events from the ATLAS_ENTITIES topic as they arrive.")
    /**
     * {@code atlas events tail}: {@code ATLAS_ENTITIES} 토픽의 엔티티 변경 이벤트를 도착하는 대로 출력합니다.
     * <p>
     * {@code --group}을 주지 않으면 실행할 때마다 {@code atlas-cli-<UUID>} 형태의 새 consumer group을 만들고
     * offset을 커밋하지 않으므로, 다른 consumer의 진행 위치에 영향을 주지 않습니다. {@code --group}을 주면
     * poll할 때마다 offset을 커밋하므로 다음 실행은 이어서 읽습니다.
     * <p>
     * {@code --max-events}나 {@code --duration}이 없으면 프로세스를 끝낼 때(Ctrl+C 등)까지 계속 읽습니다.
     */
    static final class Tail extends CliCommand {

        @Option(names = "--bootstrap-servers", paramLabel = "SERVERS",
                description = "Kafka bootstrap servers. Env: ATLAS_KAFKA_BOOTSTRAP_SERVERS")
        String bootstrapServers;

        @Option(names = "--topic", defaultValue = AtlasEntityChangeListener.DEFAULT_TOPIC,
                description = "Topic (default: ${DEFAULT-VALUE})")
        String topic;

        @Option(names = "--group", description = "Consumer group (default: a new group that commits nothing)")
        String group;

        @Option(names = "--from-beginning", description = "Start at the oldest retained event")
        boolean fromBeginning;

        @Option(names = {"-t", "--type"}, paramLabel = "TYPE", description = "Only events about this type (repeatable)")
        List<String> typeNames = new ArrayList<>();

        @Option(names = "--operation", paramLabel = "OP", description = "Only these operations (repeatable): ${COMPLETION-CANDIDATES}")
        List<OperationType> operations = new ArrayList<>();

        @Option(names = "--qualified-name", paramLabel = "GLOB",
                description = "Only entities whose qualifiedName matches, e.g. 'sales.*@prod'")
        String qualifiedName;

        @Option(names = "--json", description = "Print each event as JSON")
        boolean json;

        @Option(names = "--max-events", paramLabel = "N", description = "Stop after N events")
        Integer maxEvents;

        @Option(names = "--duration", paramLabel = "SECONDS", description = "Stop after this many seconds")
        Long durationSeconds;

        @Option(names = "-X", paramLabel = "KEY=VALUE", description = "Kafka consumer property, e.g. -X security.protocol=SASL_SSL")
        Map<String, String> properties = new LinkedHashMap<>();

        /**
         * listener를 만들고, 종료 조건을 만족할 때까지 이벤트를 받아 출력합니다.
         * <p>
         * {@code --from-beginning}은 새 consumer group에만 효과가 있습니다. 이미 커밋된 offset이 있는
         * {@code --group}을 주면 그 위치부터 읽습니다.
         *
         * @return 항상 0
         * @throws IllegalArgumentException Kafka 서버 주소가 옵션과 환경 변수 어디에도 없는 경우
         */
        @Override
        public Integer call() {
            // 옵션이 없으면 환경 변수에서 Kafka 서버 주소를 찾습니다.
            String servers = bootstrapServers != null ? bootstrapServers
                    : root().env().get("ATLAS_KAFKA_BOOTSTRAP_SERVERS");
            if (servers == null) {
                throw new IllegalArgumentException("No Kafka servers: use --bootstrap-servers or ATLAS_KAFKA_BOOTSTRAP_SERVERS");
            }
            AtlasEntityChangeListener.Builder builder = AtlasEntityChangeListener.builder()
                    .bootstrapServers(servers)
                    .topic(topic)
                    .groupId(group != null ? group : "atlas-cli-" + UUID.randomUUID())
                    .properties(properties);
            if (fromBeginning) {
                builder.startFromEarliest();
            }
            if (!typeNames.isEmpty()) {
                builder.typeNames(typeNames.toArray(String[]::new));
            }
            if (!operations.isEmpty()) {
                builder.operations(operations.toArray(OperationType[]::new));
            }
            // qualifiedName 필터는 Kafka에서 받은 뒤 클라이언트 쪽에서 적용합니다. qualifiedName이 없는 이벤트는 걸러 냅니다.
            if (qualifiedName != null) {
                Pattern pattern = glob(qualifiedName);
                builder.filter(event -> event.qualifiedName() != null && pattern.matcher(event.qualifiedName()).matches());
            }

            Instant deadline = durationSeconds == null ? null : Instant.now().plusSeconds(durationSeconds);
            int seen = 0;
            // 이벤트가 없어도 종료 시간을 확인할 수 있도록 poll은 500ms씩 기다립니다.
            try (AtlasEntityChangeListener listener = builder.build()) {
                while ((maxEvents == null || seen < maxEvents) && (deadline == null || Instant.now().isBefore(deadline))) {
                    List<EntityChangeEvent> events = listener.poll(Duration.ofMillis(500));
                    EntityChangeEvent stoppedAt = null;
                    for (EntityChangeEvent event : events) {
                        print(event);
                        if (maxEvents != null && ++seen >= maxEvents) {
                            stoppedAt = event;
                            break;
                        }
                    }
                    // 직접 지정한 group일 때만 커밋합니다. 임시 group은 다시 쓰이지 않으므로 커밋할 필요가 없습니다.
                    // --max-events로 배치 중간에 멈췄다면 출력한 이벤트까지만 커밋해서, 출력하지 않은 이벤트는
                    // 같은 group으로 다시 실행할 때 받게 합니다.
                    if (group != null) {
                        if (stoppedAt != null && stoppedAt != events.get(events.size() - 1)) {
                            listener.commitThrough(stoppedAt);
                        } else {
                            listener.commit();
                        }
                    }
                }
            }
            return 0;
        }

        /**
         * 셸 스타일 glob 패턴을 정규식으로 바꿉니다. {@code *}는 임의 길이의 문자열, {@code ?}는 문자 하나에
         * 대응하며, 그 밖의 문자(정규식 특수 문자 포함)는 글자 그대로 비교합니다. 전체 일치로 검사합니다.
         * <p>
         * {@code *}와 {@code ?}의 앞뒤를 경계로 나눠(lookahead/lookbehind로 구분자 자체도 남김) 와일드카드는
         * 정규식으로, 나머지 조각은 {@link Pattern#quote(String)}로 바꿉니다.
         *
         * @param glob 예: {@code sales.*@prod}
         * @return 컴파일된 정규식
         */
        static Pattern glob(String glob) {
            StringBuilder regex = new StringBuilder();
            for (String part : glob.split("(?=[*?])|(?<=[*?])")) {
                regex.append(switch (part) {
                    case "*" -> ".*";
                    case "?" -> ".";
                    default -> Pattern.quote(part);
                });
            }
            return Pattern.compile(regex.toString());
        }

        /**
         * 이벤트 하나를 출력합니다. {@code --json}이면 JSON 한 줄로, 아니면 시간, 연산, {@code 타입:qualifiedName},
         * guid를 한 줄로 출력합니다. 실시간으로 보이도록 매번 flush합니다.
         */
        private void print(EntityChangeEvent event) {
            if (json) {
                out().println(AtlasJson.toJson(event));
            } else {
                out().printf("%s  %-20s %s:%s (%s)%n", event.time(), event.getOperationType(), event.typeName(),
                        event.qualifiedName() != null ? event.qualifiedName() : "-", event.guid());
            }
            out().flush();
        }
    }
}
