package com.njydsz.common.audit.config;

/**
 * 审计日志分表策略枚举
 *
 * <p>强类型枚举取代字符串配置，防止拼写错误导致启动失败。Spring Boot 枚举绑定对大小写不敏感，
 * 接受 {@code monthly}、{@code MONTHLY}、{@code Monthly} 等任意大小写形式。
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * ydsz:
 *   audit:
 *     sharding-type: monthly   # 可选 monthly / daily / yearly
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public enum AuditShardingType {

  /** 按月分表：表名后缀 _yyyyMM（如 sys_audit_log_202609） */
  MONTHLY("monthly"),

  /** 按天分表：表名后缀 _yyyyMMdd（如 sys_audit_log_20260919） */
  DAILY("daily"),

  /** 按年分表：表名后缀 _yyyy（如 sys_audit_log_2026） */
  YEARLY("yearly");

  /** 分表类型字符串值（对应 YAML 配置和数据库物理表名后缀） */
  private final String code;

  AuditShardingType(String code) {
    this.code = code;
  }

  /**
   * 获取分表类型字符串值
   *
   * @return 字符串值（如 "monthly"、"daily"、"yearly"）
   */
  public String getCode() {
    return code;
  }

  @Override
  public String toString() {
    return code;
  }
}
