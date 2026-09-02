package com.njydsz.userinfo.domain.alert;

import java.time.LocalDateTime;

/**
 * 安全告警聚合根。
 *
 * <p>表示一次安全告警事件，包含告警类型、风险等级、告警内容和处理状态。
 * 告警由 {@link SecurityAlertService} 根据风险评估结果创建，并持久化到数据库。
 *
 * @param id 告警 ID
 * @param alertType 告警类型
 * @param riskLevel 风险等级
 * @param userId 关联用户 ID
 * @param username 关联用户名
 * @param sourceIp 来源 IP
 * @param title 告警标题
 * @param content 告警内容
 * @param status 告警状态
 * @param createdAt 创建时间
 * @param handledAt 处理时间
 * @param handlerNote 处理备注
 * @author ydsz-team
 * @since 26.09.01
 */
public record SecurityAlert(
    String id,
    AlertType alertType,
    RiskLevel riskLevel,
    String userId,
    String username,
    String sourceIp,
    String title,
    String content,
    AlertStatus status,
    LocalDateTime createdAt,
    LocalDateTime handledAt,
    String handlerNote) {

  /**
   * 告警类型枚举。
   */
  public enum AlertType {
    /** 账号锁定告警 */
    ACCOUNT_LOCKED,
    /** 账号封禁告警 */
    ACCOUNT_BANNED,
    /** MFA 验证失败告警 */
    MFA_FAILED,
    /** 暴力破解告警（同一 IP 多次失败） */
    BRUTE_FORCE,
    /** 异常登录告警（新设备 + 异常时段） */
    ANOMALOUS_LOGIN,
    /** 密码喷洒告警（多用户同一 IP 失败） */
    PASSWORD_SPRAY
  }

  /**
   * 告警风险等级枚举。
   */
  public enum RiskLevel {
    /** 低风险 */
    LOW,
    /** 中风险 */
    MEDIUM,
    /** 高风险 */
    HIGH,
    /** 严重 */
    CRITICAL
  }

  /**
   * 告警状态枚举。
   */
  public enum AlertStatus {
    /** 待处理 */
    PENDING,
    /** 已确认 */
    ACKNOWLEDGED,
    /** 已处理 */
    RESOLVED,
    /** 已忽略 */
    IGNORED
  }
}
