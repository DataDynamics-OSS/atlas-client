package io.datadynamics.atlas.client.auth;

import io.datadynamics.atlas.client.exception.AtlasClientException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.net.URI;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Kerberos/SPNEGO 인증입니다. JDK의 GSS-API만으로 구현했기 때문에 Hadoop 의존성이 필요 없습니다.
 * <p>
 * 서비스(데몬)에서는 {@link #keytab(String, String)}를, {@code kinit}으로 이미 받은 티켓을 다시 쓰려면
 * {@link #ticketCache()}를 씁니다. 서비스 principal은 호출하는 Atlas 서버의 {@code HTTP/<host>}입니다.
 * {@code krb5.conf}는 JDK가 평소처럼 찾습니다({@code -Djava.security.krb5.conf=...}).
 * <p>
 * Kerberos 로그인은 처음 요청할 때 한 번 하고 그 결과({@link Subject})를 재사용합니다. 토큰 생성에 실패하면
 * TGT가 만료되었을 수 있으므로 한 번 다시 로그인해 봅니다. 스레드 안전합니다.
 */
public final class KerberosAuth implements AuthProvider {

    // SPNEGO 메커니즘의 OID입니다. HTTP Negotiate 인증은 Kerberos 토큰을 SPNEGO로 감싸서 보냅니다.
    private static final String SPNEGO_OID = "1.3.6.1.5.5.2";
    // JDK에 포함된 Kerberos JAAS 로그인 모듈입니다.
    private static final String KRB5_LOGIN_MODULE = "com.sun.security.auth.module.Krb5LoginModule";

    private final Map<String, String> loginOptions;
    private final String description;
    // 로그인한 결과입니다. null이면 다음 토큰 생성 때 다시 로그인합니다.
    private volatile Subject subject;

    private KerberosAuth(Map<String, String> loginOptions, String description) {
        this.loginOptions = Map.copyOf(loginOptions);
        this.description = description;
    }

    /**
     * keytab 파일로 로그인하는 Kerberos 인증을 만듭니다. 사람이 개입하지 않는 서비스에 알맞습니다.
     * {@code refreshKrb5Config} 옵션을 켜서 로그인할 때마다 {@code krb5.conf}를 다시 읽습니다.
     *
     * @param principal  로그인할 principal(예: {@code atlas-app@EXAMPLE.COM})
     * @param keytabPath keytab 파일 경로
     * @return 새 인증 공급자(실제 로그인은 첫 요청 때 합니다)
     * @throws NullPointerException 인자가 {@code null}일 때
     */
    public static KerberosAuth keytab(String principal, String keytabPath) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(keytabPath, "keytabPath");
        Map<String, String> options = new HashMap<>();
        options.put("principal", principal);
        options.put("keyTab", keytabPath);
        options.put("useKeyTab", "true");
        options.put("storeKey", "true");
        options.put("doNotPrompt", "true");
        options.put("refreshKrb5Config", "true");
        options.put("isInitiator", "true");
        return new KerberosAuth(options, "KerberosAuth{principal=" + principal + ", keytab=" + keytabPath + "}");
    }

    /**
     * {@code kinit}으로 받아 둔 티켓 캐시를 쓰는 Kerberos 인증을 만듭니다. {@code renewTGT} 옵션으로 갱신 가능한
     * TGT는 갱신을 시도합니다.
     *
     * @return 새 인증 공급자(실제 로그인은 첫 요청 때 합니다)
     */
    public static KerberosAuth ticketCache() {
        Map<String, String> options = new HashMap<>();
        options.put("useTicketCache", "true");
        options.put("renewTGT", "true");
        options.put("doNotPrompt", "true");
        options.put("isInitiator", "true");
        return new KerberosAuth(options, "KerberosAuth{ticketCache}");
    }

    /**
     * 요청 대상 서버({@code HTTP@<host>})에 대한 SPNEGO 토큰을 만들어 {@code Negotiate <토큰>} 헤더 값으로
     * 돌려줍니다. 처음 실패하면 다시 로그인한 뒤 한 번 더 시도합니다.
     *
     * @throws AtlasClientException 로그인이나 토큰 생성에 실패했을 때
     */
    @Override
    public Optional<String> authorizationHeader(URI requestUri) {
        try {
            return Optional.of("Negotiate " + createToken(requestUri.getHost()));
        } catch (GSSException e) {
            // TGT가 만료되었을 수 있습니다. 로그인 결과를 버리고 한 번만 다시 로그인해 재시도합니다.
            subject = null;
            try {
                return Optional.of("Negotiate " + createToken(requestUri.getHost()));
            } catch (GSSException retryFailure) {
                throw new AtlasClientException("Failed to create SPNEGO token for " + requestUri.getHost(), retryFailure);
            }
        }
    }

    // 로그인한 Subject의 권한으로 GSS 컨텍스트를 만들어 첫 SPNEGO 토큰을 생성합니다. 상호 인증은 요청하고,
    // 자격 증명 위임(delegation)은 하지 않습니다. 토큰 하나만 만들면 되므로 컨텍스트는 바로 해제합니다.
    private String createToken(String host) throws GSSException {
        Subject current = loggedInSubject();
        try {
            return Subject.doAs(current, (PrivilegedExceptionAction<String>) () -> {
                GSSManager manager = GSSManager.getInstance();
                Oid spnego = new Oid(SPNEGO_OID);
                GSSName serverName = manager.createName("HTTP@" + host, GSSName.NT_HOSTBASED_SERVICE);
                GSSContext context = manager.createContext(
                        serverName.canonicalize(spnego), spnego, null, GSSContext.DEFAULT_LIFETIME);
                try {
                    context.requestMutualAuth(true);
                    context.requestCredDeleg(false);
                    byte[] token = context.initSecContext(new byte[0], 0, 0);
                    return Base64.getEncoder().encodeToString(token);
                } finally {
                    context.dispose();
                }
            });
        } catch (PrivilegedActionException e) {
            if (e.getException() instanceof GSSException) {
                throw (GSSException) e.getException();
            }
            throw new AtlasClientException("Failed to create SPNEGO token for " + host, e.getException());
        }
    }

    // 로그인한 Subject를 돌려줍니다. 이중 확인 잠금으로 여러 스레드가 동시에 로그인하지 않게 합니다.
    private Subject loggedInSubject() {
        Subject s = subject;
        if (s != null) {
            return s;
        }
        synchronized (this) {
            if (subject == null) {
                subject = login();
            }
            return subject;
        }
    }

    // 별도의 JAAS 설정 파일 없이도 동작하도록, 생성 시 받은 옵션으로 JAAS Configuration을 코드에서 만들어 로그인합니다.
    private Subject login() {
        Configuration configuration = new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return new AppConfigurationEntry[]{
                        new AppConfigurationEntry(KRB5_LOGIN_MODULE,
                                AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, loginOptions)
                };
            }
        };
        try {
            LoginContext loginContext = new LoginContext("atlas-client", new Subject(), null, configuration);
            loginContext.login();
            return loginContext.getSubject();
        } catch (LoginException e) {
            throw new AtlasClientException("Kerberos login failed: " + description, e);
        }
    }

    /** principal과 keytab 경로 또는 티켓 캐시 사용 여부를 담은 설명을 돌려줍니다. */
    @Override
    public String toString() {
        return description;
    }
}
