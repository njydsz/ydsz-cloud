package com.njydsz.userinfo.server.config;

import java.time.Duration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CAS 协议配置属性。
 *
 * <p>集中管理 CAS 服务端配置，包括服务端地址、票据有效期等。
 * 通过 {@code CasConfiguration} 的 {@code @EnableConfigurationProperties} 注册。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.cas}
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     cas:
 *       enabled: true
 *       server-name: https://userinfo.ydsz.com
 *       login-url: https://userinfo.ydsz.com/cas/login
 *       service-validate-url: https://userinfo.ydsz.com/cas/serviceValidate
 *       ticket-ttl:
 *         ticket-granting-ticket: 8h
 *         service-ticket: 5m
 * </pre>
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.cas")
@SuppressWarnings("checkstyle:MagicNumber")
public class CasProperties {

  /** CAS 协议全局开关（默认 false，需显式开启）。 */
  private boolean enabled = false;

  /** 当前应用的服务名（用于 CAS 回调地址校验）。 */
  private String serverName = "https://userinfo.ydsz.com";

  /** CAS 登录页面 URL。 */
  private String loginUrl = "https://userinfo.ydsz.com/cas/login";

  /** CAS 服务票据校验 URL。 */
  private String serviceValidateUrl = "https://userinfo.ydsz.com/cas/serviceValidate";

  /** Ticket Granting Ticket 有效期（默认 8 小时）。 */
  private Duration ticketGrantingTicketTtl = Duration.ofHours(8);

  /** Service Ticket 有效期（默认 5 分钟）。 */
  private Duration serviceTicketTtl = Duration.ofMinutes(5);

  /** 是否自动签发 PGT（Proxy Granting Ticket，用于代理认证）。 */
  private boolean enableProxy = false;
}
