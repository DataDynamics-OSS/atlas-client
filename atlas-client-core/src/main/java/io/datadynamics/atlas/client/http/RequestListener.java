package io.datadynamics.atlas.client.http;

import java.net.URI;
import java.time.Duration;

/**
 * 클라이언트가 보내는 모든 HTTP 시도를 관찰합니다. 로깅, 메트릭, 트레이싱에 씁니다.
 * <p>
 * 시도마다 한 번씩 호출됩니다. 그래서 재시도된 요청은 {@link Exchange#attempt()}가 1, 2, ...로 늘어나며
 * 여러 번 보고됩니다. HA 구성에서 ACTIVE 서버를 찾기 위한 상태 확인 요청은 보고하지 않습니다.
 * 리스너는 응답(또는 실패)을 받은 직후, 결과를 돌려주거나 재시도하기 전에 호출한 스레드에서 실행되므로
 * 빠르게 끝나야 합니다. 리스너가 던진 예외는 로그로 남기고 무시합니다.
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

    /**
     * HTTP 시도 하나가 끝났을 때 호출됩니다.
     *
     * @param exchange 시도의 요청·응답 정보
     */
    void onExchange(Exchange exchange);

    /** 아무것도 하지 않는 리스너를 돌려줍니다. 리스너를 설정하지 않았을 때의 기본값으로 씁니다. */
    static RequestListener none() {
        return exchange -> {
        };
    }

    /**
     * HTTP 시도 하나의 정보입니다.
     *
     * @param method       HTTP 메서드
     * @param uri          요청한 전체 URI(선택된 서버와 쿼리 문자열 포함)
     * @param attempt      첫 시도는 1, 첫 재시도는 2, ...
     * @param statusCode   HTTP 상태 코드, 응답을 받지 못했으면 {@code -1}
     * @param elapsed      요청을 보낸 때부터 응답 또는 실패를 받을 때까지 걸린 시간
     * @param requestBody  JSON 요청 본문, 없으면 {@code null}
     * @param responseBody 응답 본문, 응답을 받지 못했으면 {@code null}
     * @param failure      응답을 받지 못했을 때의 I/O 실패, 그 밖에는 {@code null}
     */
    record Exchange(String method, URI uri, int attempt, int statusCode, Duration elapsed,
                    String requestBody, String responseBody, Throwable failure) {

        /** 상태 코드가 2xx이면 {@code true}를 돌려줍니다. 응답을 받지 못한 시도는 {@code false}입니다. */
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
