package io.datadynamics.atlas.client.exception;

/**
 * HTTP 403 응답을 나타냅니다. 인증은 되었지만 해당 작업을 수행할 권한이 없을 때 발생합니다.
 * <p>
 * 인증 정보 자체는 유효하므로 {@link AtlasAuthenticationException}과 구분됩니다. 사용자에게 필요한 권한이
 * 부여되어 있는지 Atlas 쪽 권한 설정을 확인해야 합니다.
 */
public class AtlasAuthorizationException extends AtlasServerException {

    /**
     * 예외를 만듭니다. 보통 직접 호출하지 않고 {@link AtlasServerException#of}를 통해 생성됩니다.
     *
     * @param statusCode   HTTP 상태 코드
     * @param errorCode    Atlas 오류 코드(예: {@code ATLAS-404-00-005}), 응답 본문에 없으면 {@code null}
     * @param errorMessage Atlas가 돌려준 오류 메시지, 없으면 {@code null}
     * @param method       요청한 HTTP 메서드
     * @param url          요청한 URL
     */
    public AtlasAuthorizationException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
