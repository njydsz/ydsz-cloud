package com.njydsz.userinfo.server.auth;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.model.AuthenticationProvider;
import com.njydsz.common.util.auth.AuthInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * LDAP/ADFS domain account authentication provider.
 *
 * <p>Implements common-auth AuthenticationProvider SPI for LDAP-based
 * authentication (e.g., Active Directory / ADFS).
 *
 * <p>Configuration via application.yml:
 * <pre>
 * ydsz:
 *   auth:
 *     ldap:
 *       enabled: true
 *       host: 10.248.3.56
 *       port: 389
 *       domain: @wuxibio
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.auth.ldap", name = "enabled", havingValue = "true")
public class LdapAuthenticationProvider implements AuthenticationProvider {

    private String ldapHost = "127.0.0.1";
    private String ldapPort = "389";
    private String ldapDomain = "";

    /**
     * Authenticate via LDAP.
     *
     * <p>Extracts username and password from request, authenticates against
     * LDAP server, returns AuthInfo on success.
     */
    @Override
    public AuthInfo authenticate(HttpServletRequest request, HttpServletResponse response) {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        if (username == null || password == null) {
            return null;
        }
        boolean authenticated = authenticateLdap(username, password);
        if (!authenticated) {
            return null;
        }
        AuthInfo authInfo = new AuthInfo();
        authInfo.setUserId(username);
        authInfo.setUsername(username);
        return authInfo;
    }

    /**
     * LDAP authentication via JNDI.
     */
    private boolean authenticateLdap(String username, String password) {
        String url = "ldap://" + ldapHost + ":" + ldapPort;
        String user = username.indexOf(ldapDomain) > 0 ? username : username + ldapDomain;

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, user);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            log.info("LDAP authentication success: {}", username);
            return true;
        } catch (Exception e) {
            log.warn("LDAP authentication failed: {}, error: {}", username, e.getMessage());
            return false;
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception e) {
                    log.debug("Failed to close LDAP context", e);
                }
            }
        }
    }
}
