package io.datadynamics.atlas.client.exception;

/**
 * HTTP 502/503/504 응답을 나타냅니다. 서버를 쓸 수 없거나, HA 구성에서 PASSIVE 상태이거나,
 * 과부하 상태일 때 발생합니다.
 * <p>
 * 일시적인 상황일 가능성이 높습니다. 기본 {@link io.datadynamics.atlas.client.http.RetryPolicy}는 이 상태
 * 코드들을 재시도하며, 재시도 횟수를 모두 쓴 뒤에도 실패하면 이 예외가 호출자에게 전달됩니다.
 */
public class AtlasUnavailableException extends AtlasServerException {

    /**
     * 예외를 만듭니다. 보통 직접 호출하지 않고 {@link AtlasServerException#of}를 통해 생성됩니다.
     *
     * @param statusCode   HTTP 상태 코드
     * @param errorCode    Atlas 오류 코드(예: {@code ATLAS-404-00-005}), 응답 본문에 없으면 {@code null}
     * @param errorMessage Atlas가 돌려준 오류 메시지, 없으면 {@code null}
     * @param method       요청한 HTTP 메서드
     * @param url          요청한 URL
     */
    public AtlasUnavailableException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
