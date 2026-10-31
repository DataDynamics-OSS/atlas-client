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
 * Sends requests to Atlas: server selection (HA), authentication, retries, JSON (de)serialization
 * and mapping of error responses to {@link AtlasServerException} subclasses.
 * <p>
 * This is the low-level entry point used by the API classes; most applications should use
 * {@link io.datadynamics.atlas.client.AtlasClient} instead.
 */
public final class AtlasTransport {

    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";

    static final String STATUS_PATH = "/api/atlas/admin/status";

    private static final Logger LOG = LoggerFactory.getLogger(AtlasTransport.class);
    private static final Set<String> IDEMPOTENT_METHODS = Set.of(GET, PUT, DELETE, "HEAD", "OPTIONS");
    private static final int MAX_ERROR_BODY_LENGTH = 2000;

    private final AtlasClientConfig config;
    private final HttpClient httpClient;
    private final ServerSelector serverSelector;
    private final ObjectMapper mapper;

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
            // Atlas issues a session cookie after (Kerberos) authentication; reusing it avoids
            // a full authentication round trip on every request.
            builder.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
        this.httpClient = builder.build();
        this.serverSelector = new ServerSelector(config.getServerUrls(), this::probeStatus);
    }

    public AtlasClientConfig getConfig() {
        return config;
    }

    public <T> T call(String method, String path, QueryParams query, Object body, Class<T> responseType) {
        return call(method, path, query, body, mapper.getTypeFactory().constructType(responseType));
    }

    public <T> T call(String method, String path, QueryParams query, Object body, TypeReference<T> responseType) {
        return call(method, path, query, body, mapper.getTypeFactory().constructType(responseType));
    }

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
     * Executes a request and returns the raw response body ({@code null} for empty responses).
     *
     * @param path        path starting with {@code /}, relative to the server URL, e.g. {@code /api/atlas/v2/types/typedefs}
     * @param query       query parameters, may be {@code null}
     * @param jsonBody    JSON request body, may be {@code null}
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
                // A passive HA instance redirects or answers 503; the request was not applied.
                serverSelector.markFailed(server);
            }
            boolean retryable = retryPolicy.isRetryableStatus(status)
                    && (status == 503 || idempotent || retryPolicy.isRetryNonIdempotent());
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

    private void notifyListener(String method, URI uri, int attempt, int status, long startedNanos,
                                String requestBody, String responseBody, Throwable failure) {
        try {
            config.getRequestListener().onExchange(new RequestListener.Exchange(method, uri, attempt, status,
                    Duration.ofNanos(System.nanoTime() - startedNanos), requestBody, responseBody, failure));
        } catch (RuntimeException e) {
            LOG.warn("Request listener failed for {} {}", method, uri, e);
        }
    }

    /** Returns the HA status reported by one server, e.g. {@code ACTIVE} or {@code PASSIVE}. */
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
                // Not JSON (e.g. an HTML error page from a proxy); fall back to the raw body.
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

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean isConnectFailure(IOException e) {
        return e instanceof ConnectException || e instanceof HttpConnectTimeoutException
                || e.getCause() instanceof ConnectException;
    }

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

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_BODY_LENGTH ? value : value.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }
}
