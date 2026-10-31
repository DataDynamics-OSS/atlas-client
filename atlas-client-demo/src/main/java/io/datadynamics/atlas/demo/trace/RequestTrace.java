package io.datadynamics.atlas.demo.trace;

import io.datadynamics.atlas.client.http.RequestListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records the HTTP requests the Atlas client sends, so each page can show what a demo action did.
 * <p>
 * Client calls run on the servlet thread, so requests are collected per thread between
 * {@link #begin()} and {@link #end()} (see {@link TraceInterceptor}); all requests also go to a
 * bounded history.
 */
@Component
public class RequestTrace {

    static final int HISTORY_SIZE = 300;
    static final int MAX_BODY_LENGTH = 20_000;

    private final ThreadLocal<List<TracedRequest>> current = new ThreadLocal<>();
    private final Deque<TracedRequest> history = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();

    /** Starts collecting requests made by this thread and returns the (live) list. */
    public List<TracedRequest> begin() {
        List<TracedRequest> requests = new ArrayList<>();
        current.set(requests);
        return requests;
    }

    public void end() {
        current.remove();
    }

    /** Requests made by this thread since {@link #begin()}; empty outside a traced web request. */
    public List<TracedRequest> current() {
        List<TracedRequest> requests = current.get();
        return requests != null ? requests : List.of();
    }

    public void record(RequestListener.Exchange exchange) {
        Throwable failure = exchange.failure();
        TracedRequest request = new TracedRequest(sequence.incrementAndGet(), Instant.now(), exchange.method(),
                exchange.uri().toString(), exchange.attempt(), exchange.statusCode(), exchange.elapsed().toMillis(),
                truncate(exchange.requestBody()), truncate(exchange.responseBody()),
                failure == null ? null : failure.getClass().getSimpleName() + ": " + failure.getMessage());
        List<TracedRequest> requests = current.get();
        if (requests != null) {
            requests.add(request);
        }
        synchronized (history) {
            history.addFirst(request);
            while (history.size() > HISTORY_SIZE) {
                history.removeLast();
            }
        }
    }

    /** Most recent first. */
    public List<TracedRequest> history() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
    }

    private static String truncate(String body) {
        if (body == null || body.length() <= MAX_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_BODY_LENGTH) + "\n... (" + (body.length() - MAX_BODY_LENGTH) + " more characters)";
    }
}
