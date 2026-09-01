package com.njydsz.message.domain.enums.template;

/**
 * 模板状态枚举。
 *
 * <p>对应 SQL {@code ydsz_msg_template.status} 的 CHECK 约束取值。
 *
 * <ul>
 *   <li>{@link #ENABLED} — 启用，可被业务调用
 *   <li>{@link #DISABLED} — 禁用，不可被业务调用
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum TemplateStatusEnum {

  /** 启用 */
  ENABLED,
  /** 禁用 */
  DISABLED;

  /**
   * 从字符串安全解析模板状态，无效时返回 DISABLED。
   *
   * @param value 状态字符串
   * @return 枚举值
   */
  public static TemplateStatusEnum fromString(String value) {
    if (value == null || value.isBlank()) {
      return DISABLED;
    }
    try {
      return TemplateStatusEnum.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return DISABLED;
    }
  }
}
