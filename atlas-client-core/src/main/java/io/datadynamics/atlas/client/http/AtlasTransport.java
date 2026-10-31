package io.datadynamics.atlas.client.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.datadynamics.atlas.client.AtlasClientConfig;
import io.datadynamics.atlas.client.exception.AtlasClientException;
import io.datadynamics.atlas.client.exception.AtlasConnectionException;
import io.datadynamics.atlas.client.exception.AtlasServerException;
import io.datadynamics.atlas.client.json.AtlasJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

/**
 * Atlas로 요청을 보냅니다. 서버 선택(HA), 인증, 재시도, JSON 직렬화·역직렬화, 오류 응답을
 * {@link AtlasServerException} 하위 클래스로 바꾸는 일을 맡습니다.
 * <p>
 * API 클래스들이 쓰는 저수준 진입점입니다. 대부분의 애플리케이션은 이 클래스 대신
 * {@link io.datadynamics.atlas.client.AtlasClient}를 써야 합니다.
 * <p>
 * 내부의 {@link HttpClient}와 서버 선택기를 공유하며 여러 스레드에서 동시에 호출해도 됩니다.
 */
public final class AtlasTransport {

    /** HTTP {@code GET} 메서드 이름입니다. */
    public static final String GET = "GET";
    /** HTTP {@code POST} 메서드 이름입니다. */
    public static final String POST = "POST";
    /** HTTP {@code PUT} 메서드 이름입니다. */
    public static final String PUT = "PUT";
    /** HTTP {@code DELETE} 메서드 이름입니다. */
    public static final String DELETE = "DELETE";

    // HA 구성에서 각 서버가 ACTIVE인지 PASSIVE인지 확인할 때 부르는 경로입니다.
    static final String STATUS_PATH = "/api/atlas/admin/status";

    private static final Logger LOG = LoggerFactory.getLogger(AtlasTransport.class);
    // 여러 번 보내도 결과가 같은 메서드들입니다. 서버에 도달했을 수도 있는 실패도 이 메서드들은 재시도해도 안전합니다.
    private static final Set<String> IDEMPOTENT_METHODS = Set.of(GET, PUT, DELETE, "HEAD", "OPTIONS");
    // 예외 메시지에 넣을 응답 본문의 최대 길이입니다. 프록시의 큰 HTML 오류 페이지 등으로 메시지가 너무 길어지지 않게 합니다.
    private static final int MAX_ERROR_BODY_LENGTH = 2000;

    private final AtlasClientConfig config;
    private final HttpClient httpClient;
    private final ServerSelector serverSelector;
    private final ObjectMapper mapper;

