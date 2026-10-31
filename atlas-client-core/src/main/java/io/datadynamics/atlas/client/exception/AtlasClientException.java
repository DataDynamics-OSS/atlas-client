package io.datadynamics.atlas.client.exception;

/**
 * Atlas 클라이언트가 던지는 모든 예외의 최상위 클래스입니다.
 * <p>
 * 일부러 unchecked 예외({@link RuntimeException})로 만들었습니다. Atlas 호출이 실패하는 원인은 대부분
 * 호출한 쪽에서 그 자리에서 복구할 수 없는 것들입니다. 그런데도 모든 호출을 {@code try/catch}로 감싸도록
 * 강제하는 것은 공식 클라이언트를 쓰기 불편하게 만드는 원인 중 하나이기 때문입니다.
 * <p>
 * 하위 클래스는 크게 두 갈래입니다. 서버가 오류 상태 코드로 응답한 경우는 {@link AtlasServerException},
 * 네트워크 수준에서 요청을 끝내지 못한 경우는 {@link AtlasConnectionException}입니다.
 */
public class AtlasClientException extends RuntimeException {

    /**
     * 메시지만으로 예외를 만듭니다.
     *
     * @param message 예외 메시지
     */
    public AtlasClientException(String message) {
        super(message);
    }

    /**
     * 메시지와 원인 예외로 예외를 만듭니다.
     *
     * @param message 예외 메시지
     * @param cause   이 예외를 일으킨 원래 예외
     */
    public AtlasClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
