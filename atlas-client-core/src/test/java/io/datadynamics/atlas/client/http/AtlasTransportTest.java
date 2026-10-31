package io.datadynamics.atlas.client.http;

import io.datadynamics.atlas.client.AtlasClient;
import io.datadynamics.atlas.client.test.FakeAtlasServer;
import io.datadynamics.atlas.client.exception.AtlasConflictException;
import io.datadynamics.atlas.client.exception.AtlasConnectionException;
import io.datadynamics.atlas.client.exception.AtlasNotFoundException;
import io.datadynamics.atlas.client.exception.AtlasServerException;
import io.datadynamics.atlas.client.exception.AtlasUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.datadynamics.atlas.client.http.AtlasTransport.GET;
import static io.datadynamics.atlas.client.http.AtlasTransport.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@link AtlasTransport}의 동작을 가짜 Atlas 서버({@link FakeAtlasServer})로 검증합니다.
 * <p>
 * 인증·JSON 헤더, 오류 응답을 예외 타입으로 바꾸기, 재시도 조건(멱등성, 503), 최대 시도 횟수, HA 구성의 ACTIVE
 * 서버 선택과 장애 전환, 연결 실패, 쿼리 인코딩, 요청 리스너 보고를 확인합니다.
 */
class AtlasTransportTest {

    // 테스트가 느려지지 않도록 대기 없이 최대 3번 시도하는 재시도 정책입니다.
    private static final RetryPolicy FAST_RETRY = RetryPolicy.builder()
            .maxAttempts(3)
            .initialBackoff(Duration.ZERO)
            .build();

    private final FakeAtlasServer server = new FakeAtlasServer();
    private FakeAtlasServer secondServer;

    // 테스트마다 띄운 가짜 서버(두 번째 서버가 있으면 그것도)를 닫습니다.
    @AfterEach
    void tearDown() {
        server.close();
        if (secondServer != null) {
            secondServer.close();
        }
    }

    // 주어진 서버 URL들, Basic 인증(admin/secret), 빠른 재시도 정책으로 전송 계층을 만듭니다.
    private AtlasTransport transport(String... urls) {
        return AtlasClient.builder()
                .serverUrls(urls)
                .basicAuth("admin", "secret")
                .retryPolicy(FAST_RETRY)
                .build()
                .transport();
    }

    /**
     * 요청에 Basic 인증 헤더와 JSON Accept 헤더가 붙는지 확인합니다.
     * <p>
     * 준비: 버전 응답 등록 → 실행: GET 요청 → 기대: 본문을 그대로 돌려주고, {@code Authorization}은
     * {@code admin:secret}의 Base64, {@code Accept}는 {@code application/json}입니다.
     */
    @Test
    void sendsBasicAuthAndJsonHeaders() {
        server.on(GET, "/api/atlas/admin/version", 200, "{\"Version\":\"2.3.0\"}");

        String body = transport(server.url()).execute(GET, "/api/atlas/admin/version", null, null);

        assertThat(body).contains("2.3.0");
        FakeAtlasServer.Request request = server.lastRequest();
        assertThat(request.header("Authorization")).isEqualTo("Basic YWRtaW46c2VjcmV0");
        assertThat(request.header("Accept")).isEqualTo("application/json");
    }

