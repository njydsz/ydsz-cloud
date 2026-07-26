package com.njydsz.userinfo.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * LDAP 认证配置属性。
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   auth:
 *     ldap:
 *       enabled: true
 *       host: 10.248.3.56
 *       port: 389
 *       domain: @njydsz
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.auth.ldap")
public class LdapProperties {

    /** 是否启用 LDAP 认证 */
    private boolean enabled = false;

    /** LDAP 服务器地址 */
    private String host = "127.0.0.1";

    /** LDAP 端口 */
    private int port = 389;

    /** 域后缀（如 @njydsz） */
    private String domain = "";

    /** Base DN */
    private String baseDn = "";

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 5000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 10000;
}
