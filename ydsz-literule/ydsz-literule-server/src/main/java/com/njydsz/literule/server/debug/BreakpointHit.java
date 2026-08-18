package com.njydsz.literule.server.debug;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 断点命中事件（F1 断点调试器）
 *
 * <p>断点被命中时生成，携带规则编码、节点类型、表达式文本、 当前事实快照（只读）与可查看的变量值，供调试客户端展示"当前停在哪"。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@Builder
public class BreakpointHit {

  /** 会话 ID */
  private String sessionId;

  /** 断点 ID */
  private String breakpointId;

  /** 规则编码 */
  private String ruleCode;

  /** 节点类型（表达式节点级断点；规则级断点为 null） */
  private String nodeType;

  /** 表达式文本（规则级断点为规则编码） */
  private String expression;

  /** 当前事实快照（防御性复制，只读） */
  @Builder.Default private Map<String, Object> facts = Collections.emptyMap();

  /** 命中时可直接查看的变量快照（变量名 → 值） */
  @Builder.Default private Map<String, Object> variables = Collections.emptyMap();

  /** 命中时间 */
  private LocalDateTime hitAt;

  /**
   * 创建规则级断点命中
   *
   * @param sessionId 会话 ID
   * @param breakpointId 断点 ID
   * @param ruleCode 规则编码
   * @param facts 事实快照
   * @return 断点命中事件
   */
  public static BreakpointHit ruleHit(
      String sessionId, String breakpointId, String ruleCode, Map<String, Object> facts) {
    return BreakpointHit.builder()
        .sessionId(sessionId)
        .breakpointId(breakpointId)
        .ruleCode(ruleCode)
        .nodeType(null)
        .expression(ruleCode)
        .facts(snapshot(facts))
        .variables(snapshot(facts))
        .hitAt(LocalDateTime.now())
        .build();
  }

  /**
   * 创建表达式节点级断点命中
   *
   * @param sessionId 会话 ID
   * @param breakpointId 断点 ID
   * @param ruleCode 规则编码
   * @param nodeType 节点类型
   * @param expression 表达式文本
   * @param facts 事实快照
   * @return 断点命中事件
   */
  public static BreakpointHit nodeHit(
      String sessionId,
      String breakpointId,
      String ruleCode,
      String nodeType,
      String expression,
      Map<String, Object> facts) {
    return BreakpointHit.builder()
        .sessionId(sessionId)
        .breakpointId(breakpointId)
        .ruleCode(ruleCode)
        .nodeType(nodeType)
        .expression(expression)
        .facts(snapshot(facts))
        .variables(snapshot(facts))
        .hitAt(LocalDateTime.now())
        .build();
  }

  /** 防御性复制事实快照 */
  private static Map<String, Object> snapshot(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }
}
