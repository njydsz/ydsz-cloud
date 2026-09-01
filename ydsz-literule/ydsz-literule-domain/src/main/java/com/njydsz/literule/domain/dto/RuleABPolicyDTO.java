package com.njydsz.literule.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 规则 A/B 测试策略 DTO（统一新增/修改）。
 *
 * <p>创建时 {@code id} 字段不传，更新时传入 {@code id}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleABPolicyDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 策略唯一标识（主键，更新时传入） */
  private String id;

  /** 关联的规则编码 */
  private String ruleCode;

  /** 是否启用自动回滚 */
  private Boolean autoRollbackEnabled;

  /** 回滚动作（AUTO/NOTIFY） */
  private String rollbackAction;

  /** 错误率阈值，超过此值触发自动回滚 */
  private BigDecimal errorRateThreshold;

  /** 最小样本量，样本不足时不触发回滚 */
  private Integer minSampleSize;

  /** 评估窗口（分钟） */
  private Integer checkWindowMinutes;

  /** 通知渠道，逗号分隔 */
  private String notifyChannels;

  /** 策略描述 */
  private String description;
}
