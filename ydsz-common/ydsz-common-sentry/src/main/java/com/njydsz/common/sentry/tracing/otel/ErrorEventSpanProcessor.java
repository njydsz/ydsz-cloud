package com.njydsz.common.sentry.tracing.otel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.StatusData;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Span 错误事件处理器
 *
 * <p>在 Span 结束时，如果检测到以下情况，自动注入告警事件：
 *
 * <ul>
 *   <li>Span 状态为 ERROR 且未携带 ydsz.error.code 属性
 *   <li>HTTP 状态码 5xx 但 Span 状态仍为 OK（未正确标记）
 *   <li>耗时超过指定阈值（已配 SpanEvaluationProcessor.slowRequest，此处仅补齐告警）
 * </ul>
 *
 * <p>业务方可注册 {@link ErrorEventListener} 接收告警事件。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class ErrorEventSpanProcessor implements SpanProcessor {

  private final ErrorEventConfig config;
  private final List<ErrorEventListener> listeners = new CopyOnWriteArrayList<>();

  /**
   * error event span。
   * @param config 参数
 */
  public ErrorEventSpanProcessor(ErrorEventConfig config) {
    this.config = config;
    log.info(
        "[Sentry] ErrorEventSpanProcessor 初始化，slowThreshold={}ms", config.getSlowThresholdMillis());
  }

  @Override
  /**
   * on start。
   * @param parentContext 参数
   * @param span 参数
   */
  public void onStart(Context parentContext, ReadWriteSpan span) {
    // no-op
  }

  @Override
  /**
   * is start required。
   * @return 结果
   */
  public boolean isStartRequired() {
    return false;
  }

  @Override
  /**
   * on end。
   * @param span 参数
   */
  public void onEnd(ReadableSpan span) {
    try {
      ErrorEvent event = evaluate(span);
      if (event != null) {
        notifyListeners(event);
      }
    } catch (Exception e) {
      log.debug("[Sentry] ErrorEvent 评估失败: {}", e.getMessage());
    }
  }

  /** 评估 Span 异常 */
  private ErrorEvent evaluate(ReadableSpan span) {
    // 1) HTTP 5xx 但未标记为 ERROR
    Long status = span.getAttribute(OtelSemConv.HTTP_RESPONSE_STATUS_CODE);
    boolean isServerError = status != null && status >= 500 && status < 600;
    StatusData spanStatus = span.toSpanData().getStatus();
    boolean isError = spanStatus.getStatusCode() == StatusCode.ERROR;

    if (isServerError || isError) {
      String errorCode = span.getAttribute(OtelSemConv.REMI_ERROR_CODE);
      return new ErrorEvent(
          span.getSpanContext().getTraceId(),
          span.getSpanContext().getSpanId(),
          span.getName(),
          isServerError ? ErrorEvent.Reason.SERVER_ERROR : ErrorEvent.Reason.SPAN_ERROR,
          errorCode != null ? errorCode : (isServerError ? "HTTP_" + status : "UNCLASSIFIED"),
          spanStatus.getDescription(),
          span.getLatencyNanos() / 1_000_000L,
          span.getKind());
    }

    // 2) 慢请求
    if (config.getSlowThresholdMillis() > 0) {
      long durationMs = span.getLatencyNanos() / 1_000_000L;
      if (durationMs > config.getSlowThresholdMillis()
          && span.getKind() != SpanKind.CLIENT
          && span.getKind() != SpanKind.PRODUCER) {
        return new ErrorEvent(
            span.getSpanContext().getTraceId(),
            span.getSpanContext().getSpanId(),
            span.getName(),
            ErrorEvent.Reason.SLOW,
            "SLOW_SPAN",
            "耗时 " + durationMs + "ms 超过阈值 " + config.getSlowThresholdMillis() + "ms",
            durationMs,
            span.getKind());
      }
    }
    return null;
  }

  @Override
  /**
   * is end required。
   * @return 结果
   */
  public boolean isEndRequired() {
    return true;
  }

  @Override
  /**
   * close。
   */
  public void close() {
    // no-op
  }

  /** 注册错误事件监听器 */
  /**
   * add listener。
   * @param listener 参数
   */
  public void addListener(ErrorEventListener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  private void notifyListeners(ErrorEvent event) {
    for (ErrorEventListener l : listeners) {
      try {
        l.onErrorEvent(event);
      } catch (Exception e) {
        // 监听器异常不影响主流程
        log.debug("[ErrorEvent] 监听器异常: {}", e.getMessage());
      }
    }
  }

  // ============================================================================
  // 配置
  // ============================================================================

  /**
   * 错误事件判定配置。
   *
   * <p>当前仅控制「慢 Span」这一路判定：Span 结束时若耗时超过阈值，且 Span 类型不是 {@code CLIENT} /
   * {@code PRODUCER}（避免把下游调用与消息发送重复计为慢请求），则产生一个 {@code Reason.SLOW} 的错误事件。
   *
   * <p>把阈值设为 {@code 0} 或负数即可整体关闭慢 Span 检测；Span 自带错误状态与 HTTP 5xx 两路判定不受此配置影响。
   */
  @Data
  public static class ErrorEventConfig {

    /** 慢 Span 阈值（毫秒），耗时严格大于该值才触发；{@code <= 0} 表示关闭慢 Span 检测 */
    private long slowThresholdMillis = 3000;
  }

  // ============================================================================
  // 事件
  // ============================================================================

  /** 错误事件 */
  @Data
  public static class ErrorEvent {
    /** 错误事件触发原因。 */
    public enum Reason {
      /** Span 自带错误状态（StatusCode.ERROR） */
      SPAN_ERROR,
      /** HTTP 服务端 5xx 响应 */
      SERVER_ERROR,
      /** 执行耗时超过慢请求阈值 */
      SLOW
    }

    private final String traceId;
    private final String spanId;
    private final String spanName;
    private final Reason reason;
    private final String errorCode;
    private final String message;
    private final long durationMillis;
    private final SpanKind kind;
    private final long timestamp = System.currentTimeMillis();

    public ErrorEvent(
        String traceId,
        String spanId,
        String spanName,
        Reason reason,
        String errorCode,
        String message,
        long durationMillis,
        SpanKind kind) {
      this.traceId = traceId;
      this.spanId = spanId;
      this.spanName = spanName;
      this.reason = reason;
      this.errorCode = errorCode;
      this.message = message;
      this.durationMillis = durationMillis;
      this.kind = kind;
    }
  }

  /** 错误事件监听器 */
  @FunctionalInterface
  public interface ErrorEventListener {
    void onErrorEvent(ErrorEvent event);
  }
}
