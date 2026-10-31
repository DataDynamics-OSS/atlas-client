package io.datadynamics.atlas.client.exception;

/**
 * HTTP 400 응답을 나타냅니다. Atlas가 요청 내용을 거부했을 때 발생합니다.
 * <p>
 * 대표적인 원인은 잘못된 타입 정의나 타입에 맞지 않는 속성 값입니다. 요청 본문을 고치지 않으면
 * 다시 보내도 같은 결과가 나옵니다.
 */
public class AtlasBadRequestException extends AtlasServerException {

    /**
     * 예외를 만듭니다. 보통 직접 호출하지 않고 {@link AtlasServerException#of}를 통해 생성됩니다.
     *
     * @param statusCode   HTTP 상태 코드
     * @param errorCode    Atlas 오류 코드(예: {@code ATLAS-404-00-005}), 응답 본문에 없으면 {@code null}
     * @param errorMessage Atlas가 돌려준 오류 메시지, 없으면 {@code null}
     * @param method       요청한 HTTP 메서드
     * @param url          요청한 URL
     */
    public AtlasBadRequestException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(statusCode, errorCode, errorMessage, method, url);
    }
}
