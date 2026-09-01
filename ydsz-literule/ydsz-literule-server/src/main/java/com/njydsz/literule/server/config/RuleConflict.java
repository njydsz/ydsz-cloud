package com.njydsz.literule.server.config;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则冲突检测结果
 *
 * <p>由 {@link RuleConflictDetector} 在规则保存前检测，描述新规则与现有规则的潜在冲突。
 *
 * <p>冲突级别：
 *
 * <ul>
 *   <li>{@link Level#WARN}：潜在冲突，默认仅记录日志不阻塞保存
 *   <li>{@link Level#ERROR}：确定性冲突，默认阻塞保存（可通过配置关闭）
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleConflict implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 冲突级别 */
  public enum Level {
    /** 警告：潜在冲突，不阻塞保存 */
    WARN,
    /** 错误：确定性冲突，默认阻塞保存 */
    ERROR
  }

  /** 冲突类型 */
  public enum Type {
    /** 条件表达式完全相同（可能重复定义） */
    IDENTICAL_CONDITION,
    /** 条件相同但严重度不同（语义冲突） */
    CONTRADICTORY_SEVERITY,
    /** 同类别下名称相同但逻辑不同（命名冲突） */
    NAME_COLLISION,
    /**
     * 条件范围重叠（1.5.0 起）
     *
     * <p>两条规则的条件在相同变量上存在范围交集， 可能导致同一事实同时命中多条规则（除非属于同一互斥组）。
     */
    CONDITION_OVERLAP,
    /**
     * 死规则：条件永假（1.5.1 起）
     *
     * <p>条件表达式存在逻辑矛盾，如 {@code x > 10 && x < 5}， 永远不会触发，属于配置错误。
     */
    DEAD_RULE,
    /**
     * 互斥矛盾：同互斥组内规则条件互斥（1.5.1 起）
     *
     * <p>同互斥组的规则条件范围无交集，互斥组失去意义； 或非互斥组规则条件完全互斥，可合并为一条。
     */
    MUTEX_CONTRADICTION,
    /**
     * 子条件不可达（1.5.1 起）
     *
     * <p>复合条件中某子句被另一子句完全包含或排除， 导致该子句恒为冗余（如 {@code x > 5 && x > 3} 中 {@code x > 3} 不可达）。
     */
    UNREACHABLE_SUBCONDITION,
    /**
     * 执行顺序冲突（P0-F2 起）
     *
     * <p>两条规则条件范围重叠、优先级相同且无互斥组约束时， 执行顺序（注册顺序/并行分组）将直接影响评估结果， 存在"同一事实因顺序不同命中不同规则"的不确定性。
     */
    EXECUTION_ORDER_CONFLICT
  }

  /** 冲突类型 */
  private Type type;

  /** 冲突级别 */
  private Level level;

  /** 新规则编码 */
  private String newRuleCode;

  /** 冲突的已有规则编码 */
  private String conflictingRuleCode;

  /** 冲突描述 */
  private String description;
}
