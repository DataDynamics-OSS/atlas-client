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
 * 단위 테스트에서 Atlas 대신 쓰는, 같은 프로세스 안에서 도는 HTTP 서버입니다. Docker가 필요 없고 몇 밀리초 만에 시작합니다.
 * <pre>{@code
 * try (FakeAtlasServer atlas = new FakeAtlasServer()) {
 *     atlas.on("GET", "/api/atlas/admin/version", 200, "{\"Version\":\"2.3.0\"}");
 *     AtlasClient client = AtlasClient.builder().serverUrl(atlas.url()).build();
 *     ...
 *     assertThat(atlas.requests("POST", "/api/atlas/v2/entity/bulk")).hasSize(1);
 * }
 * }</pre>
 * 응답은 HTTP 메서드와 경로(선택적으로 요청 조건)로 고릅니다. 같은 경로에 응답을 여러 개 등록하면 등록한 순서대로
 * 돌려주고 마지막 응답은 계속 반복하므로, 재시도나 페이지 넘김 시나리오를 쉽게 꾸밀 수 있습니다.
 * 맞는 경로가 없는 요청에는 Atlas 형식의 404 오류를 돌려줍니다.
 * <p>
 * {@code 127.0.0.1}의 빈 포트에 바인딩하며, 생성자에서 바로 시작합니다. 받은 요청과 등록한 경로는
 * 동시성 컬렉션에 보관하므로 테스트 스레드와 서버 스레드가 함께 접근해도 안전합니다.
 */
public final class FakeAtlasServer implements AutoCloseable {

    /** 서버가 받은 요청 하나입니다. 테스트에서 메서드, 경로, 쿼리, 본문, 헤더를 확인할 때 씁니다. */
    public static final class Request {
        /** HTTP 메서드(예: {@code GET}). */
        public final String method;
        /** 쿼리 문자열을 뺀 디코딩된 경로입니다. */
        public final String path;
        /** 디코딩하지 않은 쿼리 문자열입니다. 없으면 {@code null}입니다. */
        public final String rawQuery;
        /** UTF-8로 읽은 요청 본문입니다. 본문이 없으면 빈 문자열입니다. */
        public final String body;
        /** 요청 헤더입니다. */
        public final Map<String, List<String>> headers;

        Request(String method, String path, String rawQuery, String body, Map<String, List<String>> headers) {
            this.method = method;
            this.path = path;
            this.rawQuery = rawQuery;
            this.body = body;
            this.headers = headers;
        }

