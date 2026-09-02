package com.njydsz.userinfo.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Remember-Me 滑动过期配置属性。
 *
 * <p>集中管理「记住我」功能的 Cookie 属性与滑动续期策略，通过 {@link
 * UserInfoConfiguration} 的 {@code @EnableConfigurationProperties} 注册。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.remember-me}
 *
 * <p><b>工作原理：</b>
 *
 * <ol>
 *   <li>用户登录时勾选「记住我」，服务端签发 Remember-Me Cookie（存储加密后的用户 ID）</li>
 *   <li>每次请求时 {@link com.njydsz.userinfo.web.filter.RememberMeFilter} 检查是否需要滑动续期</li>
 *   <li>滑动续期条件：距上次续期超过 {@link #slidingWindowSeconds} 且未超过 {@link #maxExtendDays}</li>
 *   <li>关闭浏览器后 Cookie 按 {@link #cookieMaxAge} 失效（默认 180 天）</li>
 * </ol>
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     remember-me:
 *       enabled: true
 *       cookie-name: ydsz_remember
 *       cookie-max-age: 15552000
 *       cookie-secure: true
 *       cookie-http-only: true
 *       cookie-same-site: "Lax"
 *       sliding-window-seconds: 86400
 *       max-extend-days: 180
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.remember-me")
public class RememberMeProperties {

  /** 默认cookieMaxAge值（可被配置文件覆盖） */
  private static final int DEFAULT_COOKIE_MAX_AGE = 15552000;

  /** 默认slidingWindowSeconds值（可被配置文件覆盖） */
  private static final long DEFAULT_SLIDING_WINDOW_SECONDS = 86400;

  /** 默认maxExtendDays值（可被配置文件覆盖） */
  private static final int DEFAULT_MAX_EXTEND_DAYS = 180;

  /** 是否启用 Remember-Me 功能，默认 true。 */
  private boolean enabled = true;

  /** Remember-Me Cookie 名称，默认 "ydsz_remember"。 */
  private String cookieName = "ydsz_remember";

  /**
   * Remember-Me Cookie 有效期（秒），默认 15552000（180 天）。
   *
   * <p>关闭浏览器后 Cookie 在 maxAge 秒后失效。设为 -1 表示会话 Cookie（浏览器关闭即失效），
   * 此时 Remember-Me 功能等价于不开启。
   */
  private int cookieMaxAge = DEFAULT_COOKIE_MAX_AGE;

  /** Cookie 是否仅通过 HTTPS 传输，默认 true。 */
  private boolean cookieSecure = true;

  /** Cookie 是否禁止 JavaScript 访问，默认 true。 */
  private boolean cookieHttpOnly = true;

  /** Cookie 的 SameSite 属性，默认 "Lax"。 */
  private String cookieSameSite = "Lax";

  /**
   * 滑动续期窗口（秒），默认 86400（24 小时）。
   *
   * <p>用户每次访问时，如果距上次续期超过此窗口，则自动延长 Token TTL。
   * 较小的值提高安全性（更频繁地检查），较大的值减少 Redis 写入。
   */
  private long slidingWindowSeconds = DEFAULT_SLIDING_WINDOW_SECONDS;

  /**
   * 最大续期天数，默认 180 天。
   *
   * <p>从首次登录开始计算，超过此天数后不再执行滑动续期，用户需要重新登录。
   * 防止 Remember-Me 无限期延长导致的安全风险。
   */
  private int maxExtendDays = DEFAULT_MAX_EXTEND_DAYS;
}
