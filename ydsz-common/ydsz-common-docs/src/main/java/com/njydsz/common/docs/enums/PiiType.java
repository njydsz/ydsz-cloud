package com.njydsz.common.docs.enums;

/**
 * PII（个人身份信息）类型枚举
 *
 * <p>定义系统中可识别的敏感信息类型。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum PiiType {

  /** 身份证号 */
  ID_CARD("身份证号"),
  /** 手机号 */
  PHONE("手机号"),
  /** 银行卡号 */
  BANK_CARD("银行卡号"),
  /** 邮箱地址 */
  EMAIL("邮箱地址"),
  /** API 密钥/Token */
  API_KEY("API密钥"),
  /** IP 地址 */
  IP_ADDRESS("IP地址"),
  /** 护照号码 */
  PASSPORT("护照号码");

  /** 类型描述 */
  private final String description;

  PiiType(String description) {
    this.description = description;
  }

  /**
   * 获取该 PII 类型的中文描述。
   *
   * <p>用于安全扫描报告、审计日志与前端提示的展示文案， 使非技术人员也能理解命中的敏感信息种类。 描述文案仅用于展示，业务判定请一律使用枚举常量本身，不要基于该字符串做比较。
   *
   * @return 中文类型名，如"身份证号"、"银行卡号"；恒不为 {@code null}
   */
  public String getDescription() {
    return description;
  }
}
