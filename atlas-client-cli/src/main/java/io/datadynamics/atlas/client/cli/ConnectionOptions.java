package io.datadynamics.atlas.client.cli;

import io.datadynamics.atlas.client.AtlasClient;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

/**
 * How to reach Atlas. Each setting is taken from the option, else the environment variable, else
 * the properties file ({@code --config}, {@code $ATLAS_CLIENT_CONFIG} or {@code ~/.atlas/client.properties}):
 * <pre>
 * option        environment       properties key
 * --url         ATLAS_URL         url        (comma-separated for HA)
 * --user        ATLAS_USER        user
 * --password    ATLAS_PASSWORD    password
 * --token       ATLAS_TOKEN       token
 * --principal   ATLAS_PRINCIPAL   principal  (Kerberos, with keytab)
 * --keytab      ATLAS_KEYTAB      keytab
 * </pre>
 */
public final class ConnectionOptions {

    @Option(names = "--url", paramLabel = "URL", description = "Atlas URL; comma-separated for HA. Env: ATLAS_URL")
    String url;

    @Option(names = {"-u", "--user"}, description = "User for basic authentication. Env: ATLAS_USER")
    String user;

    @Option(names = "--password", description = "Password for basic authentication. Env: ATLAS_PASSWORD")
    String password;

    @Option(names = "--token", description = "Bearer token. Env: ATLAS_TOKEN")
    String token;

    @Option(names = "--principal", description = "Kerberos principal (with --keytab). Env: ATLAS_PRINCIPAL")
    String principal;

    @Option(names = "--keytab", description = "Kerberos keytab file. Env: ATLAS_KEYTAB")
    String keytab;

    @Option(names = "--config", paramLabel = "FILE",
            description = "Connection properties file (default: ~/.atlas/client.properties). Env: ATLAS_CLIENT_CONFIG")
    Path config;

    @Option(names = "--timeout", paramLabel = "SECONDS", defaultValue = "60",
            description = "Request timeout in seconds (default: ${DEFAULT-VALUE})")
    long timeoutSeconds;

    AtlasClient newClient(Map<String, String> env) {
        Properties file = properties(env);
        String url = setting(this.url, env, "ATLAS_URL", file, "url");
        if (url == null) {
            throw new IllegalArgumentException("No Atlas URL: use --url, ATLAS_URL or 'url' in " + configPath(env));
        }
        AtlasClient.Builder builder = AtlasClient.builder()
                .serverUrls(url)
                .requestTimeout(Duration.ofSeconds(timeoutSeconds))
                .userAgent("atlas-client-cli");

        String token = setting(this.token, env, "ATLAS_TOKEN", file, "token");
        String principal = setting(this.principal, env, "ATLAS_PRINCIPAL", file, "principal");
        String user = setting(this.user, env, "ATLAS_USER", file, "user");
        if (token != null) {
            builder.bearerToken(() -> token);
        } else if (principal != null) {
            String keytab = setting(this.keytab, env, "ATLAS_KEYTAB", file, "keytab");
            if (keytab == null) {
                throw new IllegalArgumentException("--principal needs --keytab");
            }
            builder.kerberosKeytab(principal, keytab);
        } else if (user != null) {
            String password = setting(this.password, env, "ATLAS_PASSWORD", file, "password");
            builder.basicAuth(user, password == null ? "" : password);
        }
        return builder.build();
    }

    private static String setting(String option, Map<String, String> env, String variable, Properties file, String key) {
        if (option != null && !option.isBlank()) {
            return option;
        }
        String value = env.get(variable);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = file.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Path configPath(Map<String, String> env) {
        if (config != null) {
            return config;
        }
        String variable = env.get("ATLAS_CLIENT_CONFIG");
        return variable != null ? Path.of(variable)
                : Path.of(System.getProperty("user.home"), ".atlas", "client.properties");
    }

    private Properties properties(Map<String, String> env) {
        Properties properties = new Properties();
        Path path = configPath(env);
        if (!Files.isRegularFile(path)) {
            if (config != null) {
                throw new IllegalArgumentException("Config file not found: " + config);
            }
            return properties;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
        return properties;
    }
}
