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
 * 변경할 수 없는(immutable) 클라이언트 설정입니다. {@link AtlasClient#builder()}로 만듭니다.
 * <p>
 * 생성자에서 필수 값을 검사하므로, 만들어진 설정은 항상 서버 URL이 하나 이상 있고 SSL 컨텍스트 외의 값은
 * {@code null}이 아닙니다. 여러 스레드에서 함께 써도 됩니다.
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

    // 빌더만 쓰도록 package-private으로 둡니다. 서버 URL 목록은 복사해서 불변으로 보관합니다.
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
     * 서버 URL을 정규화합니다. 앞뒤 공백과 끝의 슬래시를 지우고 http 또는 https인지 확인합니다.
     * 예를 들어 {@code http://host:21000/}은 {@code http://host:21000}이 됩니다. 끝의 슬래시를 지워야
     * 경로({@code /api/...})를 이어 붙일 때 슬래시가 겹치지 않습니다.
     *
     * @throws IllegalArgumentException http(s)가 아니거나 호스트가 없을 때
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

    // 여러 URL을 정규화하고, 정규화한 결과가 같은 URL은 한 번만 남깁니다(순서 유지).
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

    /** 정규화된 Atlas 서버 URL 목록을 돌려줍니다. 둘 이상이면 HA 구성으로 동작합니다. */
    public List<URI> getServerUrls() {
        return serverUrls;
    }

    /** 요청마다 인증 헤더를 만들어 줄 공급자를 돌려줍니다. */
    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    /** 서버와 TCP 연결을 맺을 때까지 기다리는 최대 시간을 돌려줍니다. */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** 요청 하나(시도 하나)가 응답을 받을 때까지 기다리는 최대 시간을 돌려줍니다. */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /** 실패한 요청을 재시도하는 정책을 돌려줍니다. */
    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    /** HTTPS 연결에 쓸 SSL 컨텍스트를 돌려줍니다. {@code null}이면 JDK 기본 설정을 씁니다. */
    public SSLContext getSslContext() {
        return sslContext;
    }

    /** 요청의 {@code User-Agent} 헤더 값을 돌려줍니다. */
    public String getUserAgent() {
        return userAgent;
    }

    /** Atlas가 발급한 세션 쿠키를 저장해 다음 요청에 다시 쓸지 돌려줍니다. 인증 왕복을 줄여 줍니다. */
    public boolean isCookiesEnabled() {
        return cookiesEnabled;
    }

    /** 모든 HTTP 시도를 보고받을 리스너를 돌려줍니다. 설정하지 않으면 아무것도 하지 않는 리스너입니다. */
    public RequestListener getRequestListener() {
        return requestListener;
    }
}
