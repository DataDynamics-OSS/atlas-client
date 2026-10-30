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
import io.datadynamics.atlas.client.http.RetryPolicy;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Entry point of the Atlas client.
 * <pre>{@code
 * AtlasClient atlas = AtlasClient.builder()
 *         .serverUrl("http://atlas1:21000")
 *         .serverUrl("http://atlas2:21000")      // HA: the active server is detected automatically
 *         .basicAuth("admin", "admin")
 *         .build();
 *
 * atlas.types().createOrUpdate(typesDef);
 * atlas.entities().findByQualifiedName("hive_table", "db.sales@prod");
 * atlas.lineage().getByQualifiedName("hive_table", "db.sales@prod");
 * atlas.search().basicStream(SearchParameters.forType("hive_table")).forEach(...);
 * }</pre>
 * Instances are thread-safe and should be shared.
 */
public final class AtlasClient {

    private final AtlasTransport transport;
    private final TypesApi types;
    private final EntitiesApi entities;
    private final LineageApi lineage;
    private final SearchApi search;
    private final AdminApi admin;

    public AtlasClient(AtlasClientConfig config) {
        this(new AtlasTransport(config));
    }

    public AtlasClient(AtlasTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.types = new TypesApi(transport);
        this.entities = new EntitiesApi(transport);
        this.lineage = new LineageApi(transport);
        this.search = new SearchApi(transport);
        this.admin = new AdminApi(transport);
    }

    public static Builder builder() {
        return new Builder();
    }

    public TypesApi types() {
        return types;
    }

    public EntitiesApi entities() {
        return entities;
    }

    public LineageApi lineage() {
        return lineage;
    }

    public SearchApi search() {
        return search;
    }

    public AdminApi admin() {
        return admin;
    }

    /** Low-level access for endpoints not wrapped by the API classes. */
    public AtlasTransport transport() {
        return transport;
    }

    public AtlasClientConfig config() {
        return transport.getConfig();
    }

    public static final class Builder {

        private final List<String> serverUrls = new ArrayList<>();
        private AuthProvider authProvider = AuthProvider.none();
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(60);
        private RetryPolicy retryPolicy = RetryPolicy.defaults();
        private SSLContext sslContext;
        private String userAgent = "atlas-client-java";
        private boolean cookiesEnabled = true;

        private Builder() {
        }

        /** Adds an Atlas server URL, e.g. {@code http://atlas:21000}. Add several for HA. */
        public Builder serverUrl(String serverUrl) {
            serverUrls.add(Objects.requireNonNull(serverUrl, "serverUrl"));
            return this;
        }

        /** Adds Atlas server URLs; accepts a comma separated list as used in {@code atlas.rest.address}. */
        public Builder serverUrls(String... urls) {
            for (String url : urls) {
                Arrays.stream(url.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(serverUrls::add);
            }
            return this;
        }

        public Builder auth(AuthProvider authProvider) {
            this.authProvider = Objects.requireNonNull(authProvider, "authProvider");
            return this;
        }

        public Builder basicAuth(String username, String password) {
            return auth(new BasicAuth(username, password));
        }

        public Builder bearerToken(Supplier<String> tokenSupplier) {
            return auth(new BearerTokenAuth(tokenSupplier));
        }

        public Builder kerberosKeytab(String principal, String keytabPath) {
            return auth(KerberosAuth.keytab(principal, keytabPath));
        }

        public Builder kerberosTicketCache() {
            return auth(KerberosAuth.ticketCache());
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
            return this;
        }

        /** Timeout of a whole request; raise it for large bulk calls. */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
            return this;
        }

        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
            return this;
        }

        /** Keeps the Atlas session cookie between requests (default {@code true}). */
        public Builder cookiesEnabled(boolean cookiesEnabled) {
            this.cookiesEnabled = cookiesEnabled;
            return this;
        }

        public AtlasClientConfig buildConfig() {
            return new AtlasClientConfig(AtlasClientConfig.normalizeServerUrls(serverUrls), authProvider,
                    connectTimeout, requestTimeout, retryPolicy, sslContext, userAgent, cookiesEnabled);
        }

        public AtlasClient build() {
            return new AtlasClient(buildConfig());
        }
    }
}
