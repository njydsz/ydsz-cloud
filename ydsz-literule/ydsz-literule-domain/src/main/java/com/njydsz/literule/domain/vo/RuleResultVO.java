package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则评估结果视图对象（VO）。
 *
 * <p>用于前端展示单次规则评估的输出：是否命中、严重级别、生成的告警标题/描述， 以及当前值、阈值、耗时与灰度桶来源，支撑告警展示与问题下钻。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResultVO {

  /** 规则编码 */
  private String ruleCode;

  /** 规则名称（展示用） */
  private String ruleName;

  /** 规则分类 */
  private String category;

  /** 是否命中触发（true=命中并产生告警） */
  private boolean triggered;

  /** 命中严重级别（代码，如 HIGH/MEDIUM/LOW/INFO） */
  private String severity;

  /** 命中严重级别枚举（可为 null） */
  private transient Object severityEnum;

  /** 告警标题（命中时根据模板生成） */
  private String title;

  /** 告警描述 */
  private String description;

  /** 当前实际值（用于与阈值对比展示） */
  private String currentValue;

  /** 规则设定的判定阈值 */
  private String threshold;

  /** 适用范围 */
  private String scope;

  /** 命中时间 */
  private LocalDateTime triggeredAt;

  /** 是否支持下钻查看命中详情 */
  private Boolean drilldownAvailable;

  /** 评估耗时（毫秒） */
  private long elapsedMs;

  /** 命中所属桶（如 NORMAL/CANARY，标识来自全量还是灰度） */
  private String canaryBucket;

  /** 是否灰度 */
  private boolean canary;

  /** 收集的子结果 */
  private List<RuleResultVO> collectedResults;

  /** 获取严重级别权重 */
  public int getSeverityWeight() {
    return 0;
  }

  /**
   * 创建一个未命中（标记为 false）的结果
   *
   * @param reason 未命中原因描述
   * @return 未命中结果的链式调用返回自身
   */
  public RuleResultVO notTriggered(String reason) {
    RuleResultVO result = new RuleResultVO();
    result.setTriggered(false);
    result.setTitle(null);
    result.setDescription(reason);
    result.setRuleCode(this.ruleCode);
    result.setRuleName(this.ruleName);
    result.setCategory(this.category);
    result.setSeverity(this.severity);
    result.setCurrentValue(this.currentValue);
    result.setThreshold(this.threshold);
    result.setScope(this.scope);
    result.setTriggeredAt(this.triggeredAt);
    result.setDrilldownAvailable(this.drilldownAvailable);
    result.setElapsedMs(this.elapsedMs);
    result.setCanaryBucket(this.canaryBucket);
    result.setCanary(this.canary);
    return result;
  }

  /** 获取权重 */
  public int getWeight() {
    return getSeverityWeight();
  }

  /** 获取规则代码（别名方法，用于兼容 lambda 表达式） */
  public String getCode() {
    return ruleCode;
  }
}
