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
 * Kerberos/SPNEGO authentication implemented on top of the JDK GSS-API, so no Hadoop
 * dependency is required.
 * <p>
 * Use {@link #keytab(String, String)} for services, or {@link #ticketCache()} to reuse the
 * ticket obtained by {@code kinit}. The service principal is {@code HTTP/<host>} of the
 * Atlas server being called. {@code krb5.conf} is resolved by the JDK as usual
 * ({@code -Djava.security.krb5.conf=...}).
 */
public final class KerberosAuth implements AuthProvider {

    private static final String SPNEGO_OID = "1.3.6.1.5.5.2";
    private static final String KRB5_LOGIN_MODULE = "com.sun.security.auth.module.Krb5LoginModule";

    private final Map<String, String> loginOptions;
    private final String description;
    private volatile Subject subject;

    private KerberosAuth(Map<String, String> loginOptions, String description) {
        this.loginOptions = Map.copyOf(loginOptions);
        this.description = description;
    }

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

    public static KerberosAuth ticketCache() {
        Map<String, String> options = new HashMap<>();
        options.put("useTicketCache", "true");
        options.put("renewTGT", "true");
        options.put("doNotPrompt", "true");
        options.put("isInitiator", "true");
        return new KerberosAuth(options, "KerberosAuth{ticketCache}");
    }

    @Override
    public Optional<String> authorizationHeader(URI requestUri) {
        try {
            return Optional.of("Negotiate " + createToken(requestUri.getHost()));
        } catch (GSSException e) {
            // The TGT may have expired: log in again once and retry.
            subject = null;
            try {
                return Optional.of("Negotiate " + createToken(requestUri.getHost()));
            } catch (GSSException retryFailure) {
                throw new AtlasClientException("Failed to create SPNEGO token for " + requestUri.getHost(), retryFailure);
            }
        }
    }

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

    @Override
    public String toString() {
        return description;
    }
}
