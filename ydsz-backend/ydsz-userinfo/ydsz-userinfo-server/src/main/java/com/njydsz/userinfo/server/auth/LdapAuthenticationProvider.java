package com.njydsz.userinfo.server.auth;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.model.AuthenticationProvider;
import com.njydsz.common.util.auth.AuthInfo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * LDAP/ADFS 域账号认证提供者。
 *
 * <p>实现 common-auth AuthenticationProvider SPI，支持 LDAP/Active Directory 域认证。
 * 配置通过 @ConfigurationProperties 注入，替代原硬编码值。
 *
 * <p>配置示例：
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
@ConfigurationProperties(prefix = "ydsz.auth.ldap")
@Data
public class LdapAuthenticationProvider implements AuthenticationProvider {

    private boolean enabled = false;
    private String host = "127.0.0.1";
    private int port = 389;
    private String domain = "";
    private String baseDn = "";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;

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
        log.info("LDAP authentication success for user: {}, returning null AuthInfo (token issuance handled by AuthService)", username);
        return null;
    }

    /**
     * LDAP 认证（JNDI 方式）。
     */
    private boolean authenticateLdap(String username, String password) {
        String url = "ldap://" + host + ":" + port;
        String user = username.indexOf(domain) > 0 ? username : username + domain;

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, user);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(connectTimeoutMs));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(readTimeoutMs));

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
