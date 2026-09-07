package com.njydsz.agent.domain.trace;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 执行链路 Span 的不可变值对象。
 *
 * <p>作为 {@link TraceRecorder} 实现与 OpenTelemetry/其他可观测性后端的中间表示，
 * 解耦业务链路记录与具体传输协议（OTel gRPC/HTTP、Log、消息队列等）间的编译期依赖。
 *
 * <p>一个 AgentSpan 描述 Agent 执行过程中的一个"操作单元"：
 * 无论是整条链路（LLM 调用链）还是单个步骤（Think/Act/Observe/Consolidate），
 * 均可建模为 Span。
 *
 * @param traceId 上级链路 Trace ID（业务生成，非 OTel 自动生成）
 * @param spanId Span 唯一 ID
 * @param parentSpanId 父 Span ID（整条链路根节点为 null）
 * @param name Span 名称（如 "ReActAgent.Think"、"RagService.Retrieve"）
 * @param kind Span 类型（INTERNAL / CLIENT / SERVER / PRODUCER / CONSUMER）
 * @param startTime 开始时间戳（UTC 毫秒）
 * @param endTime 结束时间戳（UTC毫秒）
 * @param status Span 状态描述（如 "SUCCESS" / "FAILED" / "RUNNING"）
 * @param attributes 键值对属性集合（携带 token 用量、模型名、agent 类型等观测数据）
 * @param events 有序事件列表（Span 执行期间记录的瞬时事件，如 "LLM 首 Token 已返回"）
 * @param cost USD 成本（可选，LLM 调用场景填充）
 * @author ydsz-team
 * @since 26.09.07
 */
public record AgentSpan(
    String traceId,
    String spanId,
    String parentSpanId,
    String name,
    String kind,
    Instant startTime,
    Instant endTime,
    String status,
    Map<String, String> attributes,
    List<SpanEvent> events,
    BigDecimal cost) {

  /**
   * 便捷构造：不含可选字段。
   */
  public AgentSpan(
      String traceId,
      String spanId,
      String parentSpanId,
      String name,
      String kind,
      Instant startTime,
      Instant endTime,
      String status) {
    this(traceId, spanId, parentSpanId, name, kind, startTime, endTime, status,
        Map.of(), List.of(), BigDecimal.ZERO);
  }

  /**
   * 获取指定属性值（安全访问）。
   *
   * @param key 属性键
   * @return Optional 包装的属性值
   */
  public Optional<String> attribute(String key) {
    return Optional.ofNullable(attributes != null ? attributes.get(key) : null);
  }

  /**
   * Span 瞬态事件。
   *
   * @param name 事件名称
   * @param timestamp 事件时间戳（UTC 毫秒）
   * @param attributes 事件携带的属性键值对
   * @author ydsz-team
   * @since 26.09.07
   */
  public record SpanEvent(String name, long timestamp, Map<String, String> attributes) {}
}