    /**
     * Atlas 오류 본문이 구체적인 예외 타입과 필드로 바뀌는지 확인합니다.
     * <p>
     * 준비: Atlas 형식의 404 오류 본문 등록 → 실행: GET 요청 → 기대: {@link AtlasNotFoundException}이 발생하고
     * 상태 코드, 오류 코드, 오류 메시지가 채워지며, 예외 메시지에 메서드와 오류 코드가 들어 있습니다.
     */
    @Test
    void mapsAtlasErrorBodyToTypedException() {
        server.on(GET, "/api/atlas/v2/entity/guid/nope", 404,
                "{\"errorCode\":\"ATLAS-404-00-005\",\"errorMessage\":\"Given instance guid nope is invalid/not found\"}");

        assertThatThrownBy(() -> transport(server.url()).execute(GET, "/api/atlas/v2/entity/guid/nope", null, null))
                .isInstanceOfSatisfying(AtlasNotFoundException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(404);
                    assertThat(e.getErrorCode()).isEqualTo("ATLAS-404-00-005");
                    assertThat(e.getErrorMessage()).contains("invalid/not found");
                    assertThat(e.getMessage()).contains("GET").contains("ATLAS-404-00-005");
                });
    }

    /**
     * HTTP 409가 {@link AtlasConflictException}으로 바뀌는지 확인합니다.
     * <p>
     * 준비: 409 오류 등록 → 실행: POST 요청 → 기대: {@link AtlasConflictException}이 발생합니다.
     */
    @Test
    void mapsConflict() {
        server.on(POST, "/api/atlas/v2/types/typedefs", 409,
                "{\"errorCode\":\"ATLAS-409-00-001\",\"errorMessage\":\"Given type my_table already exists\"}");

        assertThatThrownBy(() -> transport(server.url()).execute(POST, "/api/atlas/v2/types/typedefs", null, "{}"))
                .isInstanceOf(AtlasConflictException.class);
    }

    /**
     * 오류 본문이 JSON이 아닐 때 원문 본문을 오류 메시지로 쓰는지 확인합니다.
     * <p>
     * 준비: HTML 본문의 500 응답 등록 → 실행: GET 요청 → 기대: 오류 메시지가 HTML 본문 그대로입니다.
     */
    @Test
    void nonJsonErrorBodyIsKeptAsMessage() {
        server.on(GET, "/api/atlas/x", 500, "<html>boom</html>");

        assertThatThrownBy(() -> transport(server.url()).execute(GET, "/api/atlas/x", null, null))
                .isInstanceOfSatisfying(AtlasServerException.class,
                        e -> assertThat(e.getErrorMessage()).isEqualTo("<html>boom</html>"));
    }

    /**
     * 멱등 요청이 503 뒤에 재시도되어 성공하는지 확인합니다.
     * <p>
     * 준비: 첫 응답 503, 두 번째 응답 200 → 실행: GET 요청 → 기대: 성공 본문을 받고 요청이 두 번 나갑니다.
     */
    @Test
    void retriesIdempotentRequestOn503() {
        server.on(GET, "/api/atlas/x", 503, "")
                .on(GET, "/api/atlas/x", 200, "{\"ok\":true}");

        String body = transport(server.url()).execute(GET, "/api/atlas/x", null, null);

        assertThat(body).contains("ok");
        assertThat(server.requests()).hasSize(2);
    }

    /**
     * POST는 502에서는 재시도하지 않고 503에서는 재시도하는지 확인합니다.
     * <p>
     * 502는 서버가 이미 요청을 처리했을 수 있으므로 멱등이 아닌 POST를 다시 보내면 안 되고, 503은 요청이 적용되지
     * 않았음이 확실하므로 재시도해도 됩니다.
     * <p>
     * 준비·실행: 502를 돌려주는 경로에 POST → 기대: 예외와 함께 요청 1번. 이어서 503 뒤 200을 돌려주는 경로에
     * POST → 기대: 성공하고 누적 요청이 3번이 됩니다.
     */
    @Test
    void doesNotRetryPostOn502ButDoesOn503() {
        server.on(POST, "/api/atlas/bad-gateway", 502, "");
        assertThatThrownBy(() -> transport(server.url()).execute(POST, "/api/atlas/bad-gateway", null, "{}"))
                .isInstanceOf(AtlasUnavailableException.class);
        assertThat(server.requests()).hasSize(1);

        server.on(POST, "/api/atlas/passive", 503, "")
                .on(POST, "/api/atlas/passive", 200, "{}");
        transport(server.url()).execute(POST, "/api/atlas/passive", null, "{}");
        assertThat(server.requests()).hasSize(3);
    }

    /**
     * 최대 시도 횟수를 다 쓰면 마지막 오류를 예외로 던지는지 확인합니다.
     * <p>
     * 준비: 항상 503 → 실행: GET 요청 → 기대: {@link AtlasUnavailableException}이 발생하고 요청이 정확히 3번 나갑니다.
     */
    @Test
    void givesUpAfterMaxAttempts() {
        server.on(GET, "/api/atlas/x", 503, "");

        assertThatThrownBy(() -> transport(server.url()).execute(GET, "/api/atlas/x", null, null))
                .isInstanceOf(AtlasUnavailableException.class);
        assertThat(server.requests()).hasSize(3);
    }

    /**
     * HA 구성에서 ACTIVE 서버를 찾아 쓰고, 찾은 서버를 기억하는지 확인합니다.
     * <p>
     * 준비: 첫 서버는 PASSIVE, 두 번째 서버는 ACTIVE → 실행: 요청 두 번 → 기대: 두 요청 모두 두 번째 서버로 가고,
     * 상태 확인은 처음 한 번만 합니다.
     */
    @Test
    void highAvailabilityUsesActiveServer() {
        secondServer = new FakeAtlasServer();
        server.on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"PASSIVE\"}");
        secondServer.on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"ACTIVE\"}")
                .on(GET, "/api/atlas/x", 200, "{\"from\":\"second\"}");

        AtlasTransport transport = transport(server.url(), secondServer.url());

        assertThat(transport.execute(GET, "/api/atlas/x", null, null)).contains("second");
        assertThat(transport.execute(GET, "/api/atlas/x", null, null)).contains("second");
        // 상태는 한 번만 확인하고, 그 뒤에는 기억해 둔 ACTIVE 서버를 씁니다.
        assertThat(secondServer.requests()).extracting(r -> r.path)
                .containsExactly("/api/atlas/admin/status", "/api/atlas/x", "/api/atlas/x");
    }

    /**
     * ACTIVE였던 서버가 PASSIVE로 바뀌면 다른 서버로 장애 전환하는지 확인합니다.
     * <p>
     * 준비: 첫 서버는 처음에 ACTIVE였다가 이후 PASSIVE로 답하고 실제 요청에는 503, 두 번째 서버는 ACTIVE →
     * 실행: GET 요청 → 기대: 503 뒤 ACTIVE 서버를 다시 찾아 두 번째 서버의 응답을 받습니다.
     */
    @Test
    void highAvailabilityFailsOverWhenActiveServerBecomesPassive() {
        secondServer = new FakeAtlasServer();
        server.on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"ACTIVE\"}")
                .on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"PASSIVE\"}");
        server.on(GET, "/api/atlas/x", 503, "");
        secondServer.on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"ACTIVE\"}")
                .on(GET, "/api/atlas/x", 200, "{\"from\":\"second\"}");

        AtlasTransport transport = transport(server.url(), secondServer.url());

        assertThat(transport.execute(GET, "/api/atlas/x", null, null)).contains("second");
    }

    /**
     * ACTIVE 서버가 하나도 없을 때 각 서버 상태를 담은 연결 예외가 나는지 확인합니다.
     * <p>
     * 준비: 두 서버가 각각 PASSIVE, BECOMING_ACTIVE → 실행: GET 요청 → 기대: {@link AtlasConnectionException}의
     * 메시지에 "No active Atlas server"와 서버 상태가 들어 있습니다.
     */
    @Test
    void noActiveServer() {
        secondServer = new FakeAtlasServer();
        server.on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"PASSIVE\"}");
        secondServer.on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"BECOMING_ACTIVE\"}");

        assertThatThrownBy(() -> transport(server.url(), secondServer.url()).execute(GET, "/api/atlas/x", null, null))
                .isInstanceOf(AtlasConnectionException.class)
                .hasMessageContaining("No active Atlas server")
                .hasMessageContaining("BECOMING_ACTIVE");
    }

    /**
     * 연결이 거부되면 재시도한 뒤 연결 예외로 보고하는지 확인합니다.
     * <p>
     * 준비: 비어 있는 포트를 잡았다가 바로 놓아 아무도 듣지 않는 주소를 만듦 → 실행: POST 요청 →
     * 기대: {@link AtlasConnectionException}이 발생합니다.
     */
    @Test
    void connectionRefusedIsRetriedThenReported() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> transport("http://127.0.0.1:" + freePort).execute(POST, "/api/atlas/x", null, "{}"))
                .isInstanceOf(AtlasConnectionException.class);
    }

    /**
     * 쿼리 파라미터가 올바르게 인코딩되고 {@code null} 값은 빠지는지 확인합니다.
     * <p>
     * 준비: 공백과 {@code @}가 든 qualifiedName, {@code null} 값 파라미터 → 실행: GET 요청 → 기대: 쿼리는
     * {@code attr%3AqualifiedName=db.t%20one%40c}뿐입니다(공백은 {@code %20}).
     */
    @Test
    void encodesQueryParameters() {
        server.on(GET, "/api/atlas/x", 200, "{}");

        transport(server.url()).execute(GET, "/api/atlas/x",
                QueryParams.create().uniqueAttributes(Map.of("qualifiedName", "db.t one@c")).add("skip", null),
                null);

        assertThat(server.lastRequest().rawQuery).isEqualTo("attr%3AqualifiedName=db.t%20one%40c");
    }

    /**
     * 요청 리스너가 재시도를 포함한 모든 시도를 보고받는지 확인합니다.
     * <p>
     * 준비: 503 뒤 200, 시도를 모으는 리스너 → 실행: 쿼리를 붙인 GET 요청 → 기대: 시도 1(503), 시도 2(200)가
     * 차례로 보고되고, 마지막 시도에는 메서드, 전체 URI, 응답 본문, 성공 여부가 담기며 실패 정보는 없습니다.
     */
    @Test
    void requestListenerSeesEveryAttempt() {
        server.on(GET, "/api/atlas/x", 503, "")
                .on(GET, "/api/atlas/x", 200, "{\"ok\":true}");
        List<RequestListener.Exchange> exchanges = new CopyOnWriteArrayList<>();
        AtlasTransport transport = AtlasClient.builder()
                .serverUrl(server.url())
                .retryPolicy(FAST_RETRY)
                .requestListener(exchanges::add)
                .build()
                .transport();

        transport.execute(GET, "/api/atlas/x", QueryParams.create().add("q", "1"), null);

        assertThat(exchanges).extracting(RequestListener.Exchange::attempt, RequestListener.Exchange::statusCode)
                .containsExactly(tuple(1, 503), tuple(2, 200));
        RequestListener.Exchange last = exchanges.get(1);
        assertThat(last.method()).isEqualTo(GET);
        assertThat(last.uri().toString()).isEqualTo(server.url() + "/api/atlas/x?q=1");
        assertThat(last.responseBody()).contains("ok");
        assertThat(last.isSuccess()).isTrue();
        assertThat(last.failure()).isNull();
    }

    /**
     * 연결 실패도 리스너에 보고되는지 확인합니다.
     * <p>
     * 준비: 아무도 듣지 않는 주소, 시도를 모으는 리스너 → 실행: POST 요청 → 기대: 연결 예외가 발생하고, 세 번의
     * 시도가 모두 상태 코드 -1, 실패 정보 있음, 요청 본문 포함으로 보고됩니다.
     */
    @Test
    void requestListenerSeesConnectionFailures() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        List<RequestListener.Exchange> exchanges = new CopyOnWriteArrayList<>();
        AtlasTransport transport = AtlasClient.builder()
                .serverUrl("http://127.0.0.1:" + freePort)
                .retryPolicy(FAST_RETRY)
                .requestListener(exchanges::add)
                .build()
                .transport();

        assertThatThrownBy(() -> transport.execute(POST, "/api/atlas/x", null, "{}"))
                .isInstanceOf(AtlasConnectionException.class);

        assertThat(exchanges).hasSize(3).allSatisfy(e -> {
            assertThat(e.statusCode()).isEqualTo(-1);
            assertThat(e.failure()).isNotNull();
            assertThat(e.requestBody()).isEqualTo("{}");
        });
    }

    /**
     * 리스너가 예외를 던져도 요청 자체는 성공하는지 확인합니다.
     * <p>
     * 준비: 항상 예외를 던지는 리스너 → 실행: GET 요청 → 기대: 정상 응답 본문을 돌려받습니다.
     */
    @Test
    void failingRequestListenerDoesNotBreakRequests() {
        server.on(GET, "/api/atlas/x", 200, "{}");
        AtlasTransport transport = AtlasClient.builder()
                .serverUrl(server.url())
                .requestListener(e -> {
                    throw new IllegalStateException("boom");
                })
                .build()
                .transport();

        assertThat(transport.execute(GET, "/api/atlas/x", null, null)).isEqualTo("{}");
    }
}
