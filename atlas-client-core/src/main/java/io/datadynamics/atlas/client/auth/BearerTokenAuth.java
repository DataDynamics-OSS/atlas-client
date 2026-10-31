package io.datadynamics.atlas.client.auth;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Bearer 토큰 인증입니다. 예를 들어 Knox/JWT나 OAuth 프록시 뒤에 있는 Atlas에 씁니다.
 * <p>
 * 토큰 공급자({@link Supplier})를 요청마다 호출하므로, 공급자가 갱신된 토큰을 돌려주면 그대로 반영됩니다.
 * 여러 스레드에서 동시에 호출될 수 있으므로 공급자는 스레드 안전해야 합니다. {@link #toString()}은 토큰을
 * 드러내지 않습니다.
 */
public final class BearerTokenAuth implements AuthProvider {

    private final Supplier<String> tokenSupplier;

    /**
     * Bearer 인증 공급자를 만듭니다.
     *
     * @param tokenSupplier 요청마다 호출되어 토큰을 돌려주는 공급자
     * @throws NullPointerException {@code tokenSupplier}가 {@code null}일 때
     */
    public BearerTokenAuth(Supplier<String> tokenSupplier) {
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
    }

    /**
     * 공급자에서 토큰을 받아 {@code Bearer <토큰>} 헤더 값을 만듭니다. 토큰이 {@code null}이거나 공백이면
     * 헤더를 보내지 않도록 빈 {@link Optional}을 돌려줍니다.
     */
    @Override
    public Optional<String> authorizationHeader(URI requestUri) {
        String token = tokenSupplier.get();
        return token == null || token.isBlank() ? Optional.empty() : Optional.of("Bearer " + token);
    }

    /** 클래스 이름만 돌려줍니다. 토큰은 로그에 남지 않도록 포함하지 않습니다. */
    @Override
    public String toString() {
        return "BearerTokenAuth";
    }
}
