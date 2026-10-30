package io.datadynamics.atlas.client.auth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * HTTP Basic authentication (Atlas file-based, LDAP or AD authentication).
 */
public final class BasicAuth implements AuthProvider {

    private final String username;
    private final String header;

    public BasicAuth(String username, String password) {
        this.username = Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        String token = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.header = "Basic " + token;
    }

    @Override
    public Optional<String> authorizationHeader(URI requestUri) {
        return Optional.of(header);
    }

    @Override
    public String toString() {
        return "BasicAuth{username=" + username + "}";
    }
}
