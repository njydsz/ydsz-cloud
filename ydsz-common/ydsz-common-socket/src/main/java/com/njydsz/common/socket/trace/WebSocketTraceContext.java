package com.njydsz.common.socket.trace;

import org.slf4j.MDC;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.util.id.IdGenerator;

/**
 * WebSocket 链路追踪辅助工具。
 *
 * <p>提供 traceId 在 WebSocket 推送全链路中的传递能力。 从 {@link RequestContext} / MDC 中获取当前 traceId，注入到集群广播消息中，
 * 订阅端收到消息后恢复上下文，实现跨节点链路关联。
 *
 * <p><b>统一上下文：</b>traceId 读写统一收口至 {@link RequestContext}（统一上下文主源）， MDC 仅作为日志桥接双写，保证业务代码与日志链路一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class WebSocketTraceContext {

  /** MDC 中 traceId 的键名 */
  public static final String TRACE_ID_KEY = "traceId";

  private WebSocketTraceContext() {}

  /**
   * 获取当前 traceId，不存在时生成新的。
   *
   * <p>优先读取 {@link RequestContext}，未命中回退 MDC；生成后双写写入。
   *
   * @return traceId
   */
  public static String getOrGenerateTraceId() {
    String traceId = getTraceId();
    if (traceId == null || traceId.isEmpty()) {
      traceId = generateTraceId();
      setTraceId(traceId);
    }
    return traceId;
  }

  /**
   * 获取当前 traceId。
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
   * 生成新的 traceId。
   *
   * @return 全局唯一 traceId
   */
  public static String generateTraceId() {
    return IdGenerator.nextIdStr();
  }

  /**
   * 设置 traceId 到 {@link RequestContext} 与 MDC（双写）。
   *
   * @param traceId 链路追踪 ID
   */
  public static void setTraceId(String traceId) {
    if (traceId != null && !traceId.isEmpty()) {
      RequestContext.setTraceId(traceId);
      MDC.put(TRACE_ID_KEY, traceId);
    }
  }

  /** 从 {@link RequestContext} 与 MDC 中移除 traceId。 */
  public static void clearTraceId() {
    RequestContext.remove(RequestContext.KEY_TRACE_ID);
    MDC.remove(TRACE_ID_KEY);
  }

  /**
   * 在指定 traceId 上下文中执行操作，执行完毕后自动恢复原 traceId。
   *
   * @param traceId 链路追踪 ID
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
}
