package io.datadynamics.atlas.client.auth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP Basic 인증입니다. Atlas의 파일 기반, LDAP, AD 인증에 씁니다.
 * <p>
 * 헤더 값은 생성할 때 한 번만 계산해 두고 모든 요청에 같은 값을 보냅니다. 불변 객체이므로 스레드 안전합니다.
 * {@link #toString()}은 비밀번호를 드러내지 않도록 사용자 이름만 보여 줍니다.
 */
public final class BasicAuth implements AuthProvider {

    private final String username;
    private final String header;

    /**
     * Basic 인증 공급자를 만듭니다. {@code 사용자이름:비밀번호}를 UTF-8로 인코딩한 뒤 Base64로 바꿔 헤더 값을 만듭니다.
     *
     * @param username 사용자 이름
     * @param password 비밀번호
     * @throws NullPointerException 사용자 이름이나 비밀번호가 {@code null}일 때
     */
    public BasicAuth(String username, String password) {
        this.username = Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        String token = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.header = "Basic " + token;
    }

    /** 미리 계산해 둔 {@code Basic ...} 헤더 값을 돌려줍니다. 요청 URI와 상관없이 항상 같습니다. */
    @Override
    public Optional<String> authorizationHeader(URI requestUri) {
        return Optional.of(header);
    }

    /** 사용자 이름만 담은 설명을 돌려줍니다. 비밀번호와 헤더 값은 로그에 남지 않도록 포함하지 않습니다. */
    @Override
    public String toString() {
        return "BasicAuth{username=" + username + "}";
    }
}
