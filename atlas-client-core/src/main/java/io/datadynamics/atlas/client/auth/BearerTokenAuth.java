package io.datadynamics.atlas.client.auth;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Bearer token authentication (for example Atlas behind Knox/JWT or an OAuth proxy).
 * The supplier is called for every request so it can return a refreshed token.
 */
public final class BearerTokenAuth implements AuthProvider {

    private final Supplier<String> tokenSupplier;

    public BearerTokenAuth(Supplier<String> tokenSupplier) {
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
    }

    @Override
    public Optional<String> authorizationHeader(URI requestUri) {
        String token = tokenSupplier.get();
        return token == null || token.isBlank() ? Optional.empty() : Optional.of("Bearer " + token);
    }

    @Override
    public String toString() {
        return "BearerTokenAuth";
    }
}
