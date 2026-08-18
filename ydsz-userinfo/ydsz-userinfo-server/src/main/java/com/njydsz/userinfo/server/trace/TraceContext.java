package com.njydsz.userinfo.server.trace;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * 链路追踪上下文（P1-10）。
 *
 * <p>轻量级 traceId 贯穿实现：从请求头 {@code X-Trace-Id} 读取上游 traceId（网关/Sleuth 传入），
 * 不存在则本地生成 UUID。traceId 同步写入 SLF4J MDC 与 {@link ThreadLocal}，
 * 使日志输出与指标标签均可关联同一链路。
 *
 * <p><b>与云顶规范的关系：</b>common 层暂未提供 {@code ydsz-common-trace} 组件，
 * 本实现为过渡方案；common-trace 就绪后应迁移到统一组件，禁止各模块重复实现。
 *
 * <p><b>注意：</b>异步线程不自动传递 ThreadLocal，需要异步场景时由调用方在
 * 子线程入口显式调用 {@link #setTraceId(String)}（或接入线程池 TaskDecorator）。
 *
 * @author ydsz-team
 * @since 2.21.0
 */
public final class TraceContext {

  /** MDC / ThreadLocal 中 traceId 的键名 */
  public static final String TRACE_ID_KEY = "traceId";

  /** 请求头名称 */
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

  private TraceContext() {
    throw new UnsupportedOperationException("TraceContext is a utility class");
  }

  /**
   * 获取当前线程 traceId；不存在时返回 null。
   *
   * @return traceId 或 null
   */
  public static String getTraceId() {
    String traceId = TRACE_ID_HOLDER.get();
    if (traceId == null || traceId.isBlank()) {
      traceId = MDC.get(TRACE_ID_KEY);
    }
    return traceId;
  }

  /**
   * 设置当前线程 traceId（同步写入 MDC）。
   *
   * @param traceId traceId；null 或空白时清除
   */
  public static void setTraceId(String traceId) {
    if (traceId == null || traceId.isBlank()) {
      clear();
      return;
    }
    TRACE_ID_HOLDER.set(traceId);
    MDC.put(TRACE_ID_KEY, traceId);
  }

  /**
   * 获取或生成 traceId：优先使用传入值，否则生成 UUID。
   *
   * @param incoming 上游传入的 traceId（可为 null）
   * @return 最终使用的 traceId
   */
  public static String getOrGenerate(String incoming) {
    if (incoming != null && !incoming.isBlank()) {
      return incoming.trim();
    }
    return UUID.randomUUID().toString().replace("-", "");
  }

  /** 清除当前线程 traceId（请求结束时调用，防止线程池复用串号）。 */
  public static void clear() {
    TRACE_ID_HOLDER.remove();
    MDC.remove(TRACE_ID_KEY);
  }
}
