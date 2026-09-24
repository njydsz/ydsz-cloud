package com.njydsz.literule.domain.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.jdbc.handler.JsonTypeHandler;

/**
 * 规则评分卡实体。
 *
 * <p>对应 {@code ydsz_rule_scorecard} 表，以扣分制评分卡模式评估业务对象的风险等级。
 * 评估流程：从 {@code baseScore} 基础分出发，依次判断评分因子（{@code factors}，JSON 列表，每项含条件表达式与扣分值），
 * 满足条件则减去对应分数。最终得分低于 {@code redThreshold} 判定为红灯风险，低于 {@code yellowThreshold} 判定为黄灯预警。
 *
 * <p>算法复杂度为 O(F)，F 为评分因子数量。适用于多维度累加扣分、总分阈值判级的典型风控场景。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 与泛型继承产生 unchecked 警告
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ydsz_rule_scorecard", autoResultMap = true)
public class RuleScorecard extends MpBaseEntity<String> {

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
  @TableField(typeHandler = JsonTypeHandler.class)
  private String factors;

  /** 优先级（数字越小越优先） */
  private Integer priority;

  /** 是否启用 */
  private Boolean isEnabled;

  /** 适用范围（如 ALL / PROJECT_TYPE:CONSTRUCTION 表示限定项目类型） */
  private String scope;

  /** 版本号 */
  private Integer version;

  /** 供应商侧追踪 ID */
  private String providerTraceId;
}
