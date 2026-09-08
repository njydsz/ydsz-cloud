package com.njydsz.userinfo.domain.enums;

/**
 * 社交账号绑定策略枚举（P0-2 社交登录补全）。
 *
 * <p>定义社交账号回调后，系统如何处理"社交账号尚未绑定到本地用户"的场景。
 * 不同平台/不同安全要求下需要不同的策略：
 *
 * <ul>
 *   <li>{@code AUTO_BIND} — 自动创建本地用户并绑定（适合公开注册场景）</li>
 *   <li>{@code MANUAL_BIND} — 要求用户手动确认绑定（适合企业内场景，默认）</li>
 *   <li>{@code CONFLICT_REJECT} — 如果该社交账号已绑定到其他用户，直接拒绝（最严格）</li>
 * </ul>
 *
 * <p><b>平台推荐策略：</b>
 *
 * <ul>
 *   <li>企业微信/钉钉/飞书 — {@code AUTO_BIND}（企业域内安全可信）</li>
 *   <li>微信/QQ — {@code MANUAL_BIND}（公开平台，需用户确认）</li>
 *   <li>Google/Microsoft — {@code MANUAL_BIND}（外部身份，需用户确认）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public enum SocialPlatformLinkingStrategy {

  /**
   * 自动绑定（无用户时自动创建本地账号并绑定）。
   *
   * <p>此策略在社交用户首次登录时，若本地无绑定记录且社交用户信息包含邮箱，
   * 则自动创建本地用户并创建绑定。适用于企业内部工具场景。
   */
  AUTO_BIND("auto_bind", "自动绑定"),

  /**
   * 手动绑定（仅返回社交信息，由前端引导用户登录后绑定）。
   *
   * <p>此策略在社交用户回调时，若本地无绑定记录，则仅返回社交用户信息（昵称/头像/邮箱），
   * 前端引导用户通过本地账号登录后再完成绑定。适用于安全要求较高的场景。
   */
  MANUAL_BIND("manual_bind", "手动绑定"),

  /**
   * 冲突拒绝（社交账号已绑定到其他用户时拒绝登录）。
   *
   * <p>此策略在社交用户已绑定时严格校验绑定归属，若发现 openId 已被其他用户绑定
   * 且当前登录用户与绑定用户不一致，则拒绝登录并返回冲突提示。
   */
  CONFLICT_REJECT("conflict_reject", "冲突拒绝");

  /** 策略标识 */
  private final String code;

  /** 显示名称 */
  private final String displayName;

  SocialPlatformLinkingStrategy(String code, String displayName) {
    this.code = code;
    this.displayName = displayName;
  }

  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }

  /**
   * 根据 code 值解析枚举。
   *
   * @param code 策略标识
   * @return 对应枚举；未找到返回 MANUAL_BIND（默认值）
   */
  public static SocialPlatformLinkingStrategy fromCode(String code) {
    if (code != null) {
      for (SocialPlatformLinkingStrategy strategy : values()) {
        if (strategy.code.equals(code)) {
          return strategy;
        }
      }
    }
    return MANUAL_BIND;
  }
}
