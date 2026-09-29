package com.njydsz.literule.domain.enums;

/**
 * 规则严重度枚举
 *
 * <p>三层级：INFO（提示）/ YELLOW（黄色预警）/ RED（红色严重）。 与 execution 模块 AlertSeverity 语义对齐，支持getCode/fromCode
 * 互转。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum RuleSeverity {
  /** 提示级别 */
  INFO("INFO", 1, "rule.severity.INFO"),

  /** 黄色预警 */
  YELLOW("YELLOW", 2, "rule.severity.YELLOW"),

  /** 红色严重 */
  RED("RED", 3, "rule.severity.RED");

  private final String code;
  private final int weight;

  /** i18n key — 替代硬编码中文描述 */
  private final String i18nKey;

  RuleSeverity(String code, int weight, String i18nKey) {
    this.code = code;
    this.weight = weight;
    this.i18nKey = i18nKey;
  }

  public String getCode() {
    return code;
  }

  public int getWeight() {
    return weight;
  }

  /**
   * 获取 i18n key（替代硬编码中文 desc）。
   *
   * @return i18n key 字符串，如 "rule.severity.INFO"
   */
  public String getI18nKey() {
    return i18nKey;
  }

  /**
   * 根据编码反查枚举（大小写不敏感）
   *
   * @param code 严重度编码
   * @return 枚举值；未匹配返回 null
   */
  public static RuleSeverity fromCode(String code) {
    if (code == null) {
      return null;
    }
    for (RuleSeverity v : values()) {
      if (v.code.equalsIgnoreCase(code)) {
        return v;
      }
    }
    return null;
  }
}
