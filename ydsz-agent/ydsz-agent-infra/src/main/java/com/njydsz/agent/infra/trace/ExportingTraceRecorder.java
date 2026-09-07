package com.njydsz.agent.infra.trace;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.trace.AgentSpan;
import com.njydsz.agent.domain.trace.AgentSpanExporter;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.common.core.trace.TraceIdGenerator;

/**
 * Span 导出装饰器 — 代理 {@link TraceRecorder} 并在各生命周期钩子导出 Span。
 *
 * <p>装饰器模式：不修改原有 {@code PgTraceRecorder} 或 {@code InMemoryTraceRecorder}，
 * 而是在其外围包装一层"导出到 OTel"的副作用，保持单一职责原则。
 *
 * <p>导出的 Span 结构：
 * <ul>
 *   <li>链路级 Span（AGENT_EXECUTION）：每次 {@link #startTrace} 到 {@link #endTrace} 对应一条根 Span</li>
 *   <li>步骤级 Span（AGENT_STEP）：每次 {@link #recordStep} 对应一条子 Span，父 Span 为链路根 Span</li>
 * </ul>
 *
 * <p><b>线程安全</b>：内部使用 {@link ConcurrentHashMap} 维护链路 ID → 起止时间的映射。
 * 步骤级 Span 通过 {@link TraceIdGenerator} 生成唯一 spanId。
 *
 * <p><b>容错</b>：导出失败不影响原有链路记录（try-catch 包外层）。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
public class ExportingTraceRecorder implements TraceRecorder {

  /** Span 属性集合初始容量 */
  private static final int ATTRIBUTES_CAPACITY = 8;

  /** 步骤事件列表初始容量 */
  private static final int EVENTS_CAPACITY = 4;

  /** 底层链路记录器（实际写入 PG 或内存） */
  private final TraceRecorder delegate;

  /** Span 导出器（OTel / Noop） */
  private final AgentSpanExporter exporter;

  /** 链路级 Span 元数据（traceId → 根 Span 信息） */
  private final Map<String, AgentRootSpanMeta> rootSpanMetas = new ConcurrentHashMap<>();

  /**
   * 构造 Span 导出装饰器。
   *
   * @param delegate 底层链路记录器，不可为 {@code null}
   * @param exporter Span 导出器，不可为 {@code null}
   */
  public ExportingTraceRecorder(TraceRecorder delegate, AgentSpanExporter exporter) {
    this.delegate = delegate;
    this.exporter = exporter;
  }

  /**
   * {@inheritDoc}
   *
   * <p>在底层记录器启动链路后，记录根 Span 元数据（用于步骤级 Span 的 parentSpanId 关联）。
   */
  @Override
  public String startTrace(String conversationId, String agentId) {
    String traceId = delegate.startTrace(conversationId, agentId);
    String rootSpanId = TraceIdGenerator.generateSortableTraceId();
    rootSpanMetas.put(traceId,
        new AgentRootSpanMeta(traceId, rootSpanId, conversationId, agentId, Instant.now()));
    return traceId;
  }

  /**
   * {@inheritDoc}
   *
   * <p>同时导出一条步骤级 Span（AGENT_STEP 类型）。
   */
  @Override
  public void recordStep(
      String traceId,
      String stepType,
      String content,
      Object input,
      Object output,
      long durationMs) {
    recordStep(traceId, stepType, content, input, output, durationMs, BigDecimal.ZERO);
  }

  /**
   * {@inheritDoc}
   *
   * <p>同时导出一条步骤级 Span（AGENT_STEP 类型），携带成本信息。
   */
  @Override
  public void recordStep(
      String traceId,
      String stepType,
      String content,
      Object input,
      Object output,
      long durationMs,
      BigDecimal cost) {
    delegate.recordStep(traceId, stepType, content, input, output, durationMs, cost);
    exportStepSpan(traceId, stepType, content, durationMs, cost);
  }

  /**
   * {@inheritDoc}
   *
   * <p>在底层记录器结束链路后，导出链路级 Span（AGENT_EXECUTION 类型）。
   */
  @Override
  public void endTrace(String traceId, String status) {
    delegate.endTrace(traceId, status);
    exportRootSpan(traceId, status);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<TraceStep> getSteps(String traceId) {
    return delegate.getSteps(traceId);
  }

  // ========================= 私有方法：Span 构建与导出 =========================

  /**
   * 导出步骤级 Span。
   */
  private void exportStepSpan(
      String traceId, String stepType, String content, long durationMs, BigDecimal cost) {
    try {
      AgentRootSpanMeta rootMeta = rootSpanMetas.get(traceId);
      String parentSpanId = rootMeta != null ? rootMeta.rootSpanId() : null;
      String spanId = TraceIdGenerator.generateSortableTraceId();
      Instant endTime = Instant.now();
      Instant instantStartTime = endTime.minusMillis(Math.max(durationMs, 0L));

      Map<String, String> attrs = new HashMap<>(ATTRIBUTES_CAPACITY);
      attrs.put("agent.step.type", stepType);
      attrs.put("agent.id", rootMeta != null ? rootMeta.agentId() : "unknown");
      if (content != null) {
        attrs.put("agent.step.content", truncate(content, 500));
      }

      List<AgentSpan.SpanEvent> events = new ArrayList<>(EVENTS_CAPACITY);
      if (cost != null && cost.compareTo(BigDecimal.ZERO) > 0) {
        events.add(new AgentSpan.SpanEvent(
            "cost_recorded",
            endTime.toEpochMilli(),
            Map.of("cost.usd", cost.toPlainString())));
      }

      AgentSpan span = new AgentSpan(
          traceId, spanId, parentSpanId, stepType, "INTERNAL",
          instantStartTime, endTime, "SUCCESS", attrs, events, cost != null ? cost : BigDecimal.ZERO);
      exporter.export(span);
    } catch (Exception e) {
      // 导出失败不应影响主链路记录
      log.warn("[Otel] 步骤 Span 导出失败: traceId={}, step={}, err={}",
          traceId, stepType, e.getMessage());
    }
  }

  /**
   * 导出链路级 Span。
   */
  private void exportRootSpan(String traceId, String status) {
    try {
      AgentRootSpanMeta rootMeta = rootSpanMetas.remove(traceId);
      if (rootMeta == null) {
        return;
      }
      Instant endTime = Instant.now();

      Map<String, String> attrs = new HashMap<>(ATTRIBUTES_CAPACITY);
      attrs.put("agent.id", rootMeta.agentId());
      attrs.put("conversation.id", rootMeta.conversationId());

      AgentSpan span = new AgentSpan(
          traceId, rootMeta.rootSpanId(), null, rootMeta.agentId(),
          "INTERNAL", rootMeta.startTime(), endTime,
          status != null ? status : "SUCCESS", attrs, List.of(), BigDecimal.ZERO);
      exporter.export(span);
    } catch (Exception e) {
      log.warn("[Otel] 链路 Span 导出失败: traceId={}, err={}", traceId, e.getMessage());
    }
  }

  /**
   * 截断超长字符串。
   *
   * @param value 原始值
   * @param maxLength 最大长度
   * @return 截断后的字符串
   */
  private String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() > maxLength ? value.substring(0, maxLength) : value;
  }

  /**
   * 链路级 Span 的元数据（步骤 Span 需要用它确定 parentSpanId）。
   *
   * @param traceId 链路 ID
   * @param rootSpanId 根 Span ID（步骤 Span 的 parentSpanId）
   * @param conversationId 对话 ID
   * @param agentId Agent ID
   * @param startTime 链路开始时间
   */
  private record AgentRootSpanMeta(
      String traceId,
      String rootSpanId,
      String conversationId,
      String agentId,
      Instant startTime) {}
}
