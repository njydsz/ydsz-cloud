package com.njydsz.common.jdbc.constant;

/**
 * 审计字段常量定义
 *
 * <p>定义审计字段（created_by / updated_by）在非 Web 上下文中的 fallback 标识符。 使用英文标识符而非硬编码中文，保持与 i18n 策略一致。
 *
 * <h2>i18n 扩展方式</h2>
 *
 * <p>上层业务可通过 MessageSource 将以下 key 解析为对应语言的展示文本：
 *
 * <ul>
 *   <li>{@code audit.created_by.system} → 对应 {@link #CREATED_BY_SYSTEM}
 *   <li>{@code audit.updated_by.system} → 对应 {@link #UPDATED_BY_SYSTEM}
 * </ul>
 *
 * <p>数据库中存储的值为英文标识符（如 {@code "system"}），展示层根据当前 Locale 翻译。 这种设计避免了 Handler 层直接依赖
 * MessageSource，保持轻量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class AuditFieldConstants {

  private AuditFieldConstants() {}

  /**
   * 创建人字段在非 Web 上下文中的 fallback 标识符
   *
   * <p>当无法从请求上下文获取用户信息时（定时任务、MQ 消费等）， 使用该值作为 created_by 的填充值。 展示层应通过 message key {@code
   * audit.created_by.system} 进行国际化翻译。
   */
  public static final String CREATED_BY_SYSTEM = "system";

  /**
   * 更新人字段在非 Web 上下文中的 fallback 标识符
   *
   * <p>当无法从请求上下文获取用户信息时（定时任务、MQ 消费等）， 使用该值作为 updated_by 的填充值。 展示层应通过 message key {@code
   * audit.updated_by.system} 进行国际化翻译。
   */
  public static final String UPDATED_BY_SYSTEM = "system";

  /** i18n message key：创建人系统 fallback */
  public static final String CREATED_BY_SYSTEM_KEY = "audit.created_by.system";

  /** i18n message key：更新人系统 fallback */
  public static final String UPDATED_BY_SYSTEM_KEY = "audit.updated_by.system";
}
