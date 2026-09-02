package com.njydsz.common.sentry.tracing;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import com.njydsz.common.sentry.spi.TraceContext;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 默认追踪上下文（降级方案）
 *
 * <p>当 SkyWalking 不可用时使用 UUID 生成 TraceId，通过 MDC 传递。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class DefaultTraceContext implements TraceContext {

  private static final String MDC_TRACE_ID = "traceId";
  private static final String MDC_SPAN_ID = "spanId";

  /** 构造函数，初始化默认追踪上下文（降级模式） */
  /**
   * default trace context。
 */
  public DefaultTraceContext() {
    log.info("[Sentry] DefaultTraceContext 初始化完成（降级模式, UUID TraceId）");
  }

  /**
   * 获取当前 TraceId，若 MDC 中不存在则自动生成 UUID
   *
   * @return TraceId
   */
  @Override
  /**
   * get trace id。
   * @return 结果
   */
  public String getTraceId() {
    String traceId = getMdcValue(MDC_TRACE_ID);
    if (traceId == null || traceId.isEmpty()) {
      traceId = generateTraceId();
      setMdcValue(MDC_TRACE_ID, traceId);
    }
    return traceId;
  }

  /**
   * 获取当前 SpanId，若 MDC 中不存在则自动生成 UUID
   *
   * @return SpanId
   */
  @Override
  /**
   * get span id。
   * @return 结果
   */
  public String getSpanId() {
    String spanId = getMdcValue(MDC_SPAN_ID);
    if (spanId == null || spanId.isEmpty()) {
      spanId = generateSpanId();
      setMdcValue(MDC_SPAN_ID, spanId);
    }
    return spanId;
  }

  /**
   * 判断是否在追踪链路中
   *
   * @return MDC 中存在 traceId 则返回 true
   */
  @Override
  /**
   * is tracing。
   * @return 结果
   */
  public boolean isTracing() {
    String traceId = getMdcValue(MDC_TRACE_ID);
    return traceId != null && !traceId.isEmpty();
  }

  /**
   * 注入自定义标签，通过 MDC 传递
   *
   * @param key 标签键
   * @param value 标签值
   */
  @Override
  /**
   * tag。
   * @param key 参数
   * @param value 参数
   */
  public void tag(String key, String value) {
    // 降级方案：通过 MDC 传递标签
    setMdcValue("tag_" + key, value);
  }

  /**
   * 获取追踪系统名称
   *
   * @return 固定返回 "default-uuid"
   */
  @Override
  /**
   * get tracer name。
   * @return 结果
   */
  public String getTracerName() {
    return "default-uuid";
  }

  /** 生成 TraceId */
  /**
   * generate trace id。
   * @return 结果
   */
  public static String generateTraceId() {
    return IdGenerator.nextIdStr();
  }

  /** 生成 SpanId */
  /**
   * generate span id。
   * @return 结果
   */
  public static String generateSpanId() {
    return IdGenerator.nextIdStr().substring(0, 16);
  }

  /** 获取 MDC 值 */
  private String getMdcValue(String key) {
    try {
      return MDC.get(key);
    } catch (Exception e) {
      return null;
    }
  }

  /** 设置 MDC 值 */
  private void setMdcValue(String key, String value) {
    try {
      MDC.put(key, value);
    } catch (Exception e) {
      log.debug("[Sentry] MDC put 失败: key={}, err={}", key, e.getMessage());
    }
  }
}
