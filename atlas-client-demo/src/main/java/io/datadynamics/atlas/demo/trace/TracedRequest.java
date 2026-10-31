package io.datadynamics.atlas.demo.trace;

import java.time.Instant;

/**
 * Atlas 클라이언트의 HTTP 시도 하나를 기록한 값입니다. 재시도된 요청은 시도마다 따로 기록됩니다.
 *
 * @param id            기록 순서대로 1부터 매기는 일련번호
 * @param time          기록한 시각
 * @param method        HTTP 메서드
 * @param uri           선택된 서버와 쿼리 문자열을 포함한 전체 URI
 * @param attempt       같은 요청 안에서 몇 번째 시도인지(1부터)
 * @param statusCode    HTTP 상태 코드. 응답을 받지 못했으면 {@code -1}
 * @param elapsedMillis 걸린 시간(밀리초)
 * @param requestBody   요청 본문(길면 잘림), 없으면 null
 * @param responseBody  응답 본문(길면 잘림), 없으면 null
 * @param failure       응답을 받지 못했을 때의 I/O 실패 내용("예외 클래스 이름: 메시지")
 */
public record TracedRequest(long id, Instant time, String method, String uri, int attempt, int statusCode,
                            long elapsedMillis, String requestBody, String responseBody, String failure) {

    /**
     * 성공한 시도인지 알려 줍니다.
     *
     * @return 상태 코드가 2xx이면 {@code true}
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * 서버 부분을 뺀 경로와 쿼리 문자열을 돌려줍니다. 화면에 짧게 보여 주기 위해 씁니다.
     *
     * @return {@code /api/}부터 시작하는 부분. URI에 {@code /api/}가 없으면 URI 전체
     */
    public String path() {
        int start = uri.indexOf("/api/");
        return start >= 0 ? uri.substring(start) : uri;
    }
}
