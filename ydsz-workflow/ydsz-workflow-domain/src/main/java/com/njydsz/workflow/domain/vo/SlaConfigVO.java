package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.enums.FlowSlaAction;

/**
 * SLA 超时配置值对象。
 *
 * <p>封装节点 ext JSON 中的超时相关配置，提供类型安全的访问方式。
 * 替代 {@link FlowNodeVO#getTimeoutMinutes()}、{@link FlowNodeVO#getTimeoutStrategy()}、
 * {@link FlowNodeVO#getEscalateUser()} 等弱类型 getter。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * SlaConfig sla = SlaConfigVO.fromExt(node.getExtMap());
 * if (sla.isTimeoutEnabled()) {
 *     LocalDateTime dueAt = sla.calculateDueAt(task.getClaimAt());
 * }
 * }</pre>
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>值对象置于 {@code domain/vo/} 包下，
 * 以 {@code Config} 结尾，不可变对象（所有字段 final）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@ToString
public class SlaConfigVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认超时分钟数（120 分钟） */
  public static final int DEFAULT_TIMEOUT_MINUTES = 120;

  /** 默认超时动作（REMIND 催办） */
  public static final FlowSlaAction DEFAULT_ACTION = FlowSlaAction.REMIND;

  /** 超时分钟数（≤0 表示不启用超时） */
  private final int timeoutMinutes;

  /** 超时后触发的动作 */
  private final FlowSlaAction action;

  /** 升级用户 ID（ESCALATE 动作时有效） */
  private final String escalateUserId;

  private SlaConfigVO(int timeoutMinutes, FlowSlaAction action, String escalateUserId) {
    this.timeoutMinutes = Math.max(0, timeoutMinutes);
    this.action = action != null ? action : DEFAULT_ACTION;
    this.escalateUserId = escalateUserId != null ? escalateUserId : "";
  }

  /**
   * 从 ext JSON Map 解析 SLA 配置。
   *
   * @param extMap 节点 ext JSON 解析后的 Map，不可为 null
   * @return SLA 配置值对象（不可变）
   */
  public static SlaConfigVO fromExt(Map<String, Object> extMap) {
    if (extMap == null || extMap.isEmpty()) {
      return new SlaConfigVO(0, DEFAULT_ACTION, "");
    }
    int timeout = parseIntSafe(extMap.get("timeout"), 0);
    FlowSlaAction action = parseSlaAction(extMap.get("timeoutStrategy"));
    String escalateUser = parseStringSafe(extMap.get("escalateUser"));
    return new SlaConfigVO(timeout, action, escalateUser);
  }

  /**
   * 从 ext JSON 字符串解析 SLA 配置。
   *
   * @param extJson ext JSON 字符串，可为 null 或空
   * @return SLA 配置值对象（不可变）
   */
  public static SlaConfigVO fromExtJson(String extJson) {
    if (extJson == null || extJson.isBlank()) {
      return new SlaConfigVO(0, DEFAULT_ACTION, "");
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(extJson);
      return fromExt(map);
    } catch (Exception e) {
      return new SlaConfigVO(0, DEFAULT_ACTION, "");
    }
  }

  /**
   * 是否启用超时（timeoutMinutes > 0）。
   *
   * @return true-启用超时；false-不启用
   */
  public boolean isTimeoutEnabled() {
    return timeoutMinutes > 0;
  }

  /**
   * 是否为终态动作（ESCALATE / AUTO_PASS / AUTO_REJECT）。
   *
   * <p>终态动作会改变流程状态，中间态（REMIND / NOTIFY）只发送通知。
   *
   * @return true-终态动作；false-中间态
   */
  public boolean isTerminalAction() {
    return action == FlowSlaAction.ESCALATE
        || action == FlowSlaAction.AUTO_PASS
        || action == FlowSlaAction.AUTO_REJECT;
  }

  /**
   * 是否需要升级用户（ESCALATE 动作且配置了升级用户）。
   *
   * @return true-需要升级；false-不需要
   */
  public boolean hasEscalation() {
    return action == FlowSlaAction.ESCALATE && !escalateUserId.isBlank();
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

  private static String parseStringSafe(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static FlowSlaAction parseSlaAction(Object value) {
    if (value == null) {
      return DEFAULT_ACTION;
    }
    String name = String.valueOf(value).toUpperCase();
    try {
      return FlowSlaAction.valueOf(name);
    } catch (IllegalArgumentException e) {
      return DEFAULT_ACTION;
    }
  }
}
