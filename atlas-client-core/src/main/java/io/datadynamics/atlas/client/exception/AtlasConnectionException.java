package io.datadynamics.atlas.client.exception;

/**
 * 네트워크 수준에서 요청을 끝내지 못했을 때 발생합니다.
 * <p>
 * 예를 들어 연결 거부, 타임아웃, HA 구성에서 ACTIVE 서버를 찾지 못한 경우 등입니다. 서버가 HTTP 응답을
 * 돌려준 경우에는 이 예외가 아니라 {@link AtlasServerException}이 발생합니다.
 */
public class AtlasConnectionException extends AtlasClientException {

    /**
     * 메시지만으로 예외를 만듭니다.
     *
     * @param message 예외 메시지
     */
    public AtlasConnectionException(String message) {
        super(message);
    }

    /**
     * 메시지와 원인 예외로 예외를 만듭니다.
     *
     * @param message 예외 메시지
     * @param cause   원인 예외(예: {@link java.io.IOException}, {@link InterruptedException})
     */
    public AtlasConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
