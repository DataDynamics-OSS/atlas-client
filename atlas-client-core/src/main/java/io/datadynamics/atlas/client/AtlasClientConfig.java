package io.datadynamics.atlas.client;

import io.datadynamics.atlas.client.auth.AuthProvider;
import io.datadynamics.atlas.client.http.RequestListener;
import io.datadynamics.atlas.client.http.RetryPolicy;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable client configuration. Create one through {@link AtlasClient#builder()}.
 */
public final class AtlasClientConfig {

    private final List<URI> serverUrls;
    private final AuthProvider authProvider;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final RetryPolicy retryPolicy;
    private final SSLContext sslContext;
    private final String userAgent;
    private final boolean cookiesEnabled;
    private final RequestListener requestListener;

    AtlasClientConfig(List<URI> serverUrls, AuthProvider authProvider, Duration connectTimeout,
                      Duration requestTimeout, RetryPolicy retryPolicy, SSLContext sslContext,
                      String userAgent, boolean cookiesEnabled, RequestListener requestListener) {
        if (serverUrls.isEmpty()) {
            throw new IllegalArgumentException("At least one Atlas server URL is required");
        }
        this.serverUrls = List.copyOf(serverUrls);
        this.authProvider = Objects.requireNonNull(authProvider, "authProvider");
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.sslContext = sslContext;
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
        this.cookiesEnabled = cookiesEnabled;
        this.requestListener = Objects.requireNonNull(requestListener, "requestListener");
    }

    /**
     * Normalizes a server URL: requires http(s) and strips trailing slashes, so
     * {@code http://host:21000/} becomes {@code http://host:21000}.
     */
    static URI normalizeServerUrl(String url) {
        Objects.requireNonNull(url, "url");
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        URI uri = URI.create(trimmed);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Atlas server URL must start with http:// or https://: " + url);
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Atlas server URL has no host: " + url);
        }
        return uri;
    }

    static List<URI> normalizeServerUrls(List<String> urls) {
        List<URI> result = new ArrayList<>();
        for (String url : urls) {
            URI uri = normalizeServerUrl(url);
            if (!result.contains(uri)) {
                result.add(uri);
            }
        }
        return result;
    }

    public List<URI> getServerUrls() {
        return serverUrls;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public boolean isCookiesEnabled() {
        return cookiesEnabled;
    }

    public RequestListener getRequestListener() {
        return requestListener;
    }
}
