package com.remisoft.userinfo.server.auth;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.remisoft.common.auth.model.AuthenticationProvider;
import com.remisoft.common.util.auth.AuthInfo;
import com.remisoft.userinfo.server.config.LdapProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LDAP/ADFS 域账号认证提供者。
 *
 * <p>实现 common-auth AuthenticationProvider SPI，支持 LDAP/Active Directory 域认证。
 * 配置通过 {@link LdapProperties} 注入。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "remi.auth.ldap", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LdapAuthenticationProvider implements AuthenticationProvider {

    private final LdapProperties properties;

    @Override
    public AuthInfo authenticate(HttpServletRequest request, HttpServletResponse response) {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        if (username == null || password == null) {
            return null;
        }
        if (!authenticateLdap(username, password)) {
            return null;
        }
        log.info("LDAP authentication success for user: {}", username);
        // Token 签发由 AuthServiceImpl 统一处理，SPI 方法返回 null
        return null;
    }

    /**
     * LDAP 认证（JNDI 方式）。
     *
     * <p>供 AuthServiceImpl 直接调用，不依赖 HttpServletRequest。
     *
     * @param username 用户名
     * @param password 密码
     * @return true 认证成功
     */
    public boolean authenticateLdap(String username, String password) {
        String url = "ldap://" + properties.getHost() + ":" + properties.getPort();
        String domain = properties.getDomain();
        String user = username.indexOf(domain) > 0 ? username : username + domain;

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, user);
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(properties.getConnectTimeoutMs()));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(properties.getReadTimeoutMs()));

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

    /**
     * LDAP 是否启用。
     *
     * @return true 启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
