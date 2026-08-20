package com.njydsz.literule.infra.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 规则评分卡实体
 *
 * <p>评分卡规则：基于 factors 列表（条件表达式 + 扣分）逐项评估。 基础分 base_score，低于 red_threshold 为红灯、低于 yellow_threshold
 * 为黄灯。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ydsz_rule_scorecard", autoResultMap = true)
public class RuleScorecardDO extends MpBaseEntity<String> {

  /** 规则编码 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 规则分类（RISK / QUALITY / PROFIT 等） */
  private String category;

  /** 规则描述 */
  private String description;

  /** 基础分（满分，默认 100） */
  private BigDecimal baseScore;

  /** 红灯阈值（≤ 触发红灯） */
  private BigDecimal redThreshold;

  /** 黄灯阈值（≤ 触发黄灯） */
  private BigDecimal yellowThreshold;

  /** 评分因子 JSON：[{conditionExpression, score, description}] */
  private String factors;

  /** 优先级（数字越小越优先） */
  private Integer priority;

  /** 是否启用 */
  private Boolean enabled;

  /** 适用范围（如 ALL / PROJECT_TYPE:CONSTRUCTION 表示限定项目类型） */
  private String scope;

  /** 版本号 */
  private Integer version;

  /** 供应商侧追踪 ID */
  private String providerTraceId;
}
