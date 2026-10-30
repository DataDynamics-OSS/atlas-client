package io.datadynamics.atlas.client.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * The Atlas server integration tests run against, shared by every test in the JVM.
 * <p>
 * If the {@code atlas.url} system property is set, that server is used
 * ({@code atlas.username}/{@code atlas.password}, default {@code admin}/{@code admin}, and
 * {@code atlas.kafka.bootstrap.servers} for the Kafka it uses, if any); this is the quickest way to
 * run integration tests against a development Atlas.
 * Otherwise a Kafka container ({@value #DEFAULT_KAFKA_IMAGE}, override with {@code kafka.image})
 * and an {@link AtlasContainer} using it are started on first use and stopped when the JVM exits.
 * <pre>{@code
 * AtlasTestServer atlas = AtlasTestServer.shared();
 * AtlasClient client = AtlasClient.builder()
 *         .serverUrl(atlas.url())
 *         .basicAuth(atlas.username(), atlas.password())
 *         .build();
 * }</pre>
 */
public final class AtlasTestServer {

    public static final String DEFAULT_KAFKA_IMAGE = "apache/kafka:3.9.1";

    private static final Logger LOG = LoggerFactory.getLogger(AtlasTestServer.class);
    private static final String KAFKA_ALIAS = "kafka:19092";
    private static volatile AtlasTestServer shared;

    private final String url;
    private final String username;
    private final String password;
    private final String kafkaBootstrapServers;

    private AtlasTestServer(String url, String username, String password, String kafkaBootstrapServers) {
        this.url = Objects.requireNonNull(url);
        this.username = username;
        this.password = password;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    public static AtlasTestServer shared() {
        AtlasTestServer server = shared;
        if (server != null) {
            return server;
        }
        synchronized (AtlasTestServer.class) {
            if (shared == null) {
                shared = create();
            }
            return shared;
        }
    }

    private static AtlasTestServer create() {
        String externalUrl = System.getProperty("atlas.url");
        if (externalUrl != null && !externalUrl.isBlank()) {
            LOG.info("Using external Atlas at {}", externalUrl);
            String kafka = System.getProperty("atlas.kafka.bootstrap.servers");
            return new AtlasTestServer(externalUrl, System.getProperty("atlas.username", "admin"),
                    System.getProperty("atlas.password", "admin"), kafka == null || kafka.isBlank() ? null : kafka);
        }
        Network network = Network.newNetwork();
        KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(
                System.getProperty("kafka.image", DEFAULT_KAFKA_IMAGE)).asCompatibleSubstituteFor("apache/kafka"))
                .withNetwork(network)
                .withListener(KAFKA_ALIAS);
        kafka.start();
        AtlasContainer container = new AtlasContainer().withNetwork(network).withExternalKafka(KAFKA_ALIAS);
        long started = System.nanoTime();
        LOG.info("Starting Atlas container {} (this takes a few minutes)", container.getDockerImageName());
        container.start();
        LOG.info("Atlas container ready at {} after {} s, Kafka at {}", container.getUrl(),
                Duration.ofNanos(System.nanoTime() - started).toSeconds(), kafka.getBootstrapServers());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            container.stop();
            kafka.stop();
            network.close();
        }, "atlas-container-stop"));
        return new AtlasTestServer(container.getUrl(), container.getUsername(), container.getPassword(),
                kafka.getBootstrapServers());
    }

    public String url() {
        return url;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    /**
     * Kafka the Atlas server uses for {@code ATLAS_HOOK} and {@code ATLAS_ENTITIES}, reachable from
     * the tests; empty for an external Atlas without {@code atlas.kafka.bootstrap.servers}.
     */
    public Optional<String> kafkaBootstrapServers() {
        return Optional.ofNullable(kafkaBootstrapServers);
    }
}
