package io.datadynamics.atlas.client.http;

import java.net.URI;
import java.time.Duration;

/**
 * Observes every HTTP attempt the client makes, for logging, metrics or tracing.
 * <p>
 * Called once per attempt, so a request that is retried is reported several times with an
 * increasing {@link Exchange#attempt()}. HA status probes are not reported. The listener runs on
 * the calling thread after the response (or failure) and before the result is returned or
 * retried; keep it fast. Exceptions thrown by the listener are logged and ignored.
 * <pre>{@code
 * AtlasClient atlas = AtlasClient.builder()
 *         .serverUrl("http://atlas:21000")
 *         .requestListener(e -> log.info("{} {} -> {} ({} ms)", e.method(), e.uri(), e.statusCode(),
 *                 e.elapsed().toMillis()))
 *         .build();
 * }</pre>
 */
@FunctionalInterface
public interface RequestListener {

    void onExchange(Exchange exchange);

    static RequestListener none() {
        return exchange -> {
        };
    }

    /**
     * One HTTP attempt.
     *
     * @param method       HTTP method
     * @param uri          full request URI, including the selected server and query string
     * @param attempt      1 for the first attempt, 2 for the first retry, ...
     * @param statusCode   HTTP status, or {@code -1} if no response was received
     * @param elapsed      time from sending the request to receiving the response or failure
     * @param requestBody  JSON request body, or {@code null}
     * @param responseBody response body, or {@code null} if no response was received
     * @param failure      I/O failure when no response was received, otherwise {@code null}
     */
    record Exchange(String method, URI uri, int attempt, int statusCode, Duration elapsed,
                    String requestBody, String responseBody, Throwable failure) {

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
