package io.datadynamics.atlas.client.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * In-process HTTP server standing in for Atlas in unit tests. No Docker, starts in milliseconds.
 * <pre>{@code
 * try (FakeAtlasServer atlas = new FakeAtlasServer()) {
 *     atlas.on("GET", "/api/atlas/admin/version", 200, "{\"Version\":\"2.3.0\"}");
 *     AtlasClient client = AtlasClient.builder().serverUrl(atlas.url()).build();
 *     ...
 *     assertThat(atlas.requests("POST", "/api/atlas/v2/entity/bulk")).hasSize(1);
 * }
 * }</pre>
 * Responses are matched by method and path (and optionally a request predicate). Several
 * responses registered for the same route are returned in order, the last one repeating, which
 * makes retry and paging scenarios easy to script. Unmatched requests get an Atlas-style 404.
 */
public final class FakeAtlasServer implements AutoCloseable {

    /** A request received by the server. */
    public static final class Request {
        public final String method;
        public final String path;
        public final String rawQuery;
        public final String body;
        public final Map<String, List<String>> headers;

        Request(String method, String path, String rawQuery, String body, Map<String, List<String>> headers) {
            this.method = method;
            this.path = path;
            this.rawQuery = rawQuery;
            this.body = body;
            this.headers = headers;
        }

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0))
                    .findFirst().orElse(null);
        }

        /** Decoded query parameters, e.g. {@code attr:qualifiedName}. */
        public Map<String, List<String>> queryParams() {
            Map<String, List<String>> params = new LinkedHashMap<>();
            if (rawQuery == null || rawQuery.isEmpty()) {
                return params;
            }
            for (String pair : rawQuery.split("&")) {
                int eq = pair.indexOf('=');
                String name = decode(eq < 0 ? pair : pair.substring(0, eq));
                String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
                params.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            }
            return params;
        }

        /** First value of a decoded query parameter, or {@code null}. */
        public String queryParam(String name) {
            List<String> values = queryParams().get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return method + " " + path + (rawQuery == null ? "" : "?" + rawQuery);
        }
    }

    /** A scripted response. */
    public static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final class Route {
        final String method;
        final String path;
        final Predicate<Request> condition;
        final Deque<Function<Request, Response>> handlers = new ConcurrentLinkedDeque<>();

        Route(String method, String path, Predicate<Request> condition) {
            this.method = method;
            this.path = path;
            this.condition = condition;
        }

        boolean matches(Request request) {
            return method.equals(request.method) && path.equals(request.path)
                    && (condition == null || condition.test(request));
        }

        synchronized Function<Request, Response> next() {
            return handlers.size() > 1 ? handlers.pollFirst() : handlers.peekFirst();
        }
    }

    private final HttpServer server;
    private final List<Route> routes = new CopyOnWriteArrayList<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public FakeAtlasServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start fake Atlas server", e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    /** Base URL to pass to {@code AtlasClient.builder().serverUrl(...)}. */
    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public static Response respond(int status, String body) {
        return new Response(status, body);
    }

    /** Atlas-style error response. */
    public static Response error(int status, String errorCode, String errorMessage) {
        return new Response(status, "{\"errorCode\":\"" + errorCode + "\",\"errorMessage\":\""
                + errorMessage.replace("\"", "\\\"") + "\"}");
    }

    public FakeAtlasServer on(String method, String path, int status, String body) {
        return on(method, path, request -> new Response(status, body));
    }

    public FakeAtlasServer on(String method, String path, Function<Request, Response> handler) {
        return on(method, path, null, handler);
    }

    /**
     * Route that only applies to requests matching {@code condition}, e.g. a query parameter value.
     * Conditional routes are tried before unconditional ones for the same method and path.
     */
    public FakeAtlasServer on(String method, String path, Predicate<Request> condition,
                              Function<Request, Response> handler) {
        Route route = routes.stream()
                .filter(r -> r.method.equals(method) && r.path.equals(path) && r.condition == condition)
                .findFirst()
                .orElseGet(() -> {
                    Route created = new Route(method, path, condition);
                    if (condition != null) {
                        routes.add(0, created);
                    } else {
                        routes.add(created);
                    }
                    return created;
                });
        route.handlers.addLast(handler);
        return this;
    }

    /** All requests received, in order. */
    public List<Request> requests() {
        return Collections.unmodifiableList(requests);
    }

    public List<Request> requests(String method, String path) {
        return requests.stream().filter(r -> r.method.equals(method) && r.path.equals(path))
                .collect(Collectors.toList());
    }

    public Request lastRequest() {
        if (requests.isEmpty()) {
            throw new IllegalStateException("No request received");
        }
        return requests.get(requests.size() - 1);
    }

    /** Forgets routes and recorded requests. */
    public void reset() {
        routes.clear();
        requests.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Request request = new Request(exchange.getRequestMethod(), uri.getPath(), uri.getRawQuery(), body,
                exchange.getRequestHeaders());
        requests.add(request);

        Response response;
        Route route = routes.stream().filter(r -> r.matches(request)).findFirst().orElse(null);
        if (route == null || route.handlers.isEmpty()) {
            response = error(404, "ATLAS-404-00-000", "no route for " + request.method + " " + request.path);
        } else {
            try {
                response = route.next().apply(request);
            } catch (RuntimeException e) {
                response = error(500, "ATLAS-500-00-000", "fake handler failed: " + e);
            }
        }

        byte[] bytes = response.body == null ? new byte[0] : response.body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
