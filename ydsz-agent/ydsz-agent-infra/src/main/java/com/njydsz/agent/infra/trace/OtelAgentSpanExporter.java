package com.njydsz.agent.infra.trace;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.trace.AgentSpan;
import com.njydsz.agent.domain.trace.AgentSpanExporter;

/**
 * OpenTelemetry Span 导出器实现。
 *
 * <p>将内部 {@link AgentSpan} 转换为 OTel SDK 的 {@link Span} 并结束（触发 OTLP 导出器推送）。
 * 仅当 {@code io.opentelemetry.api.OpenTelemetry} 在类路径上时由 {@code AgentOtelAutoConfiguration} 注册。
 *
 * <p>使用 OTel SDK 的 {@code Span} API 创建"事后 Span"（backdated Span），
 * 通过 {@code setEpoch(起始)} 和 {@code end(结束)} 设置精确起止时间。
 *
 * <p><b>容错</b>：所有 OTel 调用包在 try-catch 中，避免 OTel SDK 内部异常传导到链路记录。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Slf4j
public class OtelAgentSpanExporter implements AgentSpanExporter {

  /** 默认 instrumentation 名称（用于 TracerProvider.get()） */
  private static final String INSTRUMENTATION_NAME = "com.njydsz.agent";

  /** instrumentation 版本号（当前项目语义版本） */
  private static final String INSTRUMENTATION_VERSION = "26.09.07";

  /** OpenTelemetry Tracer */
  private final Tracer tracer;

  /**
   * 构造 OTel Span 导出器。
   *
   * @param openTelemetry OpenTelemetry 实例（由 Spring Boot Actuator + Micrometer 提供或用户注册）
   */
  public OtelAgentSpanExporter(OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    log.info("[Otel] OpenTelemetry Span 导出器已启用, instrumentation={}:{}", 
        INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
  }

  /**
   * {@inheritDoc}
   *
   * <p>将 AgentSpan 的时间区间直接映射为 OTel Span。由于 AgentSpan 的时间区间已经确定，
   * 使用 {@code Span.Builder.setSpanKind()} + {@code setEpoch()} 构建"已完成的 Span"并立即结束。
   */
  @Override
  public void export(AgentSpan agentSpan) {
    try {
      Span span = tracer.spanBuilder(agentSpan.name())
          .setSpanKind(mapSpanKind(agentSpan.kind()))
          .setAllAttributes(mapAttributes(agentSpan.attributes()))
          .setStartTimestamp(agentSpan.startTime())
          .startSpan();

      // 添加事件
      if (agentSpan.events() != null) {
        for (AgentSpan.SpanEvent event : agentSpan.events()) {
          span.addEvent(event.name(),
              mapAttributes(event.attributes()),
              Instant.ofEpochMilli(event.timestamp()));
        }
      }

      // 设置状态
      StatusCode statusCode = mapStatusCode(agentSpan.status());
      span.setStatus(statusCode);

      // 记录成本属性
      if (agentSpan.cost() != null && agentSpan.cost().compareTo(BigDecimal.ZERO) > 0) {
        span.setAttribute("cost.usd", agentSpan.cost().doubleValue());
      }

      // 结束 Span（指定导出）
      span.end(agentSpan.endTime());
    } catch (Exception e) {
      // OTel SDK 异常不应影响链路记录
      log.warn("[Otel] Span 导出异常: span={}, err={}", agentSpan.name(), e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>OTel SDK 的 Span 导出是无状态的（GPRC/HTTP 连接由 OTel SDK 内部管理），
   * 本方法不执行资源释放；容器销毁时 OTel SDK 自身会关闭。
   */
  @Override
  public void close() {
    // OTel SDK 全局生命周期由 OpenTelemetry 实例自行管理
    log.info("[Otel] OtelAgentSpanExporter 已关闭");
  }

  // ========================= 私有方法 =========================

  /**
   * 映射内部 Span 类型字符串到 OTel SpanKind 枚举。
   */
  private io.opentelemetry.api.trace.SpanKind mapSpanKind(String kind) {
    if (kind == null) {
      return io.opentelemetry.api.trace.SpanKind.INTERNAL;
    }
    return switch (kind.toUpperCase()) {
      case "CLIENT" -> io.opentelemetry.api.trace.SpanKind.CLIENT;
      case "SERVER" -> io.opentelemetry.api.trace.SpanKind.SERVER;
      case "PRODUCER" -> io.opentelemetry.api.trace.SpanKind.PRODUCER;
      case "CONSUMER" -> io.opentelemetry.api.trace.SpanKind.CONSUMER;
      default -> io.opentelemetry.api.trace.SpanKind.INTERNAL;
    };
  }

  /**
   * 映射状态字符串到 OTel StatusCode 枚举。
   */
  private StatusCode mapStatusCode(String status) {
    if (status == null) {
      return StatusCode.UNSET;
    }
    return switch (status.toUpperCase()) {
      case "SUCCESS" -> StatusCode.OK;
      case "FAILED", "ERROR" -> StatusCode.ERROR;
      default -> StatusCode.UNSET;
    };
  }

  /**
   * Map String 属性转换为 OTel Attributes。
   *
   * <p>OTel Attributes 支持 String / long / double / boolean。当前将所有属性转换为 String，
   * 更精确的类型映射（如 cost → double）由调用方在 AgentSpan.attributes 中预留 OTel 语义名。
   */
  private io.opentelemetry.api.common.Attributes mapAttributes(Map<String, String> attrs) {
    if (attrs == null || attrs.isEmpty()) {
      return io.opentelemetry.api.common.Attributes.empty();
    }
    io.opentelemetry.api.common.AttributesBuilder builder = io.opentelemetry.api.common.Attributes.builder();
    for (Map.Entry<String, String> entry : attrs.entrySet()) {
      builder.put(AttributeKey.stringKey(entry.getKey()), entry.getValue());
    }
    return builder.build();
  }
}
