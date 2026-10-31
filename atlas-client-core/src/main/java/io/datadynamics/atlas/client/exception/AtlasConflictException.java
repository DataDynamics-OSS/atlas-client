package io.datadynamics.atlas.client.exception;

/**
 * HTTP 409 응답을 나타냅니다. 만들려는 리소스가 이미 있을 때 발생합니다.
 * <p>
 * 예를 들어 이미 정의된 타입을 다시 생성하려고 하면 이 예외가 발생합니다.
 */
public class AtlasConflictException extends AtlasServerException {

    /**
     * 예외를 만듭니다. 보통 직접 호출하지 않고 {@link AtlasServerException#of}를 통해 생성됩니다.
     *
     * @param statusCode   HTTP 상태 코드
     * @param errorCode    Atlas 오류 코드(예: {@code ATLAS-404-00-005}), 응답 본문에 없으면 {@code null}
     * @param errorMessage Atlas가 돌려준 오류 메시지, 없으면 {@code null}
     * @param method       요청한 HTTP 메서드
     * @param url          요청한 URL
     */
    public AtlasConflictException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
