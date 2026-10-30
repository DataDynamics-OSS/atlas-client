package io.datadynamics.atlas.client.test;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Apache Atlas in Docker for integration tests.
 * <p>
 * Uses the community image {@value #DEFAULT_IMAGE} (Atlas with embedded HBase and Solr) by default;
 * override with the {@code atlas.image} system property or {@link #AtlasContainer(String)}.
 * Atlas needs several minutes to start: share one container across test classes
 * (see {@link AtlasTestServer}) rather than starting one per test.
 * <pre>{@code
 * try (AtlasContainer atlas = new AtlasContainer()) {
 *     atlas.start();
 *     AtlasClient client = AtlasClient.builder()
 *             .serverUrl(atlas.getUrl())
 *             .basicAuth(atlas.getUsername(), atlas.getPassword())
 *             .build();
 * }
 * }</pre>
 */
public class AtlasContainer extends GenericContainer<AtlasContainer> {

    public static final String DEFAULT_IMAGE = "sburn/apache-atlas:2.3.0";
    public static final int ATLAS_PORT = 21000;
    public static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(10);

    /** Configuration file and start command of the default image. */
    private static final String CONFIG_FILE = "/apache-atlas/conf/atlas-application.properties";
    private static final String START_COMMAND = "/apache-atlas/bin/atlas_start.py; tail -fF /apache-atlas/logs/application.log";

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";

    public AtlasContainer() {
        this(System.getProperty("atlas.image", DEFAULT_IMAGE));
    }

    public AtlasContainer(String image) {
        super(DockerImageName.parse(image));
        withExposedPorts(ATLAS_PORT);
        // The status endpoint answers before Atlas has finished loading its models; ACTIVE
        // means the server accepts requests.
        waitingFor(Wait.forHttp("/api/atlas/admin/status")
                .forPort(ATLAS_PORT)
                .withBasicCredentials(DEFAULT_USERNAME, DEFAULT_PASSWORD)
                .forStatusCode(200)
                .forResponsePredicate(body -> body.contains("ACTIVE"))
                .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));
    }

    /**
     * Makes Atlas use an external Kafka instead of its embedded broker, which only listens inside
     * the container. With a Kafka container on the same network, tests can publish to
     * {@code ATLAS_HOOK} and read {@code ATLAS_ENTITIES}:
     * <pre>{@code
     * Network network = Network.newNetwork();
     * KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1")
     *         .withNetwork(network).withListener("kafka:19092");
     * AtlasContainer atlas = new AtlasContainer().withNetwork(network).withExternalKafka("kafka:19092");
     * }</pre>
     * Works with the layout of the default image ({@value #DEFAULT_IMAGE}).
     *
     * @param bootstrapServers Kafka address as seen from inside the Atlas container
     */
    public AtlasContainer withExternalKafka(String bootstrapServers) {
        return withCommand("bash", "-c", "sed -i"
                + " -e 's|^atlas.notification.embedded=.*|atlas.notification.embedded=false|'"
                + " -e 's|^atlas.kafka.bootstrap.servers=.*|atlas.kafka.bootstrap.servers=" + bootstrapServers + "|'"
                + " " + CONFIG_FILE + " && " + START_COMMAND);
    }

    /** Base URL of the Atlas REST API, e.g. {@code http://localhost:32768}. */
    public String getUrl() {
        return "http://" + getHost() + ":" + getMappedPort(ATLAS_PORT);
    }

    public String getUsername() {
        return DEFAULT_USERNAME;
    }

    public String getPassword() {
        return DEFAULT_PASSWORD;
    }
}