    /**
     * 설정에 따라 HTTP 클라이언트와 서버 선택기를 준비합니다. 이 시점에는 아직 서버에 연결하지 않습니다.
     * <p>
     * HTTP/1.1을 쓰고, 리다이렉트는 따라가지 않습니다. HA 구성의 PASSIVE 서버가 리다이렉트로 응답할 때
     * 이를 직접 감지해 ACTIVE 서버를 다시 찾기 위해서입니다.
     *
     * @param config 클라이언트 설정
     */
    public AtlasTransport(AtlasClientConfig config) {
        this.config = config;
        this.mapper = AtlasJson.mapper();
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(config.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (config.getSslContext() != null) {
            builder.sslContext(config.getSslContext());
        }
        if (config.isCookiesEnabled()) {
            // Atlas는 (Kerberos 등) 인증에 성공하면 세션 쿠키를 발급합니다. 이 쿠키를 다시 쓰면 요청마다
            // 인증 과정을 처음부터 거치지 않아도 됩니다.
            builder.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
        this.httpClient = builder.build();
        this.serverSelector = new ServerSelector(config.getServerUrls(), this::probeStatus);
    }

    /** 이 전송 계층이 쓰는 클라이언트 설정을 돌려줍니다. */
    public AtlasClientConfig getConfig() {
        return config;
    }

    /**
     * 요청을 보내고 응답 본문을 주어진 클래스로 역직렬화합니다.
     *
     * @param method       HTTP 메서드(예: {@link #GET})
     * @param path         {@code /}로 시작하는, 서버 URL 기준 경로
     * @param query        쿼리 파라미터, 없으면 {@code null}
     * @param body         JSON으로 직렬화해 보낼 요청 본문, 없으면 {@code null}
     * @param responseType 응답 클래스. {@code Void.class}이면 본문을 읽지 않습니다
     * @param <T>          응답 타입
     * @return 역직렬화된 응답, 응답 본문이 비어 있거나 {@code Void.class}이면 {@code null}
     * @throws AtlasServerException     서버가 오류 상태 코드로 응답했을 때(재시도 후에도)
     * @throws AtlasConnectionException 네트워크 수준에서 요청을 끝내지 못했을 때
     * @throws AtlasClientException     응답 본문을 해석하지 못했을 때
     */
    public <T> T call(String method, String path, QueryParams query, Object body, Class<T> responseType) {
        return call(method, path, query, body, mapper.getTypeFactory().constructType(responseType));
    }

    /**
     * {@link #call(String, String, QueryParams, Object, Class)}와 같지만, {@code List<...>} 같은 제네릭 응답
     * 타입을 {@link TypeReference}로 받습니다.
     *
     * @param method       HTTP 메서드
     * @param path         {@code /}로 시작하는, 서버 URL 기준 경로
     * @param query        쿼리 파라미터, 없으면 {@code null}
     * @param body         요청 본문, 없으면 {@code null}
     * @param responseType 응답 타입
     * @param <T>          응답 타입
     * @return 역직렬화된 응답, 응답 본문이 비어 있으면 {@code null}
     */
    public <T> T call(String method, String path, QueryParams query, Object body, TypeReference<T> responseType) {
        return call(method, path, query, body, mapper.getTypeFactory().constructType(responseType));
    }

    // 두 public call()이 공통으로 쓰는 구현입니다. 본문 직렬화 → 실행 → 응답 역직렬화 순서로 처리합니다.
    private <T> T call(String method, String path, QueryParams query, Object body, JavaType responseType) {
        String requestBody = body == null ? null : AtlasJson.toJson(body);
        String responseBody = execute(method, path, query, requestBody);
        if (responseType.getRawClass() == Void.class || responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(responseBody, responseType);
        } catch (IOException e) {
            throw new AtlasClientException("Failed to parse response of " + method + " " + path
                    + " as " + responseType + ": " + truncate(responseBody), e);
        }
    }

    /**
     * 요청을 실행하고 응답 본문을 가공하지 않은 문자열로 돌려줍니다.
     * <p>
     * 시도마다 {@link ServerSelector}로 서버를 고르고, 실패하면 {@link RetryPolicy}에 따라 기다렸다가 다시
     * 보냅니다. 재시도 여부는 다음과 같이 정합니다.
     * <ul>
     *     <li>I/O 실패: 서버에 닿지 못한 연결 실패이거나, 멱등 메서드이거나, 멱등이 아닌 메서드의 재시도가
     *     허용된 경우에만 재시도합니다.</li>
     *     <li>오류 상태 코드: 재시도 대상 상태 코드이면서 503이거나 멱등 메서드(또는 재시도 허용)인 경우에
     *     재시도합니다.</li>
     *     <li>HA 구성에서 리다이렉트(3xx) 응답: PASSIVE 서버의 응답으로 보고 항상 재시도합니다.</li>
     * </ul>
     * 시도 횟수를 모두 쓰면 마지막 실패를 예외로 던집니다. 각 시도는 {@link RequestListener}에 보고됩니다.
     *
     * @param method   HTTP 메서드
     * @param path     {@code /}로 시작하는, 서버 URL 기준 경로(예: {@code /api/atlas/v2/types/typedefs})
     * @param query    쿼리 파라미터, 없을 수 있습니다({@code null})
     * @param jsonBody JSON 요청 본문, 없을 수 있습니다({@code null})
     * @return 2xx 응답의 본문 문자열
     * @throws AtlasServerException     서버가 오류 상태 코드로 응답했을 때
     * @throws AtlasConnectionException 네트워크 수준 실패 또는 스레드 인터럽트로 요청을 끝내지 못했을 때
     */
    public String execute(String method, String path, QueryParams query, String jsonBody) {
        RetryPolicy retryPolicy = config.getRetryPolicy();
        boolean idempotent = IDEMPOTENT_METHODS.contains(method);
        int attempt = 0;
        while (true) {
            attempt++;
            URI server = serverSelector.current();
            URI uri = buildUri(server, path, query);
            long started = System.nanoTime();
            HttpResponse<String> response;
            try {
                response = httpClient.send(buildRequest(method, uri, jsonBody),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                notifyListener(method, uri, attempt, -1, started, jsonBody, null, e);
                // 연결 단계에서 실패했다면 요청이 서버에 전달되지 않았으므로 어떤 메서드든 재시도해도 안전합니다.
                boolean notSent = isConnectFailure(e);
                serverSelector.markFailed(server);
                if (attempt < retryPolicy.getMaxAttempts()
                        && (notSent || idempotent || retryPolicy.isRetryNonIdempotent())) {
                    LOG.warn("{} {} failed ({}), retrying (attempt {}/{})", method, uri, e.toString(),
                            attempt, retryPolicy.getMaxAttempts());
                    sleep(retryPolicy.backoff(attempt));
                    continue;
                }
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                throw new AtlasConnectionException(method + " " + uri + " failed: " + reason, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                notifyListener(method, uri, attempt, -1, started, jsonBody, null, e);
                throw new AtlasConnectionException(method + " " + uri + " interrupted", e);
            }
            notifyListener(method, uri, attempt, response.statusCode(), started, jsonBody, response.body(), null);

            int status = response.statusCode();
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            LOG.debug("{} {} -> {} ({} ms)", method, uri, status, elapsedMillis);
            if (LOG.isTraceEnabled()) {
                LOG.trace("request body: {}", jsonBody);
                LOG.trace("response body: {}", response.body());
            }

            if (status >= 200 && status < 300) {
                return response.body();
            }

            boolean redirected = status >= 300 && status < 400;
            if (redirected || status == 503) {
                // HA 구성의 PASSIVE 인스턴스는 리다이렉트하거나 503으로 응답하며, 이때 요청은 적용되지 않았습니다.
                // 그래서 이 서버를 실패로 표시해 다음 시도에서 ACTIVE 서버를 다시 찾게 합니다.
                serverSelector.markFailed(server);
            }
            // 503은 요청이 적용되지 않았음이 확실하므로 메서드와 상관없이 재시도합니다. 502/504 등은 서버가 이미
            // 처리했을 수도 있으므로 멱등 메서드이거나 재시도가 허용된 경우에만 재시도합니다.
            boolean retryable = retryPolicy.isRetryableStatus(status)
                    && (status == 503 || idempotent || retryPolicy.isRetryNonIdempotent());
            // HA 구성에서의 리다이렉트는 PASSIVE 서버의 응답이므로, 재시도 상태 코드 목록과 상관없이 다른 서버로 다시 보냅니다.
            if (redirected && serverSelector.isHighAvailability()) {
                retryable = true;
            }
            if (retryable && attempt < retryPolicy.getMaxAttempts()) {
                LOG.warn("{} {} returned HTTP {}, retrying (attempt {}/{})", method, uri, status,
                        attempt, retryPolicy.getMaxAttempts());
                sleep(retryPolicy.backoff(attempt));
                continue;
            }
            throw toServerException(method, uri, response);
        }
    }

    // 요청 리스너에 시도 결과를 알립니다. 리스너의 오류 때문에 실제 요청이 실패하면 안 되므로 예외는 로그만 남깁니다.
    private void notifyListener(String method, URI uri, int attempt, int status, long startedNanos,
                                String requestBody, String responseBody, Throwable failure) {
        try {
            config.getRequestListener().onExchange(new RequestListener.Exchange(method, uri, attempt, status,
                    Duration.ofNanos(System.nanoTime() - startedNanos), requestBody, responseBody, failure));
        } catch (RuntimeException e) {
            LOG.warn("Request listener failed for {} {}", method, uri, e);
        }
    }

    /**
     * 서버 하나가 보고하는 HA 상태(예: {@code ACTIVE}, {@code PASSIVE})를 돌려줍니다.
     * <p>
     * {@link ServerSelector}가 상태 확인용으로 호출합니다. 재시도하지 않고 {@link RequestListener}에도 보고하지
     * 않습니다. 응답 JSON에 {@code Status} 필드가 없으면 {@code UNKNOWN}을 돌려줍니다.
     *
     * @throws AtlasConnectionException 응답이 200이 아니거나 연결에 실패했을 때
     */
    String probeStatus(URI server) {
        URI uri = URI.create(server + STATUS_PATH);
        try {
            HttpResponse<String> response = httpClient.send(buildRequest(GET, uri, null),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new AtlasConnectionException("HTTP " + response.statusCode());
            }
            JsonNode node = mapper.readTree(response.body());
            return node.path("Status").asText("UNKNOWN");
        } catch (IOException e) {
            throw new AtlasConnectionException(e.toString(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AtlasConnectionException("interrupted", e);
        }
    }

    // 공통 헤더(Accept, User-Agent, 인증)를 붙여 요청을 만듭니다. 인증 헤더는 요청 URI마다 AuthProvider에서
    // 새로 받습니다(예: Kerberos는 서버 호스트마다 토큰이 다릅니다). 본문이 있을 때만 Content-Type을 붙입니다.
    private HttpRequest buildRequest(String method, URI uri, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(config.getRequestTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", config.getUserAgent());
        config.getAuthProvider().authorizationHeader(uri).ifPresent(value -> builder.header("Authorization", value));
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json; charset=UTF-8");
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    /**
     * 서버 URL, 경로, 쿼리 파라미터를 이어 붙여 요청 URI를 만듭니다.
     *
     * @throws IllegalArgumentException 경로가 {@code /}로 시작하지 않을 때
     */
    static URI buildUri(URI server, String path, QueryParams query) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Path must start with '/': " + path);
        }
        StringBuilder sb = new StringBuilder(server.toString()).append(path);
        if (query != null && !query.isEmpty()) {
            sb.append('?').append(query.toQueryString());
        }
        return URI.create(sb.toString());
    }

    // 오류 응답을 예외로 바꿉니다. 본문이 Atlas 형식의 JSON이면 errorCode와 errorMessage(없으면 error)를 꺼내고,
    // 아니면 본문을 잘라서 메시지로 씁니다. Location 헤더가 있으면(리다이렉트) 메시지 끝에 덧붙여 원인을 알기 쉽게 합니다.
    private AtlasServerException toServerException(String method, URI uri, HttpResponse<String> response) {
        String body = response.body();
        String errorCode = null;
        String errorMessage = null;
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = mapper.readTree(body);
                if (node.isObject()) {
                    errorCode = textOrNull(node, "errorCode");
                    errorMessage = textOrNull(node, "errorMessage");
                    if (errorMessage == null) {
                        errorMessage = textOrNull(node, "error");
                    }
                }
            } catch (IOException ignored) {
                // JSON이 아닙니다(예: 프록시가 돌려준 HTML 오류 페이지). 아래에서 원문 본문을 대신 씁니다.
            }
            if (errorMessage == null) {
                errorMessage = truncate(body);
            }
        }
        String location = response.headers().firstValue("Location").orElse(null);
        if (location != null) {
            errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "(Location: " + location + ")";
        }
        return AtlasServerException.of(response.statusCode(), errorCode, errorMessage, method, uri.toString());
    }

    // JSON 필드가 없거나 JSON null이면 null을, 아니면 문자열 값을 돌려줍니다.
    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    // 연결 자체를 맺지 못한 실패인지 판단합니다. 이 경우 요청이 서버로 전송되지 않았다고 볼 수 있습니다.
    // HttpClient는 ConnectException을 다른 IOException으로 감싸 던지기도 하므로 원인(cause)도 확인합니다.
    private static boolean isConnectFailure(IOException e) {
        return e instanceof ConnectException || e instanceof HttpConnectTimeoutException
                || e.getCause() instanceof ConnectException;
    }

    // 재시도 전 대기합니다. 대기 중 인터럽트되면 인터럽트 상태를 복구하고 재시도를 멈춥니다.
    private static void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AtlasConnectionException("Interrupted while waiting to retry", e);
        }
    }

    // 예외 메시지에 넣을 문자열을 MAX_ERROR_BODY_LENGTH 글자로 자르고, 잘렸으면 "..."을 붙입니다.
    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_BODY_LENGTH ? value : value.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }
}
