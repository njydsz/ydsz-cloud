package com.njydsz.userinfo.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * 登录安全配置属性（从 {@link UserInfoProperties} 拆分的子配置）。
 *
 * <p>聚焦登录失败锁定、图形验证码、密码策略等安全参数。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.security}
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     security:
 *       max-login-fail-count: 5
 *       lock-duration-minutes: 30
 *       captcha-enabled: true
 *       captcha-ttl-seconds: 300
 *       password-min-length: 8
 *       password-max-length: 64
 *       password-min-category-count: 3
 *       bcrypt-strength: 10
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.security")
public class UserSecurityProperties {

  /** 最大登录失败次数，默认 5 次。 */
  private int maxLoginFailCount = 5;

  /** 账号锁定时长（分钟），默认 30 分钟。 */
  private int lockDurationMinutes = 30;

  /** 登录时是否强制图形验证码，默认 true。 */
  private boolean captchaEnabled = true;

  /** 图形验证码有效期（秒），默认 5 分钟。 */
  private long captchaTtlSeconds = 300;

  /** 密码最小长度，默认 8。 */
  private int passwordMinLength = 8;

  /** 密码最大长度，默认 64。 */
  private int passwordMaxLength = 64;

  /** 密码最少字符种类数（大写/小写/数字/特殊字符），默认 3。 */
  private int passwordMinCategoryCount = 3;

  /** BCrypt 加密强度（4-31），默认 10。 */
  private int bcryptStrength = 10;

  /** 密码历史记录保留条数，默认 5。 */
  private int passwordHistoryCount = 5;
}