        /**
         * 이름이 일치하는(대소문자 무시) 헤더의 첫 번째 값을 돌려줍니다.
         *
         * @param name 헤더 이름
         * @return 첫 번째 값. 헤더가 없으면 {@code null}
         */
        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0))
                    .findFirst().orElse(null);
        }

        /**
         * URL 디코딩한 쿼리 파라미터를 돌려줍니다(예: {@code attr:qualifiedName}). 같은 이름이 여러 번 나오면
         * 값이 순서대로 목록에 들어가고, 값 없는 파라미터는 빈 문자열이 됩니다.
         *
         * @return 이름별 값 목록. 쿼리가 없으면 빈 맵
         */
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

        /**
         * 디코딩한 쿼리 파라미터의 첫 번째 값을 돌려줍니다.
         *
         * @param name 파라미터 이름
         * @return 첫 번째 값. 없으면 {@code null}
         */
        public String queryParam(String name) {
            List<String> values = queryParams().get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }

        /** {@code GET /path?query} 형태로 요청을 요약합니다. */
        @Override
        public String toString() {
            return method + " " + path + (rawQuery == null ? "" : "?" + rawQuery);
        }
    }

    /** 미리 정해 둔 응답입니다. {@link #respond(int, String)}나 {@link #error(int, String, String)}로 만듭니다. */
    public static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    /** 메서드, 경로, 선택적 조건으로 요청을 고르고, 등록된 응답 핸들러를 순서대로 내주는 경로입니다. */
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

        /**
         * 다음 핸들러를 돌려줍니다. 둘 이상 남아 있으면 맨 앞을 꺼내고, 하나만 남으면 꺼내지 않고 계속 돌려줍니다.
         * 크기 확인과 꺼내기가 한 동작이 되도록 동기화합니다.
         */
        synchronized Function<Request, Response> next() {
            return handlers.size() > 1 ? handlers.pollFirst() : handlers.peekFirst();
        }
    }

    private final HttpServer server;
    private final List<Route> routes = new CopyOnWriteArrayList<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    /**
     * {@code 127.0.0.1}의 빈 포트에 서버를 만들고 바로 시작합니다.
     *
     * @throws IllegalStateException 서버 소켓을 열지 못했을 때
     */
    public FakeAtlasServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start fake Atlas server", e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * {@code AtlasClient.builder().serverUrl(...)}에 넘길 기본 URL을 돌려줍니다.
     *
     * @return {@code http://127.0.0.1:<포트>}
     */
    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 주어진 상태 코드와 본문으로 응답을 만듭니다. 함수형 핸들러에서 씁니다.
     *
     * @param status HTTP 상태 코드
     * @param body   응답 본문. {@code null}이나 빈 문자열이면 본문 없이 보냅니다
     * @return 응답
     */
    public static Response respond(int status, String body) {
        return new Response(status, body);
    }

    /**
     * Atlas 형식의 오류 응답({@code errorCode}와 {@code errorMessage}를 담은 JSON)을 만듭니다.
     * 메시지 안의 큰따옴표는 이스케이프합니다.
     *
     * @param status       HTTP 상태 코드
     * @param errorCode    Atlas 오류 코드(예: {@code ATLAS-404-00-000})
     * @param errorMessage 오류 메시지
     * @return 응답
     */
    public static Response error(int status, String errorCode, String errorMessage) {
        return new Response(status, "{\"errorCode\":\"" + errorCode + "\",\"errorMessage\":\""
                + errorMessage.replace("\"", "\\\"") + "\"}");
    }

    /**
     * 메서드와 경로에 고정 응답을 등록합니다.
     *
     * @param method HTTP 메서드
     * @param path   쿼리 문자열을 뺀 경로(정확히 일치해야 합니다)
     * @param status HTTP 상태 코드
     * @param body   응답 본문
     * @return 이 서버(체이닝용)
     */
    public FakeAtlasServer on(String method, String path, int status, String body) {
        return on(method, path, request -> new Response(status, body));
    }

    /**
     * 메서드와 경로에 요청에 따라 응답을 만드는 핸들러를 등록합니다. 핸들러가 예외를 던지면 500 오류로 응답합니다.
     *
     * @param method  HTTP 메서드
     * @param path    쿼리 문자열을 뺀 경로(정확히 일치해야 합니다)
     * @param handler 요청을 받아 응답을 돌려주는 함수
     * @return 이 서버(체이닝용)
     */
    public FakeAtlasServer on(String method, String path, Function<Request, Response> handler) {
        return on(method, path, null, handler);
    }

    /**
     * {@code condition}을 만족하는 요청(예: 특정 쿼리 파라미터 값)에만 적용되는 경로를 등록합니다.
     * 같은 메서드와 경로라면 조건 있는 경로를 조건 없는 경로보다 먼저 확인합니다.
     * <p>
     * 같은 메서드, 경로, 조건 객체(같은 인스턴스)로 다시 등록하면 새 경로를 만들지 않고 기존 경로의 핸들러 목록
     * 뒤에 추가합니다.
     *
     * @param method    HTTP 메서드
     * @param path      쿼리 문자열을 뺀 경로
     * @param condition 요청 조건. {@code null}이면 조건 없는 경로입니다
     * @param handler   요청을 받아 응답을 돌려주는 함수
     * @return 이 서버(체이닝용)
     */
    public FakeAtlasServer on(String method, String path, Predicate<Request> condition,
                              Function<Request, Response> handler) {
        // 조건은 참조 비교(==)로 같은 경로인지 판단합니다. 람다는 동등성 비교를 할 수 없기 때문입니다.
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

    /**
     * 받은 모든 요청을 받은 순서대로 돌려줍니다. 맞는 경로가 없어 404로 응답한 요청도 포함합니다.
     *
     * @return 수정할 수 없는 목록. 서버가 요청을 더 받으면 내용이 늘어납니다
     */
    public List<Request> requests() {
        return Collections.unmodifiableList(requests);
    }

    /**
     * 주어진 메서드와 경로로 받은 요청만 순서대로 돌려줍니다.
     *
     * @param method HTTP 메서드
     * @param path   쿼리 문자열을 뺀 경로
     * @return 일치하는 요청 목록(새 목록)
     */
    public List<Request> requests(String method, String path) {
        return requests.stream().filter(r -> r.method.equals(method) && r.path.equals(path))
                .collect(Collectors.toList());
    }

    /**
     * 마지막으로 받은 요청을 돌려줍니다.
     *
     * @return 마지막 요청
     * @throws IllegalStateException 아직 받은 요청이 없을 때
     */
    public Request lastRequest() {
        if (requests.isEmpty()) {
            throw new IllegalStateException("No request received");
        }
        return requests.get(requests.size() - 1);
    }

    /** 등록한 경로와 기록한 요청을 모두 지웁니다. 서버는 계속 실행됩니다. */
    public void reset() {
        routes.clear();
        requests.clear();
    }

    /**
     * 모든 요청을 처리하는 HTTP 핸들러입니다. 요청을 기록한 뒤 처음으로 일치하는 경로의 다음 핸들러로 응답을 만듭니다.
     * 응답은 항상 {@code Content-Type: application/json}으로 보냅니다.
     */
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

        // 본문이 비어 있으면 길이로 -1을 넘겨 본문 없는 응답으로 보냅니다.
        byte[] bytes = response.body == null ? new byte[0] : response.body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 서버를 즉시 멈추고 포트를 닫습니다. */
    @Override
    public void close() {
        server.stop(0);
    }
}
