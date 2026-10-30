package io.datadynamics.atlas.client.auth;

import java.net.URI;
import java.util.Optional;

/**
 * Supplies the {@code Authorization} header for a request.
 * <p>
 * Implementations must be thread-safe; the header is computed per request so that
 * short-lived credentials (tokens, Kerberos tickets) can be refreshed transparently.
 */
@FunctionalInterface
public interface AuthProvider {

    /**
     * @param requestUri the full URI of the request about to be sent
     * @return the value of the {@code Authorization} header, or empty to send none
     */
    Optional<String> authorizationHeader(URI requestUri);

    static AuthProvider none() {
        return uri -> Optional.empty();
    }

    static AuthProvider basic(String username, String password) {
        return new BasicAuth(username, password);
    }

    static AuthProvider bearer(String token) {
        return new BearerTokenAuth(() -> token);
    }
}
