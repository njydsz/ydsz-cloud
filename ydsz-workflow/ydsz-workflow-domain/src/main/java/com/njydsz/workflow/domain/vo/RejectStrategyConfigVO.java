package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

import com.njydsz.common.json.YdszJson;

/**
 * 节点驳回策略配置值对象。
 *
 * <p>借鉴 Flowlong 的「驳回策略可配置化」能力，允许节点级别配置驳回行为，
 * 而非全局固定策略。每个审批节点可独立配置适用的驳回策略。
 *
 * <p><b>ext JSON 配置：</b>
 *
 * <ul>
 *   <li>{@code rejectStrategy}：默认驳回策略（PREVIOUS / INITIATOR / ANY_NODE / CUSTOM，默认 PREVIOUS）
 *   <li>{@code allowedStrategies}：允许的驳回策略列表（JSON 数组，默认 ["PREVIOUS", "INITIATOR"]）
 *   <li>{@code reExecuteMode}：驳回后重执行模式（CONTINUE / RETURN，默认 RETURN）
 *   <li>{@code customTarget}：自定义驳回目标节点编码（rejectStrategy=CUSTOM 时必填）
 * </ul>
 *
 * <p><b>策略说明：</b>
 *
 * <ul>
 *   <li>PREVIOUS：回上一节点（默认行为）
 *   <li>INITIATOR：回发起人（重新开始）
 *   <li>ANY_NODE：回任意历史节点
 *   <li>CUSTOM：回指定节点
 * </ul>
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>值对象置于 {@code domain/vo/} 包下，
 * 以 {@code Config} 结尾，不可变对象。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.domain.enums.FlowNodeType#APPROVAL
 */
