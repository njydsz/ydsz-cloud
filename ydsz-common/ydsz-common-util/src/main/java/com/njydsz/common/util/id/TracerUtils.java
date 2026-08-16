package com.njydsz.common.util.id;

import com.njydsz.common.util.internal.proxy.CoreConstants;
import com.njydsz.common.util.internal.proxy.ParsedTraceparent;
import com.njydsz.common.util.internal.proxy.RequestContextProxy;
import com.njydsz.common.util.internal.proxy.TraceIdGeneratorProxy;
import com.njydsz.common.util.string.StringUtils;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 链路追踪工具类
 *
 * <p>参考 SkyWalking、Zipkin、Sleuth 等链路追踪规范实现。 支持多种 Trace ID 生成策略和上下文管理，是分布式调用链追踪的基础工具。
 *
 * <p><b>核心特性：</b>
 *
 * <ul>
 *   <li>支持 SkyWalking {@code TraceContext} 集成（反射调用，无编译期硬依赖）
 *   <li>支持 SLF4J MDC 上下文注入
 *   <li>支持 {@link TraceIdGeneratorProxy#generateSortableTraceId()} 生成时间有序 TraceId
 *   <li>支持 Span ID 父子关系管理
 *   <li>支持线程间链路追踪上下文传递
 *   <li>支持 W3C TraceContext {@code traceparent} 头的解析与注入（v4.2.0+）
 * </ul>
 *
 * <p><b>统一上下文：</b>自 v2.0.0 起，traceId 读写统一收口至 {@link RequestContextProxy} （底层桥接 ydsz-common-core 的
 * RequestContext），MDC 仅作为日志桥接双写， 保证业务代码读取 {@link RequestContextProxy#getTraceId()} 与日志输出保持一致。
 *
 * <p><b>线程安全性：</b>所有方法均为静态无状态，线程安全。 实际状态存储于 SLF4J {@link MDC} 与 {@link
 * RequestContextProxy}，由调用方保证清理。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 在拦截器中获取或创建 Trace ID
 * String traceId = TracerUtils.getOrCreateTraceId();
 *
 * // 创建子 Span
 * String spanId = TracerUtils.createNewSpan();
 *
 * // 在异步线程中传递追踪上下文
 * TracerUtils.runWithTrace(traceId, () -> {
 *     // 业务逻辑
 * });
 *
 * // 从入站 HTTP 请求头注入 W3C traceparent
 * boolean injected = TracerUtils.injectTraceparent(request.getHeader("traceparent"));
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RequestContextProxy
 */
public final class TracerUtils {

  private static final Logger log = LoggerFactory.getLogger(TracerUtils.class);

  private static final String TRACE_ID_NAME = "traceId";
  private static final String SPAN_ID_NAME = "spanId";
  private static final String PARENT_SPAN_ID_NAME = "parentSpanId";

  /** SkyWalking TraceContext 类名（反射调用，无编译期硬依赖） */
  private static final String SKYWALKING_TRACE_CONTEXT_CLASS =
      "org.apache.skywalking.apm.toolkit.trace.TraceContext";

  /** SkyWalking TraceContext.traceId() 反射缓存 */
  private static volatile Method skywalkingTraceIdMethod;

  private static volatile boolean skywalkingChecked = false;
  private static volatile boolean skywalkingAvailable = false;

  private TracerUtils() {
    throw new UnsupportedOperationException("Utility class should not be instantiated");
  }

  /**
   * 反射调用 SkyWalking TraceContext.traceId()
   *
   * <p>使用反射避免对 SkyWalking toolkit 的编译期硬依赖。 当 SkyWalking 不在 classpath 时，返回 null。 反射结果缓存到 volatile
   * 字段，避免每次调用都进行类加载检查。
   *
   * @return SkyWalking traceId，或 null（不可用时）
   */
  private static String getSkyWalkingTraceId() {
    if (!skywalkingChecked) {
      synchronized (TracerUtils.class) {
        if (!skywalkingChecked) {
          try {
            Class<?> clazz = Class.forName(SKYWALKING_TRACE_CONTEXT_CLASS);
            skywalkingTraceIdMethod = clazz.getMethod("traceId");
            skywalkingAvailable = true;
          } catch (ClassNotFoundException | NoSuchMethodException e) {
            skywalkingAvailable = false;
            log.debug("SkyWalking TraceContext not available, trace ID will fallback to MDC only");
          }
          skywalkingChecked = true;
        }
      }
    }
    if (!skywalkingAvailable || skywalkingTraceIdMethod == null) {
      return null;
    }
    try {
      return (String) skywalkingTraceIdMethod.invoke(null);
    } catch (Exception e) {
      log.debug("SkyWalking TraceContext.traceId() invocation failed: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 获取链路追踪编号。 1. 首先尝试获取 SkyWalking 的 TraceId（反射调用，无硬依赖）。 2. 如果不存在，则尝试从 {@link RequestContextProxy}
   * 中获取（统一上下文主源）。 3. 如果都不存在，则尝试从 MDC 中获取（兼容非 Web / 旧逻辑场景）。 4. 如果都不存在，则返回空字符串。
   *
   * @return 链路追踪编号
   */
  public static String getTraceId() {
    String traceId = getSkyWalkingTraceId();
    if (StringUtils.isNotEmpty(traceId) && !"Ignored_Trace".equalsIgnoreCase(traceId)) {
      return traceId;
    }

    traceId = RequestContextProxy.getTraceId();
    if (StringUtils.isNotEmpty(traceId)) {
      return traceId;
    }

    traceId = MDC.get(TRACE_ID_NAME);
    return traceId == null ? "" : traceId;
  }

  /**
   * 获取或创建 Trace ID（如果不存在则自动生成）
   *
   * @return Trace ID
   */
  public static String getOrCreateTraceId() {
    String traceId = getTraceId();
    if (StringUtils.isEmpty(traceId)) {
      traceId = generateTraceId();
      setTraceId(traceId);
    }
    return traceId;
  }

  /**
   * 生成新的 Trace ID（委托 {@link TraceIdGeneratorProxy}）。
   *
   * <p>core 层基于 {@code ThreadLocalRandom + HexFormat} 实现，无锁且性能 优于 UUID v7（约 2.5x），输出格式同为 32 位十六进制，与
   * W3C TraceContext 等主流规范兼容，下游无需感知差异。
   *
   * @return 新生成的 Trace ID（32 位十六进制）
   */
  public static String generateTraceId() {
    return TraceIdGeneratorProxy.generateSortableTraceId();
  }

  /**
   * 将 Trace ID 注入到 MDC 与 {@link RequestContextProxy}（双写，保证统一上下文一致）
   *
   * @param traceId Trace ID
   */
  public static void setTraceId(String traceId) {
    if (StringUtils.isNotEmpty(traceId)) {
      MDC.put(TRACE_ID_NAME, traceId);
      RequestContextProxy.setTraceId(traceId);
    }
  }

  /**
   * 获取 Span ID
   *
   * @return Span ID
   */
  public static String getSpanId() {
    String spanId = MDC.get(SPAN_ID_NAME);
    return spanId == null ? "" : spanId;
  }

  /**
   * 设置 Span ID
   *
   * @param spanId Span ID
   */
  public static void setSpanId(String spanId) {
    if (StringUtils.isNotEmpty(spanId)) {
      MDC.put(SPAN_ID_NAME, spanId);
    }
  }

  /**
   * 获取 Parent Span ID
   *
   * @return Parent Span ID
   */
  public static String getParentSpanId() {
    String parentSpanId = MDC.get(PARENT_SPAN_ID_NAME);
    return parentSpanId == null ? "" : parentSpanId;
  }

  /**
   * 设置 Parent Span ID
   *
   * @param parentSpanId Parent Span ID
   */
  public static void setParentSpanId(String parentSpanId) {
    if (StringUtils.isNotEmpty(parentSpanId)) {
      MDC.put(PARENT_SPAN_ID_NAME, parentSpanId);
    }
  }

  /**
   * 创建新的 Span
   *
   * <p>自动将当前 Span ID 设置为 Parent Span ID， 并生成新的 Span ID。
   *
   * @return 新的 Span ID
   */
  public static String createNewSpan() {
    String currentSpanId = getSpanId();
    if (StringUtils.isNotEmpty(currentSpanId)) {
      setParentSpanId(currentSpanId);
    }
    String newSpanId = generateSpanId();
    setSpanId(newSpanId);
    return newSpanId;
  }

  /**
   * 生成新的 Span ID
   *
   * <p>生成 16 位十六进制字符串（64 位），与 W3C Trace Context、B3、SkyWalking 等 主流链路追踪规范的 Span ID 格式一致。4 位数字 Span
   * ID 仅 10,000 种取值， 在高并发场景下碰撞概率极高，无法满足分布式追踪需求。
   *
   * @return 16 位十六进制 Span ID
   */
  public static String generateSpanId() {
    return TraceIdGeneratorProxy.generateSpanId();
  }

  /**
   * 获取完整的追踪上下文信息
   *
   * @return 追踪上下文信息字符串
   */
  public static String getTraceContext() {
    String traceId = getTraceId();
    String spanId = getSpanId();
    String parentSpanId = getParentSpanId();

    StringBuilder sb = new StringBuilder();
    sb.append("traceId=").append(traceId);
    if (StringUtils.isNotEmpty(spanId)) {
      sb.append(", spanId=").append(spanId);
    }
    if (StringUtils.isNotEmpty(parentSpanId)) {
      sb.append(", parentSpanId=").append(parentSpanId);
    }
    return sb.toString();
  }

  /** 清理 MDC 与 {@link RequestContextProxy} 中的 Trace ID */
  public static void clear() {
    MDC.remove(TRACE_ID_NAME);
    RequestContextProxy.remove(CoreConstants.MDC_TRACE_ID_KEY);
  }

  /**
   * 清理所有追踪上下文（Trace ID、Span ID、Parent Span ID）
   *
   * <p>Trace ID 同时清理 {@link RequestContextProxy}，Span ID / Parent Span ID 仅清理 MDC （Span
   * 属日志级诊断信息，不进入统一请求上下文）。
   */
  public static void clearAll() {
    MDC.remove(TRACE_ID_NAME);
    MDC.remove(SPAN_ID_NAME);
    MDC.remove(PARENT_SPAN_ID_NAME);
    RequestContextProxy.remove(CoreConstants.MDC_TRACE_ID_KEY);
  }

  /**
   * 检查是否存在有效的 Trace ID
   *
   * @return 是否存在有效的 Trace ID
   */
  public static boolean hasValidTraceId() {
    String traceId = getTraceId();
    return StringUtils.isNotEmpty(traceId) && !"Ignored_Trace".equalsIgnoreCase(traceId);
  }

  /**
   * 执行带追踪上下文的可运行对象
   *
   * <p>自动设置和清理追踪上下文。执行完成后恢复原有的 traceId、spanId、parentSpanId， 避免被调用方在 runnable 内创建子 Span 后污染调用方上下文。
   *
   * @param traceId Trace ID
   * @param runnable 要执行的可运行对象
   */
  public static void runWithTrace(String traceId, Runnable runnable) {
    String originalTraceId = getTraceId();
    String originalSpanId = getSpanId();
    String originalParentSpanId = getParentSpanId();
    try {
      setTraceId(traceId);
      runnable.run();
    } finally {
      if (StringUtils.isNotEmpty(originalTraceId)) {
        setTraceId(originalTraceId);
      } else {
        MDC.remove(TRACE_ID_NAME);
        RequestContextProxy.remove(CoreConstants.MDC_TRACE_ID_KEY);
      }
      if (StringUtils.isNotEmpty(originalSpanId)) {
        MDC.put(SPAN_ID_NAME, originalSpanId);
      } else {
        MDC.remove(SPAN_ID_NAME);
      }
      if (StringUtils.isNotEmpty(originalParentSpanId)) {
        MDC.put(PARENT_SPAN_ID_NAME, originalParentSpanId);
      } else {
        MDC.remove(PARENT_SPAN_ID_NAME);
      }
    }
  }

  /**
   * 执行带追踪上下文的可运行对象（自动生成 Trace ID）
   *
   * @param runnable 要执行的可运行对象
   */
  public static void runWithTrace(Runnable runnable) {
    String traceId = generateTraceId();
    runWithTrace(traceId, runnable);
  }

  // ==================== W3C TraceContext traceparent 编解码 ====================

  /**
   * 解析 W3C {@code traceparent} 头并注入到当前线程的 MDC 与 {@link RequestContextProxy}。
   *
   * <p>用于入口拦截器接收上游 W3C 格式的 {@code traceparent} header 后恢复链路上下文。 注入成功后，{@link #getTraceId()} 与
   * {@link #getSpanId()} 可直接读取解析结果。
   *
   * <p>格式：{@code 00-{32hex}-{16hex}-{flags}}，非法格式返回 {@code false}。
   *
   * @param traceparent W3C traceparent 字符串（如 {@code
   *     00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}）
   * @return {@code true} 表示注入成功；格式非法返回 {@code false}
   * @since 4.2.0
   */
  public static boolean injectTraceparent(String traceparent) {
    ParsedTraceparent parsed = TraceIdGeneratorProxy.parseTraceparent(traceparent);
    if (parsed == null) {
      return false;
    }
    setTraceId(parsed.traceId());
    setSpanId(parsed.spanId());
    return true;
  }

  /**
   * 解析 W3C {@code traceparent} 头。
   *
   * <p>仅解析、不注入上下文，适用于需要对解析结果做自定义处理的场景。
   *
   * @param traceparent W3C traceparent 字符串
   * @return 解析结果；格式非法返回 null
   * @since 4.2.0
   */
  public static ParsedTraceparent parseTraceparent(String traceparent) {
    return TraceIdGeneratorProxy.parseTraceparent(traceparent);
  }

  /**
   * 获取当前 W3C {@code traceparent} 字符串。
   *
   * <p>基于当前线程的 Trace ID 和 Span ID 构造 W3C 格式的 traceparent 头， 用于跨服务调用时透传链路追踪上下文。
   *
   * <p>格式：{@code 00-{32hex}-{16hex}-{flags}}。
   *
   * @return W3C traceparent 字符串；如果没有有效的 Trace ID 则返回空字符串
   * @since 4.2.0
   */
  public static String getCurrentTraceParent() {
    String traceId = getTraceId();
    if (StringUtils.isEmpty(traceId) || "Ignored_Trace".equalsIgnoreCase(traceId)) {
      return "";
    }
    String spanId = getSpanId();
    if (StringUtils.isEmpty(spanId)) {
      spanId = generateSpanId();
      setSpanId(spanId);
    }
    // 规范化 traceId 为 32 位十六进制
    if (traceId.length() > 32) {
      traceId = traceId.substring(traceId.length() - 32);
    } else if (traceId.length() < 32) {
      traceId = "0".repeat(32 - traceId.length()) + traceId;
    }
    // 规范化 spanId 为 16 位十六进制
    if (spanId.length() > 16) {
      spanId = spanId.substring(spanId.length() - 16);
    } else if (spanId.length() < 16) {
      spanId = "0".repeat(16 - spanId.length()) + spanId;
    }
    return "00-" + traceId + "-" + spanId + "-01";
  }
}
