package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 规则评分卡视图对象（VO）。
 *
 * <p>用于 Controller 层返回评分卡的完整信息。评分卡是一种以基础分加减因子分 的方式计算最终分的规则类型，支持红黄线阈值设置，广泛应用于通用评分场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleScorecardVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 评分卡唯一标识（主键） */
  private String id;

  /** 关联的规则编码 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 分类编码 */
  private String category;

  /** 描述 */
  private String description;

  /** 基础分 */
  private BigDecimal baseScore;

  /** 红线阈值，低于此值标记为高风险 */
  private BigDecimal redThreshold;

  /** 黄线阈值，低于此值标记为中等风险 */
  private BigDecimal yellowThreshold;

  /** 评分因子 JSON，包含各因子名称、权重和计算方式 */
  private String factors;

  /** 优先级，数值越小优先级越高 */
  private Integer priority;

  /** 是否启用 */
  private Boolean enabled;

  /** 适用范围 */
  private String scope;

  /** 版本号 */
  private Integer version;

  /** 外部模型追踪 ID */
  private String providerTraceId;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
