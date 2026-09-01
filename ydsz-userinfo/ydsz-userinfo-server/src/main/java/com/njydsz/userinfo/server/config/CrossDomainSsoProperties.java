package com.njydsz.userinfo.server.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 跨域 SSO 配置属性。
 *
 * <p>集中管理微前端架构下主应用与子应用之间的跨域 Token 传递方案，支持 Cookie 共享与 postMessage 两种方式，
 * 实现子应用免登录。通过 {@link UserInfoConfiguration} 的 {@code @EnableConfigurationProperties} 注册。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.sso}
 *
 * <p><b>工作原理：</b>
 *
 * <ol>
 *   <li>主应用登录成功后，服务端在响应中设置 Domain 为父域名的 Cookie（如 {@code .example.com}）</li>
 *   <li>子应用（如 {@code app1.example.com}）跨域请求时，浏览器自动携带该 Cookie</li>
 *   <li>子应用也可通过 {@code postMessage} 从主应用获取 Token</li>
 *   <li>令牌交换端点支持用父域 Token 换取子域可用 Token</li>
 * </ol>
 *
 * <p><b>安全约束：</b>
 *
 * <ul>
 *   <li>跨域 Cookie 必须设置 {@code SameSite=None} 且 {@code Secure=true}</li>
 *   <li>CORS 响应头必须验证 Origin 白名单，不能直接返回 "*"</li>
 *   <li>令牌交换需要校验请求来源 Origin 白名单</li>
 * </ul>
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     sso:
 *       enabled: true
 *       cookie-name: ydsz_token
 *       cookie-domain: ".example.com"
 *       cookie-secure: true
 *       cookie-same-site: "None"
 *       cookie-max-age: 7200
 *       trusted-domains:
 *         - "https://app1.example.com"
 *         - "https://app2.example.com"
 *       post-message-target-origin: "*"
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.sso")
public class CrossDomainSsoProperties {

  /** 默认cookieMaxAge值（可被配置文件覆盖） */
  private static final int DEFAULT_COOKIE_MAX_AGE = -1;

  /** 是否启用跨域 SSO，默认 true。 */
  private boolean enabled = true;

  /** 跨域共享 Cookie 名称，默认 "ydsz_token"。 */
  private String cookieName = "ydsz_token";

  /**
   * 共享 Cookie 域名（如 ".example.com"）。
   *
   * <p>设置后，该 Cookie 对 {@code *.example.com} 下所有子域可见，实现跨子域单点登录。
   * 为空时不设置 Domain（仅当前域可见），降级为同域 Cookie。
   */
  private String cookieDomain;

  /** 跨域 Cookie 是否仅通过 HTTPS 传输，默认 true（SameSite=None 前置要求）。 */
  private boolean cookieSecure = true;

  /**
   * 跨域 Cookie 的 SameSite 属性，默认 "None"。
   *
   * <p>必须设为 "None" 才能在跨站请求中携带 Cookie。注意：SameSite=None 要求 Secure=true，
   * 否则浏览器会拒绝该 Cookie。
   */
  private String cookieSameSite = "None";

  /**
   * 跨域 Cookie 有效期（秒），默认 -1（会话 Cookie）。
   *
   * <p>设为正数时覆盖 access_token TTL，控制跨域 Cookie 的过期时间。
   * 建议与 access_token 有效期一致，避免子域 Cookie 早于 Token 失效。
   */
  private int cookieMaxAge = DEFAULT_COOKIE_MAX_AGE;

  /**
   * 允许跨域携带 Token 的来源域白名单。
   *
   * <p>CORS 预检和令牌交换端点仅放行此列表中的 Origin，防止未授权的第三方域窃取 Token。
   * 列表为空时拒绝所有跨域请求（安全兜底）。
   */
  private List<String> trustedDomains = new ArrayList<>();

  /**
   * postMessage 目标域名，默认 "*"。
   *
   * <p>前端通过 {@code window.postMessage()} 传递 Token 时，targetOrigin 参数使用此值。
   * 生产环境建议设为具体域名（如 "https://example.com"），避免 Token 泄露给未授权页面。
   */
  private String postMessageTargetOrigin = "*";
}
