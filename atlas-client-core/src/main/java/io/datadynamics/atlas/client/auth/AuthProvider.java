package io.datadynamics.atlas.client.auth;

import java.net.URI;
import java.util.Optional;

/**
 * 요청에 붙일 {@code Authorization} 헤더 값을 제공합니다.
 * <p>
 * 구현체는 스레드 안전해야 합니다. 헤더는 요청마다 새로 계산하므로, 수명이 짧은 인증 정보(토큰, Kerberos
 * 티켓)도 호출하는 쪽이 신경 쓰지 않게 갱신할 수 있습니다.
 */
@FunctionalInterface
public interface AuthProvider {

    /**
     * 곧 보낼 요청의 {@code Authorization} 헤더 값을 돌려줍니다.
     *
     * @param requestUri 곧 보낼 요청의 전체 URI(서버마다 다른 인증 정보가 필요할 때 씁니다)
     * @return {@code Authorization} 헤더 값, 헤더를 보내지 않으려면 빈 {@link Optional}
     */
    Optional<String> authorizationHeader(URI requestUri);

    /** 인증 헤더를 보내지 않는 공급자를 돌려줍니다. 인증이 꺼진 Atlas에 씁니다. */
    static AuthProvider none() {
        return uri -> Optional.empty();
    }

    /**
     * HTTP Basic 인증 공급자를 돌려줍니다.
     *
     * @param username 사용자 이름
     * @param password 비밀번호
     * @return {@link BasicAuth} 공급자
     * @throws NullPointerException 사용자 이름이나 비밀번호가 {@code null}일 때
     */
    static AuthProvider basic(String username, String password) {
        return new BasicAuth(username, password);
    }

    /**
     * 고정된 토큰으로 Bearer 인증을 하는 공급자를 돌려줍니다. 토큰을 갱신해야 하면
     * {@link BearerTokenAuth#BearerTokenAuth(java.util.function.Supplier)}를 직접 씁니다.
     *
     * @param token Bearer 토큰. {@code null}이거나 공백이면 헤더를 보내지 않습니다
     * @return {@link BearerTokenAuth} 공급자
     */
    static AuthProvider bearer(String token) {
        return new BearerTokenAuth(() -> token);
    }
}
