package com.njydsz.userinfo.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LDAP 认证配置属性。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   auth:
 *     ldap:
 *       enabled: true
 *       host: 10.248.3.56
 *       port: 389
 *       domain: @ydszsoft
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.auth.ldap")
@SuppressWarnings("checkstyle:MagicNumber")
public class LdapProperties {

  /** 默认 LDAP 端口（标准 LDAP）。 */
  private static final int DEFAULT_LDAP_PORT = 389;
  /** 默认连接超时（毫秒）。 */
  private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

  /** 是否启用 LDAP 认证 */
  private boolean enabled = false;

  /** LDAP 服务器地址 */
  private String host = "127.0.0.1";

  /** LDAP 端口 */
  private int port = DEFAULT_LDAP_PORT;

  /** 域后缀（如 @ydszsoft） */
  private String domain = "";

  /** Base DN */
  private String baseDn = "";

  /** 连接超时（毫秒） */
  private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;

  /** 读取超时（毫秒） */
  private int readTimeoutMs = 10000;
}
