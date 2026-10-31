package io.datadynamics.atlas.client.exception;

/**
 * HTTP 404 응답을 나타냅니다. 요청한 타입, 엔티티 또는 리소스가 없을 때 발생합니다.
 * <p>
 * API 클래스의 {@code find...} 조회 메서드(예: {@code TypesApi.findByName}, {@code EntitiesApi.find})는
 * 이 예외를 잡아 {@link java.util.Optional#empty()}로 바꿔 돌려줍니다. {@code get...} 메서드는 그대로 던집니다.
 */
public class AtlasNotFoundException extends AtlasServerException {

    /**
     * 예외를 만듭니다. 보통 직접 호출하지 않고 {@link AtlasServerException#of}를 통해 생성됩니다.
     *
     * @param statusCode   HTTP 상태 코드
     * @param errorCode    Atlas 오류 코드(예: {@code ATLAS-404-00-005}), 응답 본문에 없으면 {@code null}
     * @param errorMessage Atlas가 돌려준 오류 메시지, 없으면 {@code null}
     * @param method       요청한 HTTP 메서드
     * @param url          요청한 URL
     */
    public AtlasNotFoundException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
