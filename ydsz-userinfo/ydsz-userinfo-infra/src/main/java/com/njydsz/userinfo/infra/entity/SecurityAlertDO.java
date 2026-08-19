package com.njydsz.userinfo.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 安全告警实体。
 *
 * <p>对应数据库表 {@code ydsz_security_alert}，存储安全告警事件记录。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>{@code idx_status_risk} — 状态+风险等级复合索引（待处理告警查询）</li>
 *   <li>{@code idx_type_time} — 告警类型+创建时间复合索引（告警去重统计）</li>
 *   <li>{@code idx_user_id} — 用户 ID 索引（按用户查询告警历史）</li>
 *   <li>{@code idx_source_ip} — 来源 IP 索引（IP 维度告警统计）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.18.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_security_alert")
public class SecurityAlertDO extends MpBaseEntity<String> {

  /** 告警类型（ACCOUNT_LOCKED/ACCOUNT_BANNED/MFA_FAILED/BRUTE_FORCE/ANOMALOUS_LOGIN/PASSWORD_SPRAY） */
  private String alertType;

  /** 风险等级（LOW/MEDIUM/HIGH/CRITICAL） */
  private String riskLevel;

  /** 关联用户 ID */
  private String userId;

  /** 关联用户名 */
  private String username;

  /** 来源 IP */
  private String sourceIp;

  /** 告警标题 */
  private String title;

  /**告警内容 */
  private String content;

  /** 告警状态（PENDING/ACKNOWLEDGED/RESOLVED/IGNORED） */
  private String status;

  /** 处理时间 */
  private LocalDateTime handledAt;

  /** 处理备注 */
  private String handlerNote;
}
