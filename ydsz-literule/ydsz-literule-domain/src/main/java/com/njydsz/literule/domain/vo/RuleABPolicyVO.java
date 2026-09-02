package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 规则 A/B 测试策略视图对象（VO）。
 *
 * <p>用于 Controller 层返回 A/B 测试策略的完整信息，包含灰度比例、自动回滚阈值、 评估窗口及通知渠道配置，支撑规则灰度发布的效果评估与安全回滚。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RuleABPolicyVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 策略唯一标识（主键） */
  private String id;

  /** 关联的规则编码 */
  private String ruleCode;

  /** 是否启用自动回滚 */
  private Boolean autoRollbackEnabled;

  /** 回滚动作（ROLLBACK/NOTIFY_ONLY） */
  private String rollbackAction;

  /** 错误率阈值，超过此值触发自动回滚 */
  private BigDecimal errorRateThreshold;

  /** 最小样本量，样本不足时不触发回滚 */
  private Integer minSampleSize;

  /** 评估窗口（分钟），在此时间窗口内统计错误率 */
  private Integer checkWindowMinutes;

  /** 通知渠道，逗号分隔（如 "sms,email,dingtalk"） */
  private String notifyChannels;

  /** 策略描述 */
  private String description;

  /** 最近一次评估时间 */
  private LocalDateTime lastEvaluatedAt;

  /** 最近一次回滚时间 */
  private LocalDateTime lastRollbackAt;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
