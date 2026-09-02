package com.njydsz.common.queue.trace;

import org.slf4j.MDC;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.util.id.TracerUtils;

/**
 * 消息链路追踪上下文管理器
 *
 * <p>基于 SLF4J MDC 和 RequestContext 实现 traceId 的注入、提取和清理， 用于在消息生产/消费全链路中传递追踪标识，便于问题排查和日志关联。
 *
 * <p>集成说明：
 *
 * <ul>
 *   <li>MDC: 用于日志框架自动注入 traceId 到日志输出
 *   <li>RequestContext: 用于跨线程上下文传递，支持线程池场景
 * </ul>
 *
 * <p>支持两种使用模式：
 *
 * <ul>
 *   <li><b>静态方法</b>：直接注入/提取/清理 traceId（兼容旧 API）
 *   <li><b>try-with-resources</b>：进入上下文时设置 traceId，退出时自动恢复/清除
 * </ul>
 *
 * <p>典型用法：
 *
 * <pre>{@code
 * // 生产端：注入 traceId
 * MessageTracer.injectTraceId(traceId);
 *
 * // 消费端（try-with-resources 模式）：
 * try (MessageTraceScope scope = MessageTracer.enter(request.getMessageId())) {
 *     // 此作用域内 traceId 已设置
 *     messageService.send(request);
 * }
 * // 退出后 MDC 自动恢复/清除
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class MessageTracer {

  private MessageTracer() {}

  private static final String TRACE_ID_KEY = "traceId";

  // ==================== 静态方法（兼容旧 API） ====================

  /**
   * 注入 traceId 到当前线程的 MDC 和 RequestContext 上下文中
   *
   * @param traceId 链路追踪ID，为 null 或空字符串时忽略
   */
  public static void injectTraceId(String traceId) {
    if (traceId != null && !traceId.isEmpty()) {
      MDC.put(TRACE_ID_KEY, traceId);
      RequestContext.setTraceId(traceId);
    }
  }

  /**
   * 从当前线程的上下文中提取 traceId
   *
   * <p>优先从 RequestContext 获取，其次从 MDC 获取
   *
   * @return 链路追踪ID，未设置时返回 null
   */
  public static String extractTraceId() {
    // 优先从 RequestContext 获取（支持跨线程传递）
    String traceId = RequestContext.getTraceId();
    if (traceId != null && !traceId.isEmpty()) {
      return traceId;
    }
    // 回退到 MDC
    return MDC.get(TRACE_ID_KEY);
  }

  /**
   * 清除当前线程上下文中的 traceId
   *
   * <p>注意：RequestContext 中的 traceId 仅在有上层管理时才清除。 静态清理方法会同时清理 MDC 和 RequestContext。
   */
  public static void clearTraceId() {
    MDC.remove(TRACE_ID_KEY);
    RequestContext.remove(RequestContext.KEY_TRACE_ID);
  }

  // ==================== try-with-resources 模式 ====================

  /**
   * 进入追踪上下文：将 traceId 写入 MDC 与 RequestContext。
   *
   * <p>退出时自动恢复进入前的 traceId 或清除（无原始 traceId 时）。 traceId 为 null / 空白时自动生成新 traceId。
   *
   * @param traceId 待设置的 traceId；为 null / 空白时自动生成
   * @return 追踪作用域实例（try-with-resources 自动清理）
   */
  public static MessageTraceScope enter(String traceId) {
    String previous = MDC.get(TRACE_ID_KEY);
    if (traceId == null || traceId.isBlank()) {
      TracerUtils.getOrCreateTraceId();
    } else {
      TracerUtils.setTraceId(traceId);
    }
    return new MessageTraceScope(previous);
  }

  /**
   * 进入追踪上下文（自动生成 traceId）。
   *
   * @return 追踪作用域实例
   */
  public static MessageTraceScope enter() {
    return enter(null);
  }

  // ==================== 内部作用域类 ====================

  /**
   * 追踪作用域（try-with-resources 自动管理 MDC traceId 生命周期）。
   *
   * <p>进入时保存当前 traceId 并设置新 traceId， 退出时恢复原 traceId 或清除。
   */
  public static final class MessageTraceScope implements AutoCloseable {

    /** 进入前的 MDC traceId，用于退出时恢复 */
    private final String previousTraceId;

    private MessageTraceScope(String previousTraceId) {
      this.previousTraceId = previousTraceId;
    }

    @Override
    public void close() {
      if (previousTraceId != null && !previousTraceId.isEmpty()) {
        TracerUtils.setTraceId(previousTraceId);
      } else {
        TracerUtils.clear();
      }
    }
  }
}
