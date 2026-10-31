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
 * 통합 테스트가 접속할 Atlas 서버입니다. JVM 안의 모든 테스트가 하나를 공유합니다.
 * <p>
 * {@code atlas.url} 시스템 속성이 있으면 그 서버를 씁니다. 계정은 {@code atlas.username}/{@code atlas.password}
 * (기본값 {@code admin}/{@code admin})이고, 그 서버가 쓰는 Kafka가 있으면 {@code atlas.kafka.bootstrap.servers}로
 * 지정합니다. 개발용 Atlas에 통합 테스트를 돌리는 가장 빠른 방법입니다.
 * <p>
 * 속성이 없으면 처음 사용할 때 Kafka 컨테이너({@value #DEFAULT_KAFKA_IMAGE}, {@code kafka.image}로 변경 가능)와
 * 그 Kafka를 쓰는 {@link AtlasContainer}를 같은 Docker 네트워크에 띄우고, JVM이 끝날 때 shutdown hook으로 멈춥니다.
 * <pre>{@code
 * AtlasTestServer atlas = AtlasTestServer.shared();
 * AtlasClient client = AtlasClient.builder()
 *         .serverUrl(atlas.url())
 *         .basicAuth(atlas.username(), atlas.password())
 *         .build();
 * }</pre>
 */
public final class AtlasTestServer {

    /** 기본 Kafka Docker 이미지입니다. */
    public static final String DEFAULT_KAFKA_IMAGE = "apache/kafka:3.9.1";

    private static final Logger LOG = LoggerFactory.getLogger(AtlasTestServer.class);
    /** Docker 네트워크 안에서 Atlas 컨테이너가 Kafka에 접속할 때 쓰는 주소입니다. */
    private static final String KAFKA_ALIAS = "kafka:19092";
    /** 지연 생성되는 공유 인스턴스입니다. double-checked locking을 위해 {@code volatile}입니다. */
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

    /**
     * 공유 서버를 돌려줍니다. 처음 호출할 때만 서버를 준비하며(컨테이너를 쓰면 몇 분이 걸립니다), 이후 호출은
     * 같은 인스턴스를 바로 돌려줍니다. 여러 스레드에서 동시에 호출해도 서버는 한 번만 만들어집니다.
     *
     * @return 공유 {@link AtlasTestServer}
     */
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

    /**
     * 외부 서버 설정이 있으면 그것을 쓰고, 없으면 Kafka와 Atlas 컨테이너를 차례로 띄웁니다.
     * Atlas가 시작할 때 Kafka에 접속해야 하므로 Kafka를 먼저 시작합니다.
     */
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

    /**
     * Atlas REST API의 기본 URL을 돌려줍니다.
     *
     * @return 서버 URL
     */
    public String url() {
        return url;
    }

    /**
     * Basic 인증에 쓸 사용자 이름을 돌려줍니다.
     *
     * @return 사용자 이름
     */
    public String username() {
        return username;
    }

    /**
     * Basic 인증에 쓸 비밀번호를 돌려줍니다.
     *
     * @return 비밀번호
     */
    public String password() {
        return password;
    }

    /**
     * Atlas 서버가 {@code ATLAS_HOOK}과 {@code ATLAS_ENTITIES}에 쓰는 Kafka의, 테스트에서 접속할 수 있는 주소를 돌려줍니다.
     *
     * @return Kafka bootstrap 주소. {@code atlas.kafka.bootstrap.servers} 없이 외부 Atlas를 쓰면 비어 있습니다
     */
    public Optional<String> kafkaBootstrapServers() {
        return Optional.ofNullable(kafkaBootstrapServers);
    }
}
