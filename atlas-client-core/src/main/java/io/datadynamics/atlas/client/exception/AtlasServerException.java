package io.datadynamics.atlas.client.exception;

/**
 * Atlas가 성공이 아닌 HTTP 상태 코드(2xx 이외)로 응답했을 때 발생합니다.
 * <p>
 * Atlas의 오류 응답 본문은 {@code {"errorCode":"ATLAS-404-00-005","errorMessage":"..."}} 형태입니다.
 * 두 값이 본문에 있으면 각각 {@link #getErrorCode()}, {@link #getErrorMessage()}로 꺼낼 수 있습니다.
 * <p>
 * 자주 쓰는 상태 코드는 더 구체적인 하위 클래스로 만들어지므로({@link #of} 참고), 호출하는 쪽은
 * 예를 들어 {@link AtlasNotFoundException}만 골라 잡을 수 있습니다.
 */
public class AtlasServerException extends AtlasClientException {

    private final int statusCode;
    private final String errorCode;
    private final String errorMessage;
    private final String method;
    private final String url;

    /**
     * 예외를 만듭니다. 예외 메시지는 {@code "GET <url> -> HTTP 404 [ATLAS-404-00-005]: <메시지>"} 형태로
     * 조립됩니다. 상태 코드에 맞는 하위 클래스가 필요하면 {@link #of}를 사용합니다.
     *
     * @param statusCode   HTTP 상태 코드
     * @param errorCode    Atlas 오류 코드, 응답 본문에 없으면 {@code null}
     * @param errorMessage Atlas가 돌려준 오류 메시지, 없으면 {@code null}
     * @param method       요청한 HTTP 메서드
     * @param url          요청한 URL
     */
    public AtlasServerException(int statusCode, String errorCode, String errorMessage, String method, String url) {
        super(buildMessage(statusCode, errorCode, errorMessage, method, url));
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.method = method;
        this.url = url;
    }

    // 예외 메시지를 "메서드 URL -> HTTP 상태 [오류 코드]: 오류 메시지" 형태로 만듭니다.
    // 오류 코드와 메시지는 있을 때만 붙입니다.
    private static String buildMessage(int statusCode, String errorCode, String errorMessage, String method, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(url).append(" -> HTTP ").append(statusCode);
        if (errorCode != null) {
            sb.append(" [").append(errorCode).append(']');
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            sb.append(": ").append(errorMessage);
        }
        return sb.toString();
    }

    /**
     * 주어진 HTTP 상태 코드에 맞는 가장 구체적인 예외 타입을 만듭니다.
     * <p>
     * 400은 {@link AtlasBadRequestException}, 401은 {@link AtlasAuthenticationException},
     * 403은 {@link AtlasAuthorizationException}, 404는 {@link AtlasNotFoundException},
     * 409는 {@link AtlasConflictException}, 502/503/504는 {@link AtlasUnavailableException}이 되며,
     * 그 밖의 상태 코드는 {@link AtlasServerException} 자체가 됩니다.
     *
     * @param statusCode   HTTP 상태 코드
     * @param errorCode    Atlas 오류 코드, 없으면 {@code null}
     * @param errorMessage 오류 메시지, 없으면 {@code null}
     * @param method       요청한 HTTP 메서드
     * @param url          요청한 URL
     * @return 상태 코드에 맞는 예외 객체(던지지 않고 돌려주기만 합니다)
     */
    public static AtlasServerException of(int statusCode, String errorCode, String errorMessage, String method, String url) {
        switch (statusCode) {
            case 400:
                return new AtlasBadRequestException(statusCode, errorCode, errorMessage, method, url);
            case 401:
                return new AtlasAuthenticationException(statusCode, errorCode, errorMessage, method, url);
            case 403:
                return new AtlasAuthorizationException(statusCode, errorCode, errorMessage, method, url);
            case 404:
                return new AtlasNotFoundException(statusCode, errorCode, errorMessage, method, url);
            case 409:
                return new AtlasConflictException(statusCode, errorCode, errorMessage, method, url);
            case 502:
            case 503:
            case 504:
                return new AtlasUnavailableException(statusCode, errorCode, errorMessage, method, url);
            default:
                return new AtlasServerException(statusCode, errorCode, errorMessage, method, url);
        }
    }

    /** 서버가 돌려준 HTTP 상태 코드를 돌려줍니다. */
    public int getStatusCode() {
        return statusCode;
    }

    /** {@code ATLAS-404-00-005} 같은 Atlas 오류 코드를 돌려줍니다. 응답 본문에 없었으면 {@code null}입니다. */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 오류 메시지를 돌려줍니다. 본문의 {@code errorMessage}(없으면 {@code error}) 값이며, 본문이 JSON이 아니면
     * 잘라낸 원문 본문일 수 있습니다. 응답에 {@code Location} 헤더가 있으면 끝에 {@code (Location: ...)}이
     * 덧붙습니다. 본문도 {@code Location} 헤더도 없으면 {@code null}입니다.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** 실패한 요청의 HTTP 메서드(예: {@code GET})를 돌려줍니다. */
    public String getMethod() {
        return method;
    }

    /** 실패한 요청의 전체 URL(서버 주소와 쿼리 문자열 포함)을 돌려줍니다. */
    public String getUrl() {
        return url;
    }

    /**
     * Atlas 오류 코드가 주어진 값과 같은지 확인합니다. 같은 HTTP 상태 코드 안에서 원인을 구분할 때 씁니다.
     *
     * @param code 비교할 오류 코드(예: {@code ATLAS-404-00-005})
     * @return 오류 코드가 같으면 {@code true}, {@code code}나 오류 코드가 {@code null}이면 {@code false}
     */
    public boolean hasErrorCode(String code) {
        return code != null && code.equals(errorCode);
    }
}
