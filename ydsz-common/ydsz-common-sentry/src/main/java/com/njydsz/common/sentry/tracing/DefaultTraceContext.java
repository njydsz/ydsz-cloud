package com.njydsz.common.sentry.tracing;

import java.security.SecureRandom;
import java.util.HexFormat;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import com.njydsz.common.sentry.spi.TraceContext;

/**
 * 默认追踪上下文（降级方案）
 *
 * <p>当 OpenTelemetry SDK 和 SkyWalking Agent 均不可用时作为最终降级方案。
 *
 * <p>生成符合 W3C Trace Context 标准的 32 位十六进制 traceId 和 16 位十六进制 spanId，通过 MDC 传递，与网关 {@code
 * TraceIdGenerator.generateW3CTraceId()} 对齐。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
public class DefaultTraceContext implements TraceContext {

  private static final String MDC_TRACE_ID = "traceId";
  private static final String MDC_SPAN_ID = "spanId";

  /** W3C TraceId 字节长度（16 bytes = 128 bit = 32 hex 字符） */
  private static final int TRACE_ID_BYTES = 16;

  /** W3C SpanId 字节长度（8 bytes = 64 bit = 16 hex 字符） */
  private static final int SPAN_ID_BYTES = 8;

  /** 密码学安全随机数生成器（线程安全，可重用） */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** 共享的 HexFormat 实例（线程安全，可重用） */
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  /** 构造函数，初始化默认追踪上下文（降级模式，W3C 32 位十六进制 TraceId） */
  public DefaultTraceContext() {
    log.info("[Sentry] DefaultTraceContext 初始化完成（降级模式, W3C 32-hex TraceId）");
  }

  /**
   * 获取当前 TraceId，若 MDC 中不存在则自动生成 W3C 格式的 32 位十六进制 TraceId
   *
   * @return TraceId（32 位小写十六进制字符串）
   */
  @Override
  public String getTraceId() {
    String traceId = getMdcValue(MDC_TRACE_ID);
    if (traceId == null || traceId.isEmpty()) {
      traceId = generateTraceId();
      setMdcValue(MDC_TRACE_ID, traceId);
    }
    return traceId;
  }

  /**
   * 获取当前 SpanId，若 MDC 中不存在则自动生成 W3C 格式的 16 位十六进制 SpanId
   *
   * @return SpanId（16 位小写十六进制字符串）
   */
  @Override
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
  public void tag(String key, String value) {
    // 降级方案：通过 MDC 传递标签
    setMdcValue("tag_" + key, value);
  }

  /**
   * 获取追踪系统名称
   *
   * @return 固定返回 "default-w3c"
   */
  @Override
  public String getTracerName() {
    return "default-w3c";
  }

  /**
   * 生成符合 W3C Trace Context 标准的 32 位十六进制 TraceId。
   *
   * <p>使用 {@link SecureRandom} 生成 128 bit 密码学安全随机数，格式化为 32 位小写 hex。碰撞概率约 2^-128。
   *
   * @return 32 位小写十六进制字符串
   */
  public static String generateTraceId() {
    byte[] bytes = new byte[TRACE_ID_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return HEX_FORMAT.formatHex(bytes);
  }

  /**
   * 生成符合 W3C Trace Context 标准的 16 位十六进制 SpanId。
   *
   * <p>使用 {@link SecureRandom} 生成 64 bit 密码学安全随机数，格式化为 16 位小写 hex。
   *
   * @return 16 位小写十六进制字符串
   */
  public static String generateSpanId() {
    byte[] bytes = new byte[SPAN_ID_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return HEX_FORMAT.formatHex(bytes);
  }

  /** 获取 MDC 值 */
  private String getMdcValue(String key) {
    try {
      return MDC.get(key);
    } catch (Exception e) {
      log.debug("[Sentry] MDC get 失败: key={}, err={}", key, e.getMessage());
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
