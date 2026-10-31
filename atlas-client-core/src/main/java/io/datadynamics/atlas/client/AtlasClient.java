package io.datadynamics.atlas.client;

import io.datadynamics.atlas.client.api.AdminApi;
import io.datadynamics.atlas.client.api.EntitiesApi;
import io.datadynamics.atlas.client.api.LineageApi;
import io.datadynamics.atlas.client.api.SearchApi;
import io.datadynamics.atlas.client.api.TypesApi;
import io.datadynamics.atlas.client.auth.AuthProvider;
import io.datadynamics.atlas.client.auth.BasicAuth;
import io.datadynamics.atlas.client.auth.BearerTokenAuth;
import io.datadynamics.atlas.client.auth.KerberosAuth;
import io.datadynamics.atlas.client.http.AtlasTransport;
import io.datadynamics.atlas.client.http.RequestListener;
import io.datadynamics.atlas.client.http.RetryPolicy;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Atlas 클라이언트의 진입점입니다.
 * <p>
 * 영역별 API 객체({@link #types()}, {@link #entities()}, {@link #lineage()}, {@link #search()},
 * {@link #admin()})를 제공하며, 모두 같은 전송 계층({@link AtlasTransport})을 공유합니다.
 * <pre>{@code
 * AtlasClient atlas = AtlasClient.builder()
 *         .serverUrl("http://atlas1:21000")
 *         .serverUrl("http://atlas2:21000")      // HA: ACTIVE 서버를 자동으로 찾습니다
 *         .basicAuth("admin", "admin")
 *         .build();
 *
 * atlas.types().createOrUpdate(typesDef);
 * atlas.entities().findByQualifiedName("hive_table", "db.sales@prod");
 * atlas.lineage().getByQualifiedName("hive_table", "db.sales@prod");
 * atlas.search().basicStream(SearchParameters.forType("hive_table")).forEach(...);
 * }</pre>
 * 인스턴스는 스레드 안전하므로 애플리케이션 안에서 하나를 만들어 공유해야 합니다. 요청마다 새로 만들면
 * HTTP 연결, 세션 쿠키, ACTIVE 서버 정보를 매번 새로 얻어야 합니다.
 */
public final class AtlasClient {

    private final AtlasTransport transport;
    private final TypesApi types;
    private final EntitiesApi entities;
    private final LineageApi lineage;
    private final SearchApi search;
    private final AdminApi admin;

    /**
     * 설정으로 클라이언트를 만듭니다. 보통은 {@link #builder()}를 쓰는 편이 간단합니다.
     *
     * @param config 클라이언트 설정
     */
    public AtlasClient(AtlasClientConfig config) {
        this(new AtlasTransport(config));
    }

    /**
     * 이미 만든 전송 계층으로 클라이언트를 만듭니다. 전송 계층을 여러 클라이언트가 공유하거나 테스트에서
     * 직접 주입할 때 씁니다.
     *
     * @param transport 요청을 보낼 전송 계층
     * @throws NullPointerException {@code transport}가 {@code null}일 때
     */
    public AtlasClient(AtlasTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.types = new TypesApi(transport);
        this.entities = new EntitiesApi(transport);
        this.lineage = new LineageApi(transport);
        this.search = new SearchApi(transport);
        this.admin = new AdminApi(transport);
    }

    /** 클라이언트를 설정하고 만드는 새 빌더를 돌려줍니다. */
    public static Builder builder() {
        return new Builder();
    }

    /** 타입 정의 API({@code /api/atlas/v2/types})를 돌려줍니다. */
    public TypesApi types() {
        return types;
    }

    /** 엔티티 API({@code /api/atlas/v2/entity})를 돌려줍니다. */
    public EntitiesApi entities() {
        return entities;
    }

    /** 리니지 API({@code /api/atlas/v2/lineage})를 돌려줍니다. */
    public LineageApi lineage() {
        return lineage;
    }

    /** 검색 API({@code /api/atlas/v2/search})를 돌려줍니다. */
    public SearchApi search() {
        return search;
    }

    /** 관리 API({@code /api/atlas/admin}: 버전, HA 상태, purge)를 돌려줍니다. */
    public AdminApi admin() {
        return admin;
    }

    /** API 클래스가 감싸지 않은 엔드포인트를 직접 호출할 때 쓰는 저수준 전송 계층을 돌려줍니다. */
    public AtlasTransport transport() {
        return transport;
    }

    /** 이 클라이언트가 쓰는 설정을 돌려줍니다. */
    public AtlasClientConfig config() {
        return transport.getConfig();
    }

    /**
     * {@link AtlasClient}와 {@link AtlasClientConfig}를 만드는 빌더입니다.
     * <p>
     * 서버 URL은 최소 하나 지정해야 합니다. 나머지 기본값은 인증 없음, 연결 타임아웃 10초, 요청 타임아웃 60초,
     * {@link RetryPolicy#defaults()}, JDK 기본 SSL, User-Agent {@code atlas-client-java}, 쿠키 사용,
     * 리스너 없음입니다. 스레드 안전하지 않으므로 한 스레드에서 설정을 마친 뒤 {@link #build()}를 호출합니다.
     */
    public static final class Builder {

        private final List<String> serverUrls = new ArrayList<>();
        private AuthProvider authProvider = AuthProvider.none();
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(60);
        private RetryPolicy retryPolicy = RetryPolicy.defaults();
        private SSLContext sslContext;
        private String userAgent = "atlas-client-java";
        private boolean cookiesEnabled = true;
        private RequestListener requestListener = RequestListener.none();

        private Builder() {
        }

        /**
         * Atlas 서버 URL을 하나 추가합니다(예: {@code http://atlas:21000}). HA 구성이면 여러 번 호출해 모든 서버를
         * 추가합니다.
         *
         * @param serverUrl 서버 URL
         * @return 이 빌더
         * @throws NullPointerException {@code serverUrl}이 {@code null}일 때
         */
        public Builder serverUrl(String serverUrl) {
            serverUrls.add(Objects.requireNonNull(serverUrl, "serverUrl"));
            return this;
        }

        /**
         * Atlas 서버 URL들을 추가합니다. 각 인자는 {@code atlas.rest.address} 설정처럼 쉼표로 구분한 목록이어도
         * 됩니다. 앞뒤 공백은 지우고 빈 항목은 건너뜁니다.
         *
         * @param urls 서버 URL 또는 쉼표로 구분한 URL 목록
         * @return 이 빌더
         */
        public Builder serverUrls(String... urls) {
            for (String url : urls) {
                Arrays.stream(url.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(serverUrls::add);
            }
            return this;
        }

        /**
         * 인증 방식을 지정합니다. 마지막에 지정한 방식 하나만 적용됩니다.
         *
         * @param authProvider 인증 공급자
         * @return 이 빌더
         * @throws NullPointerException {@code authProvider}가 {@code null}일 때
         */
        public Builder auth(AuthProvider authProvider) {
            this.authProvider = Objects.requireNonNull(authProvider, "authProvider");
            return this;
        }

        /**
         * HTTP Basic 인증을 씁니다. {@link BasicAuth}를 참고합니다.
         *
         * @param username 사용자 이름
         * @param password 비밀번호
         * @return 이 빌더
         */
        public Builder basicAuth(String username, String password) {
            return auth(new BasicAuth(username, password));
        }

        /**
         * Bearer 토큰 인증을 씁니다. 공급자는 요청마다 호출되므로 갱신된 토큰을 돌려줄 수 있습니다.
         *
         * @param tokenSupplier 토큰 공급자
         * @return 이 빌더
         */
        public Builder bearerToken(Supplier<String> tokenSupplier) {
            return auth(new BearerTokenAuth(tokenSupplier));
        }

        /**
         * keytab 파일로 로그인하는 Kerberos 인증을 씁니다. {@link KerberosAuth#keytab(String, String)}를 참고합니다.
         *
         * @param principal  로그인할 principal
         * @param keytabPath keytab 파일 경로
         * @return 이 빌더
         */
        public Builder kerberosKeytab(String principal, String keytabPath) {
            return auth(KerberosAuth.keytab(principal, keytabPath));
        }

        /**
         * {@code kinit}으로 받아 둔 티켓 캐시로 Kerberos 인증을 합니다. {@link KerberosAuth#ticketCache()}를 참고합니다.
         *
         * @return 이 빌더
         */
        public Builder kerberosTicketCache() {
            return auth(KerberosAuth.ticketCache());
        }

        /**
         * 서버와 연결을 맺을 때까지 기다리는 최대 시간을 정합니다(기본 10초).
         *
         * @param connectTimeout 연결 타임아웃
         * @return 이 빌더
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
            return this;
        }

        /**
         * 요청 하나 전체의 타임아웃을 정합니다(기본 60초). 큰 일괄(bulk) 호출을 한다면 늘려야 합니다.
         *
         * @param requestTimeout 요청 타임아웃
         * @return 이 빌더
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
            return this;
        }

        /**
         * 재시도 정책을 정합니다(기본 {@link RetryPolicy#defaults()}). 재시도를 끄려면 {@link RetryPolicy#none()}을 씁니다.
         *
         * @param retryPolicy 재시도 정책
         * @return 이 빌더
         */
        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
            return this;
        }

        /**
         * HTTPS 연결에 쓸 SSL 컨텍스트를 정합니다. 자체 서명 인증서 등을 신뢰해야 할 때 씁니다.
         *
         * @param sslContext SSL 컨텍스트, {@code null}이면 JDK 기본 설정을 씁니다
         * @return 이 빌더
         */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /**
         * 요청의 {@code User-Agent} 헤더 값을 정합니다(기본 {@code atlas-client-java}).
         *
         * @param userAgent User-Agent 값
         * @return 이 빌더
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
            return this;
        }

        /**
         * 요청 사이에 Atlas 세션 쿠키를 유지할지 정합니다(기본 {@code true}). 유지하면 요청마다 인증 과정을
         * 처음부터 거치지 않아도 됩니다.
         *
         * @param cookiesEnabled 쿠키를 유지하려면 {@code true}
         * @return 이 빌더
         */
        public Builder cookiesEnabled(boolean cookiesEnabled) {
            this.cookiesEnabled = cookiesEnabled;
            return this;
        }

        /**
         * 모든 HTTP 시도를 관찰할 리스너를 정합니다. 자세한 내용은 {@link RequestListener}를 참고합니다.
         *
         * @param requestListener 리스너
         * @return 이 빌더
         */
        public Builder requestListener(RequestListener requestListener) {
            this.requestListener = Objects.requireNonNull(requestListener, "requestListener");
            return this;
        }

        /**
         * 클라이언트 없이 설정만 만듭니다. 서버 URL은 정규화하고 중복을 제거합니다.
         *
         * @return 새 설정
         * @throws IllegalArgumentException 서버 URL이 없거나 올바르지 않을 때
         */
        public AtlasClientConfig buildConfig() {
            return new AtlasClientConfig(AtlasClientConfig.normalizeServerUrls(serverUrls), authProvider,
                    connectTimeout, requestTimeout, retryPolicy, sslContext, userAgent, cookiesEnabled, requestListener);
        }

        /**
         * 설정을 만들고 그 설정으로 클라이언트를 만듭니다.
         *
         * @return 새 클라이언트
         * @throws IllegalArgumentException 서버 URL이 없거나 올바르지 않을 때
         */
        public AtlasClient build() {
            return new AtlasClient(buildConfig());
        }
    }
}