@Getter
@ToString
public class RejectStrategyConfigVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认驳回策略 */
  public static final RejectStrategy DEFAULT_STRATEGY = RejectStrategy.PREVIOUS;

  /** 默认重执行模式 */
  public static final ReExecuteMode DEFAULT_RE_EXECUTE_MODE = ReExecuteMode.RETURN;

  /** 默认允许的驳回策略列表 */
  private static final List<RejectStrategy> DEFAULT_ALLOWED_STRATEGIES = List.of(
      RejectStrategy.PREVIOUS, RejectStrategy.INITIATOR);

  /** 默认驳回策略 */
  private final RejectStrategy rejectStrategy;

  /** 允许的驳回策略列表 */
  private final List<RejectStrategy> allowedStrategies;

  /** 驳回后重执行模式 */
  private final ReExecuteMode reExecuteMode;

  /** 自定义驳回目标节点编码 */
  private final String customTarget;

  private RejectStrategyConfigVO(RejectStrategy rejectStrategy,
      List<RejectStrategy> allowedStrategies, ReExecuteMode reExecuteMode, String customTarget) {
    this.rejectStrategy = rejectStrategy != null ? rejectStrategy : DEFAULT_STRATEGY;
    this.allowedStrategies =
        allowedStrategies != null && !allowedStrategies.isEmpty() ? allowedStrategies
            : DEFAULT_ALLOWED_STRATEGIES;
    this.reExecuteMode = reExecuteMode != null ? reExecuteMode : DEFAULT_RE_EXECUTE_MODE;
    this.customTarget = customTarget != null ? customTarget : "";
  }

  /**
   * 从 ext JSON Map 解析驳回策略配置。
   *
   * @param extMap 节点 ext JSON 解析后的 Map，不可为 null
   * @return 驳回策略配置值对象（不可变）
   */
  public static RejectStrategyConfigVO fromExt(Map<String, Object> extMap) {
    if (extMap == null || extMap.isEmpty()) {
      return new RejectStrategyConfigVO(DEFAULT_STRATEGY, DEFAULT_ALLOWED_STRATEGIES,
          DEFAULT_RE_EXECUTE_MODE, "");
    }
    RejectStrategy strategy = parseRejectStrategy(extMap.get("rejectStrategy"));
    List<RejectStrategy> allowed = parseAllowedStrategies(extMap.get("allowedStrategies"));
    ReExecuteMode mode = parseReExecuteMode(extMap.get("reExecuteMode"));
    String customTarget = parseStringSafe(extMap.get("customTarget"));
    return new RejectStrategyConfigVO(strategy, allowed, mode, customTarget);
  }

  /**
   * 从 ext JSON 字符串解析驳回策略配置。
   *
   * @param extJson ext JSON 字符串，可为 null 或空
   * @return 驳回策略配置值对象（不可变）
   */
  public static RejectStrategyConfigVO fromExtJson(String extJson) {
    if (extJson == null || extJson.isBlank()) {
      return new RejectStrategyConfigVO(DEFAULT_STRATEGY, DEFAULT_ALLOWED_STRATEGIES,
          DEFAULT_RE_EXECUTE_MODE, "");
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(extJson);
      return fromExt(map);
    } catch (Exception e) {
      return new RejectStrategyConfigVO(DEFAULT_STRATEGY, DEFAULT_ALLOWED_STRATEGIES,
          DEFAULT_RE_EXECUTE_MODE, "");
    }
  }

  /**
   * 是否允许指定驳回策略。
   *
   * @param strategy 待检查的策略
   * @return true 表示允许
   */
  public boolean isAllowed(RejectStrategy strategy) {
    return strategy != null && allowedStrategies.contains(strategy);
  }

  /**
   * 是否需要指定驳回目标节点。
   *
   * @return true 表示需要 customTarget
   */
  public boolean requiresCustomTarget() {
    return rejectStrategy == RejectStrategy.CUSTOM;
  }

  /**
   * 获取有效的驳回目标节点编码。
   *
   * @return CUSTOM 模式下返回 customTarget，否则返回空串
   */
  public String getEffectiveTarget() {
    return requiresCustomTarget() ? customTarget : "";
  }

  /**
   * 驳回策略类型枚举。
   *
   * <p>定义流程节点支持的驳回策略类型。
   */
  public enum RejectStrategy {
    /** 回上一节点 */
    PREVIOUS,
    /** 回发起人（重新开始） */
    INITIATOR,
    /** 回任意历史节点 */
    ANY_NODE,
    /** 回指定节点 */
    CUSTOM
  }

  /**
   * 驳回后重执行模式枚举。
   *
   * <p>控制驳回后重新经过已执行节点时的行为。
   */
  public enum ReExecuteMode {
    /** 继续：跳过已执行的自动节点 */
    CONTINUE,
    /** 返回：重新执行全部节点 */
    RETURN
  }

  // ==================== 内部工具方法 ====================

  private static String parseStringSafe(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static RejectStrategy parseRejectStrategy(Object value) {
    if (value == null) {
      return DEFAULT_STRATEGY;
    }
    String name = String.valueOf(value).toUpperCase();
    try {
      return RejectStrategy.valueOf(name);
    } catch (IllegalArgumentException e) {
      return DEFAULT_STRATEGY;
    }
  }

  @SuppressWarnings("unchecked")
  private static List<RejectStrategy> parseAllowedStrategies(Object value) {
    if (value == null) {
      return DEFAULT_ALLOWED_STRATEGIES;
    }
    if (value instanceof List<?> list) {
      return list.stream()
          .map(Object::toString)
          .map(String::toUpperCase)
          .filter(s -> {
            try {
              RejectStrategy.valueOf(s);
              return true;
            } catch (IllegalArgumentException e) {
              return false;
            }
          })
          .map(RejectStrategy::valueOf)
          .toList();
    }
    return DEFAULT_ALLOWED_STRATEGIES;
  }

  private static ReExecuteMode parseReExecuteMode(Object value) {
    if (value == null) {
      return DEFAULT_RE_EXECUTE_MODE;
    }
    String name = String.valueOf(value).toUpperCase();
    try {
      return ReExecuteMode.valueOf(name);
    } catch (IllegalArgumentException e) {
      return DEFAULT_RE_EXECUTE_MODE;
    }
  }
}
