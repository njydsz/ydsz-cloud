package com.njydsz.common.notify.core;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.notify.enums.NotifyChannel;

/**
 * 通知链路追踪辅助工具（P1-1）
 *
 * <p>提供 traceId 在通知发送全链路中的传递能力。 从 {@link RequestContext} / MDC 中获取当前 traceId，注入到通知请求中，便于跨服务问题排查。
 *
 * <p><b>统一上下文：</b>traceId 读写统一收口至 {@link RequestContext}（统一上下文主源）， MDC 仅作为日志桥接双写，保证业务代码与日志链路一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class NotifyTraceContext {

  private static final Logger LOG = LoggerFactory.getLogger(NotifyTraceContext.class);

  /** MDC 中 traceId 的键名 */
  public static final String TRACE_ID_KEY = "traceId";

  /** MDC 中 spanId 的键名 */
  public static final String SPAN_ID_KEY = "spanId";

  private NotifyTraceContext() {}

  /**
   * 获取当前 traceId
   *
   * <p>优先从 {@link RequestContext} 读取，未命中回退 MDC（兼容旧逻辑）。
   *
   * @return traceId，不存在时返回 null
   */
  public static String getTraceId() {
    String traceId = RequestContext.getTraceId();
    if (traceId != null && !traceId.isEmpty()) {
      return traceId;
    }
    return MDC.get(TRACE_ID_KEY);
  }

  /**
   * 获取当前 MDC 中的 spanId
   *
   * @return spanId，不存在时返回 null
   */
  public static String getSpanId() {
    return MDC.get(SPAN_ID_KEY);
  }

  /**
   * 设置 traceId 到 {@link RequestContext} 与 MDC（双写）
   *
   * @param traceId 链路追踪ID
   */
  public static void setTraceId(String traceId) {
    if (traceId != null && !traceId.isEmpty()) {
      RequestContext.setTraceId(traceId);
      MDC.put(TRACE_ID_KEY, traceId);
    }
  }

  /** 从 {@link RequestContext} 与 MDC 中移除 traceId */
  public static void clearTraceId() {
    RequestContext.remove(RequestContext.KEY_TRACE_ID);
    MDC.remove(TRACE_ID_KEY);
  }

  /**
   * 构建带追踪信息的日志前缀
   *
   * @param channel 通知渠道
   * @return 日志前缀字符串
   */
  public static String buildLogPrefix(NotifyChannel channel) {
    String traceId = getTraceId();
    if (traceId != null) {
      return "[traceId=" + traceId + ", channel=" + channel.getName() + "]";
    }
    return "[channel=" + channel.getName() + "]";
  }

  /**
   * 在指定 traceId 上下文中执行操作
   *
   * <p>执行完毕后自动清除 traceId，避免线程池线程复用导致的 traceId 泄漏。
   *
   * @param traceId 链路追踪ID
   * @param runnable 要执行的操作
   */
  public static void runWithTrace(String traceId, Runnable runnable) {
    String previousTraceId = getTraceId();
    try {
      setTraceId(traceId);
      runnable.run();
    } finally {
      if (previousTraceId != null) {
        setTraceId(previousTraceId);
      } else {
        clearTraceId();
      }
    }
  }

  /**
   * 在指定 traceId 上下文中执行有返回值的操作
   *
   * <p>执行完毕后自动恢复原 traceId，避免线程池线程复用导致的 traceId 泄漏。
   *
   * @param traceId 链路追踪ID
   * @param supplier 要执行的操作（有返回值）
   * @param <T> 返回值类型
   * @return 操作返回值
   */
  public static <T> T runWithTraceResult(String traceId, Supplier<T> supplier) {
    String previousTraceId = getTraceId();
    try {
      setTraceId(traceId);
      return supplier.get();
    } finally {
      if (previousTraceId != null) {
        setTraceId(previousTraceId);
      } else {
        clearTraceId();
      }
    }
  }

  /**
   * 记录通知发送追踪日志
   *
   * @param channel 通知渠道
   * @param receiver 接收者
   * @param success 是否成功
   * @param durationMs 耗时（毫秒）
   */
  public static void logTrace(
      NotifyChannel channel, String receiver, boolean success, long durationMs) {
    String traceId = getTraceId();
    if (success) {
      LOG.info(
          "[NotifyTrace] 通知发送成功: traceId={}, channel={}, receiver={}, durationMs={}",
          traceId,
          channel.getName(),
          receiver,
          durationMs);
    } else {
      LOG.warn(
          "[NotifyTrace] 通知发送失败: traceId={}, channel={}, receiver={}, durationMs={}",
          traceId,
          channel.getName(),
          receiver,
          durationMs);
    }
  }
}
