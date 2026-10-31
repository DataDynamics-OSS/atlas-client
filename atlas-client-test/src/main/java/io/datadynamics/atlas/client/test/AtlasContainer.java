package io.datadynamics.atlas.client.test;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * 통합 테스트용으로 Docker에서 Apache Atlas를 띄우는 Testcontainers 컨테이너입니다.
 * <p>
 * 기본으로 커뮤니티 이미지 {@value #DEFAULT_IMAGE}(HBase와 Solr가 내장된 Atlas)를 씁니다.
 * {@code atlas.image} 시스템 속성이나 {@link #AtlasContainer(String)}로 바꿀 수 있습니다.
 * Atlas는 시작하는 데 몇 분이 걸리므로 테스트마다 새로 띄우지 말고 여러 테스트 클래스가 컨테이너 하나를
 * 공유하십시오({@link AtlasTestServer} 참고).
 * <p>
 * 시작은 {@code /api/atlas/admin/status}가 {@code ACTIVE}를 돌려줄 때까지, 최대
 * {@link #DEFAULT_STARTUP_TIMEOUT} 동안 기다립니다.
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

    /** 기본 Docker 이미지입니다. */
    public static final String DEFAULT_IMAGE = "sburn/apache-atlas:2.3.0";
    /** 컨테이너 안에서 Atlas REST API가 듣는 포트입니다. 호스트 쪽 포트는 무작위로 매핑됩니다. */
    public static final int ATLAS_PORT = 21000;
    /** Atlas가 ACTIVE 상태가 될 때까지 기다리는 최대 시간입니다. */
    public static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(10);

    /** 기본 이미지의 설정 파일 경로와 시작 명령입니다. {@link #withExternalKafka(String)}에서 씁니다. */
    private static final String CONFIG_FILE = "/apache-atlas/conf/atlas-application.properties";
    private static final String START_COMMAND = "/apache-atlas/bin/atlas_start.py; tail -fF /apache-atlas/logs/application.log";

    /** 기본 이미지에 설정된 관리자 계정입니다. */
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";

    /**
     * {@code atlas.image} 시스템 속성에 지정된 이미지, 없으면 {@value #DEFAULT_IMAGE}로 컨테이너를 만듭니다.
     * 아직 시작하지 않은 상태이므로 {@code start()}를 호출해야 합니다.
     */
    public AtlasContainer() {
        this(System.getProperty("atlas.image", DEFAULT_IMAGE));
    }

    /**
     * 주어진 이미지로 컨테이너를 만듭니다. Atlas 포트를 노출하고, Atlas가 ACTIVE가 될 때까지 기다리도록 설정합니다.
     *
     * @param image Docker 이미지 이름(예: {@code sburn/apache-atlas:2.3.0})
     */
    public AtlasContainer(String image) {
        super(DockerImageName.parse(image));
        withExposedPorts(ATLAS_PORT);
        // status 엔드포인트는 Atlas가 모델 로딩을 마치기 전부터 응답합니다. 응답 본문에 ACTIVE가 있어야
        // 서버가 요청을 받을 준비가 된 것이므로, 200 응답만이 아니라 ACTIVE까지 확인합니다.
        waitingFor(Wait.forHttp("/api/atlas/admin/status")
                .forPort(ATLAS_PORT)
                .withBasicCredentials(DEFAULT_USERNAME, DEFAULT_PASSWORD)
                .forStatusCode(200)
                .forResponsePredicate(body -> body.contains("ACTIVE"))
                .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT));
    }

    /**
     * Atlas가 내장 브로커 대신 외부 Kafka를 쓰도록 합니다. 내장 브로커는 컨테이너 안에서만 듣기 때문에
     * 테스트에서 접근할 수 없습니다. 같은 네트워크에 Kafka 컨테이너를 두면 테스트가 {@code ATLAS_HOOK}에
     * 발행하고 {@code ATLAS_ENTITIES}를 읽을 수 있습니다.
     * <pre>{@code
     * Network network = Network.newNetwork();
     * KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1")
     *         .withNetwork(network).withListener("kafka:19092");
     * AtlasContainer atlas = new AtlasContainer().withNetwork(network).withExternalKafka("kafka:19092");
     * }</pre>
     * 시작 명령을 바꾸어, Atlas를 시작하기 전에 {@code sed}로 설정 파일의
     * {@code atlas.notification.embedded}와 {@code atlas.kafka.bootstrap.servers}를 고칩니다.
     * 그래서 기본 이미지({@value #DEFAULT_IMAGE})의 파일 배치에서만 동작합니다.
     *
     * @param bootstrapServers Atlas 컨테이너 안에서 보이는 Kafka 주소
     * @return 이 컨테이너
     */
    public AtlasContainer withExternalKafka(String bootstrapServers) {
        return withCommand("bash", "-c", "sed -i"
                + " -e 's|^atlas.notification.embedded=.*|atlas.notification.embedded=false|'"
                + " -e 's|^atlas.kafka.bootstrap.servers=.*|atlas.kafka.bootstrap.servers=" + bootstrapServers + "|'"
                + " " + CONFIG_FILE + " && " + START_COMMAND);
    }

    /**
     * Atlas REST API의 기본 URL(예: {@code http://localhost:32768})을 돌려줍니다. 컨테이너가 시작된 뒤에만 쓸 수 있습니다.
     *
     * @return 호스트와 매핑된 포트로 만든 URL
     */
    public String getUrl() {
        return "http://" + getHost() + ":" + getMappedPort(ATLAS_PORT);
    }

    /**
     * 기본 이미지의 관리자 사용자 이름을 돌려줍니다.
     *
     * @return 사용자 이름({@code admin})
     */
    public String getUsername() {
        return DEFAULT_USERNAME;
    }

    /**
     * 기본 이미지의 관리자 비밀번호를 돌려줍니다.
     *
     * @return 비밀번호({@code admin})
     */
    public String getPassword() {
        return DEFAULT_PASSWORD;
    }
}
