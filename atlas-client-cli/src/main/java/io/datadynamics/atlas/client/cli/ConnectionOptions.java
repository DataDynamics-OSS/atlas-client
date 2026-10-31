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
 * Atlas에 접속하는 방법(서버 URL, 인증 정보, 타임아웃)을 담는 picocli mixin입니다.
 * <p>
 * 각 설정은 명령줄 옵션을 먼저 보고, 없으면 환경 변수, 그래도 없으면 properties 파일에서 가져옵니다.
 * properties 파일은 {@code --config}, {@code $ATLAS_CLIENT_CONFIG}, {@code ~/.atlas/client.properties} 순서로
 * 처음 지정된 위치를 씁니다. 옵션·환경 변수·키의 대응은 다음과 같습니다.
 * <pre>
 * 옵션          환경 변수         properties 키
 * --url         ATLAS_URL         url        (HA 구성이면 쉼표로 구분)
 * --user        ATLAS_USER        user
 * --password    ATLAS_PASSWORD    password
 * --token       ATLAS_TOKEN       token
 * --principal   ATLAS_PRINCIPAL   principal  (Kerberos, keytab과 함께 사용)
 * --keytab      ATLAS_KEYTAB      keytab
 * </pre>
 * 인증 방식은 token, Kerberos principal, user 순서로 처음 설정된 것 하나만 씁니다.
 * 셋 다 없으면 인증 없이 접속합니다.
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

    /**
     * 설정을 모아 {@link AtlasClient}를 만듭니다.
     * <p>
     * User-Agent는 {@code atlas-client-cli}로 보냅니다. basic 인증에서 비밀번호가 없으면 빈 문자열을 씁니다.
     *
     * @param env 설정을 찾을 환경 변수
     * @return 새 클라이언트
     * @throws IllegalArgumentException URL이 어디에도 없거나, principal은 있는데 keytab이 없거나,
     *                                  {@code --config}로 지정한 파일이 없는 경우
     * @throws UncheckedIOException     properties 파일을 읽지 못한 경우
     */
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
        // 인증 방식은 하나만 고릅니다. token이 가장 우선이고, 다음이 Kerberos, 마지막이 basic 인증입니다.
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

    /**
     * 설정값 하나를 옵션, 환경 변수, properties 파일 순서로 찾습니다. 빈 문자열이나 공백뿐인 값은 없는 것으로 봅니다.
     * properties 파일에서 읽은 값만 앞뒤 공백을 잘라 냅니다.
     *
     * @return 찾은 값, 어디에도 없으면 {@code null}
     */
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

    /** 읽을 properties 파일 경로를 {@code --config}, {@code ATLAS_CLIENT_CONFIG}, 홈 디렉터리 기본값 순서로 정합니다. */
    private Path configPath(Map<String, String> env) {
        if (config != null) {
            return config;
        }
        String variable = env.get("ATLAS_CLIENT_CONFIG");
        return variable != null ? Path.of(variable)
                : Path.of(System.getProperty("user.home"), ".atlas", "client.properties");
    }

    /**
     * properties 파일을 UTF-8로 읽습니다.
     * <p>
     * 환경 변수나 기본 위치로 정해진 파일은 없어도 됩니다(빈 설정을 돌려줍니다). 하지만 {@code --config}로
     * 직접 지정한 파일이 없으면 사용자가 잘못 지정한 것이므로 오류로 처리합니다.
     */
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
