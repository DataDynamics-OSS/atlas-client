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

class AtlasTransportTest {

    private static final RetryPolicy FAST_RETRY = RetryPolicy.builder()
            .maxAttempts(3)
            .initialBackoff(Duration.ZERO)
            .build();

    private final FakeAtlasServer server = new FakeAtlasServer();
    private FakeAtlasServer secondServer;

    @AfterEach
    void tearDown() {
        server.close();
        if (secondServer != null) {
            secondServer.close();
        }
    }

    private AtlasTransport transport(String... urls) {
        return AtlasClient.builder()
                .serverUrls(urls)
                .basicAuth("admin", "secret")
                .retryPolicy(FAST_RETRY)
                .build()
                .transport();
    }

    @Test
    void sendsBasicAuthAndJsonHeaders() {
        server.on(GET, "/api/atlas/admin/version", 200, "{\"Version\":\"2.3.0\"}");

        String body = transport(server.url()).execute(GET, "/api/atlas/admin/version", null, null);

        assertThat(body).contains("2.3.0");
        FakeAtlasServer.Request request = server.lastRequest();
        assertThat(request.header("Authorization")).isEqualTo("Basic YWRtaW46c2VjcmV0");
        assertThat(request.header("Accept")).isEqualTo("application/json");
    }

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

    @Test
    void mapsConflict() {
        server.on(POST, "/api/atlas/v2/types/typedefs", 409,
                "{\"errorCode\":\"ATLAS-409-00-001\",\"errorMessage\":\"Given type my_table already exists\"}");

        assertThatThrownBy(() -> transport(server.url()).execute(POST, "/api/atlas/v2/types/typedefs", null, "{}"))
                .isInstanceOf(AtlasConflictException.class);
    }

    @Test
    void nonJsonErrorBodyIsKeptAsMessage() {
        server.on(GET, "/api/atlas/x", 500, "<html>boom</html>");

        assertThatThrownBy(() -> transport(server.url()).execute(GET, "/api/atlas/x", null, null))
                .isInstanceOfSatisfying(AtlasServerException.class,
                        e -> assertThat(e.getErrorMessage()).isEqualTo("<html>boom</html>"));
    }

    @Test
    void retriesIdempotentRequestOn503() {
        server.on(GET, "/api/atlas/x", 503, "")
                .on(GET, "/api/atlas/x", 200, "{\"ok\":true}");

        String body = transport(server.url()).execute(GET, "/api/atlas/x", null, null);

        assertThat(body).contains("ok");
        assertThat(server.requests()).hasSize(2);
    }

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

    @Test
    void givesUpAfterMaxAttempts() {
        server.on(GET, "/api/atlas/x", 503, "");

        assertThatThrownBy(() -> transport(server.url()).execute(GET, "/api/atlas/x", null, null))
                .isInstanceOf(AtlasUnavailableException.class);
        assertThat(server.requests()).hasSize(3);
    }

    @Test
    void highAvailabilityUsesActiveServer() {
        secondServer = new FakeAtlasServer();
        server.on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"PASSIVE\"}");
        secondServer.on(GET, "/api/atlas/admin/status", 200, "{\"Status\":\"ACTIVE\"}")
                .on(GET, "/api/atlas/x", 200, "{\"from\":\"second\"}");

        AtlasTransport transport = transport(server.url(), secondServer.url());

        assertThat(transport.execute(GET, "/api/atlas/x", null, null)).contains("second");
        assertThat(transport.execute(GET, "/api/atlas/x", null, null)).contains("second");
        // Status is probed once, then the active server is cached.
        assertThat(secondServer.requests()).extracting(r -> r.path)
                .containsExactly("/api/atlas/admin/status", "/api/atlas/x", "/api/atlas/x");
    }

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

    @Test
    void connectionRefusedIsRetriedThenReported() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> transport("http://127.0.0.1:" + freePort).execute(POST, "/api/atlas/x", null, "{}"))
                .isInstanceOf(AtlasConnectionException.class);
    }

    @Test
    void encodesQueryParameters() {
        server.on(GET, "/api/atlas/x", 200, "{}");

        transport(server.url()).execute(GET, "/api/atlas/x",
                QueryParams.create().uniqueAttributes(Map.of("qualifiedName", "db.t one@c")).add("skip", null),
                null);

        assertThat(server.lastRequest().rawQuery).isEqualTo("attr%3AqualifiedName=db.t%20one%40c");
    }

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
