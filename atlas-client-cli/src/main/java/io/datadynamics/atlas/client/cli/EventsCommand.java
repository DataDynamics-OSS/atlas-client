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

@Command(name = "events", description = "Reads the entity change notifications Atlas publishes to Kafka.",
        subcommands = EventsCommand.Tail.class)
final class EventsCommand {

    @Command(name = "tail", description = "Prints entity change events from the ATLAS_ENTITIES topic as they arrive.")
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

        @Override
        public Integer call() {
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
            if (qualifiedName != null) {
                Pattern pattern = glob(qualifiedName);
                builder.filter(event -> event.qualifiedName() != null && pattern.matcher(event.qualifiedName()).matches());
            }

            Instant deadline = durationSeconds == null ? null : Instant.now().plusSeconds(durationSeconds);
            int seen = 0;
            try (AtlasEntityChangeListener listener = builder.build()) {
                while ((maxEvents == null || seen < maxEvents) && (deadline == null || Instant.now().isBefore(deadline))) {
                    for (EntityChangeEvent event : listener.poll(Duration.ofMillis(500))) {
                        print(event);
                        if (maxEvents != null && ++seen >= maxEvents) {
                            break;
                        }
                    }
                    if (group != null) {
                        listener.commit();
                    }
                }
            }
            return 0;
        }

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
