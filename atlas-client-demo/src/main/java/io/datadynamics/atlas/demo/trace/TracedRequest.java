package io.datadynamics.atlas.demo.trace;

import java.time.Instant;

/**
 * One HTTP attempt of the Atlas client.
 *
 * @param statusCode {@code -1} if no response was received
 * @param failure    I/O failure when no response was received
 */
public record TracedRequest(long id, Instant time, String method, String uri, int attempt, int statusCode,
                            long elapsedMillis, String requestBody, String responseBody, String failure) {

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /** Path and query, without the server part. */
    public String path() {
        int start = uri.indexOf("/api/");
        return start >= 0 ? uri.substring(start) : uri;
    }
}
