package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.enums.FlowPerformType;

/**
 * 会签配置值对象。
 *
 * <p>封装节点 ext JSON 中会签相关的配置，提供类型安全的访问方式。
 * 替代从 ext Map 中直接获取 performType / approveCount / votePassRate 等弱类型方式。
 *
 * <p><b>会签类型：</b>
 *
 * <ul>
 *   <li>{@link FlowPerformType#OR} — 或签：任一人通过即推进
 *   <li>{@link FlowPerformType#PARALLEL} — 并行会签：全部通过才推进
 *   <li>{@link FlowPerformType#WEIGHTED} — 票签：加权投票，通过率 ≥ votePassRate 时推进
 * </ul>
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范）：</b>值对象置于 {@code domain/vo/} 包下，
 * 以 {@code Config} 结尾，不可变对象（所有字段 final）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@ToString
public class CountersignConfig implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认会签类型（OR 或签） */
  public static final FlowPerformType DEFAULT_PERFORM_TYPE = FlowPerformType.OR;

  /** 默认通过率阈值（0.5 = 过半数） */
  public static final BigDecimal DEFAULT_VOTE_PASS_RATE = new BigDecimal("0.5");

  /** 会签类型 */
  private final FlowPerformType performType;

  /** 会签所需通过人数（PARALLEL 模式：会签总人数） */
  private final int approveCount;

  /** 通过率阈值（0~1，WEIGHTED 模式使用） */
  private final BigDecimal votePassRate;

  private CountersignConfig(FlowPerformType performType, int approveCount, BigDecimal votePassRate) {
    this.performType = performType != null ? performType : DEFAULT_PERFORM_TYPE;
    this.approveCount = Math.max(1, approveCount);
    this.votePassRate =
        votePassRate != null ? votePassRate.max(BigDecimal.ONE.negate()).min(BigDecimal.ONE)
            : DEFAULT_VOTE_PASS_RATE;
  }

  /**
   * 从 ext JSON Map 解析会签配置。
   *
   * @param extMap 节点 ext JSON 解析后的 Map，不可为 null
   * @return 会签配置值对象（不可变）
   */
  public static CountersignConfig fromExt(Map<String, Object> extMap) {
    if (extMap == null || extMap.isEmpty()) {
      return new CountersignConfig(DEFAULT_PERFORM_TYPE, 1, DEFAULT_VOTE_PASS_RATE);
    }
    FlowPerformType type = parsePerformType(extMap.get("performType"));
    int approveCount = parseIntSafe(extMap.get("approveCount"), 1);
    BigDecimal voteRate = parseBigDecimalSafe(extMap.get("votePassRate"),
        DEFAULT_VOTE_PASS_RATE);
    return new CountersignConfig(type, approveCount, voteRate);
  }

  /**
   * 从 ext JSON 字符串解析会签配置。
   *
   * @param extJson ext JSON 字符串，可为 null 或空
   * @return 会签配置值对象（不可变）
   */
  public static CountersignConfig fromExtJson(String extJson) {
    if (extJson == null || extJson.isBlank()) {
      return new CountersignConfig(DEFAULT_PERFORM_TYPE, 1, DEFAULT_VOTE_PASS_RATE);
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(extJson);
      return fromExt(map);
    } catch (Exception e) {
      return new CountersignConfig(DEFAULT_PERFORM_TYPE, 1, DEFAULT_VOTE_PASS_RATE);
    }
  }

  /**
   * 是否为或签模式。
   *
   * @return true-或签；false-其他模式
   */
  public boolean isOrSign() {
    return performType == FlowPerformType.OR;
  }

  /**
   * 是否为并行会签模式。
   *
   * @return true-并行会签；false-其他模式
   */
  public boolean isParallel() {
    return performType == FlowPerformType.PARALLEL;
  }

  /**
   * 是否为票签模式。
   *
   * @return true-票签；false-其他模式
   */
  public boolean isWeighted() {
    return performType == FlowPerformType.WEIGHTED;
  }

  /**
   * 判断给定通过人数是否满足推进条件。
   *
   * @param finishedCount 已通过人数
   * @param totalCount 总人数
   * @return true-满足推进条件；false-不满足
   */
  public boolean isPassConditionMet(int finishedCount, int totalCount) {
    return switch (performType) {
      case OR -> finishedCount >= 1;
      case PARALLEL -> finishedCount >= Math.min(approveCount, totalCount);
      case WEIGHTED -> false; // 票签模式需要权重信息，使用 #isWeightPassConditionMet
    };
  }

  /**
   * 判断给定权重是否满足票签推进条件。
   *
   * @param approveWeight 累计通过权重
   * @param totalWeight 总权重
   * @return true-满足推进条件；false-不满足
   */
  public boolean isWeightPassConditionMet(int approveWeight, int totalWeight) {
    if (performType != FlowPerformType.WEIGHTED) {
      return isPassConditionMet(approveWeight, totalWeight);
    }
    if (totalWeight <= 0) {
      return false;
    }
    BigDecimal rate = BigDecimal.valueOf(approveWeight).divide(
        BigDecimal.valueOf(totalWeight), 4, BigDecimal.ROUND_HALF_UP);
    return rate.compareTo(votePassRate) >= 0;
  }

  // ==================== 内部工具方法 ====================

  private static int parseIntSafe(Object value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static BigDecimal parseBigDecimalSafe(Object value, BigDecimal defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    try {
      return new BigDecimal(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static FlowPerformType parsePerformType(Object value) {
    if (value == null) {
      return DEFAULT_PERFORM_TYPE;
    }
    String name = String.valueOf(value).toUpperCase();
    try {
      return FlowPerformType.valueOf(name);
    } catch (IllegalArgumentException e) {
      return DEFAULT_PERFORM_TYPE;
    }
  }
}
