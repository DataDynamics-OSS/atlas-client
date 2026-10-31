package io.datadynamics.atlas.client.exception;

/**
 * HTTP 401 응답을 나타냅니다. 인증 정보가 없거나 올바르지 않을 때 발생합니다.
 * <p>
 * 사용자 이름·비밀번호, 토큰, Kerberos 티켓 등 {@link io.datadynamics.atlas.client.auth.AuthProvider}가
 * 붙인 인증 정보를 Atlas가 받아들이지 않았다는 뜻입니다. 같은 요청을 다시 보내도 대개 결과가 같으므로
 * 인증 설정을 먼저 확인해야 합니다.
 */
public class AtlasAuthenticationException extends AtlasServerException {

    /**
     * 예외를 만듭니다. 보통 직접 호출하지 않고 {@link AtlasServerException#of}를 통해 생성됩니다.
     *
     * @param statusCode   HTTP 상태 코드
     * @param errorCode    Atlas 오류 코드(예: {@code ATLAS-404-00-005}), 응답 본문에 없으면 {@code null}
     * @param errorMessage Atlas가 돌려준 오류 메시지, 없으면 {@code null}
     * @param method       요청한 HTTP 메서드
     * @param url          요청한 URL
     */
    public AtlasAuthenticationException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
