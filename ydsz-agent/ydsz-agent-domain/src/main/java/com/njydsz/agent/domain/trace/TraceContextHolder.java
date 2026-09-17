package com.njydsz.agent.domain.trace;

/**
 * 链路上下文持有者
 *
 * <p>基于 ThreadLocal 传递当前请求的业务关联维度（botId/turnId/conversationId/accountId）。
 * 在请求入口设置，在 Trace/Metrics 记录时消费。
 *
 * <p>典型用法：
 * <pre>{@code
 * try {
 *     TraceContextHolder.set(new TraceContextHolder.TraceContext(botId, turnId, conversationId, accountId));
 *     // ... 业务处理 ...
 * } finally {
 *     TraceContextHolder.clear();
 * }
 * }</pre>
 *
 * <p><b>注意</b>：必须在请求处理完成后调用 {@link #clear()}，通常在 finally 块中，
 * 以避免线程池复用导致的上下文泄漏。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public final class TraceContextHolder {

  /** 工具类禁止实例化 */
  private TraceContextHolder() {
    throw new UnsupportedOperationException("工具类禁止实例化");
  }

  /** ThreadLocal 存储链路上下文（clear() 方法提供 remove() 语义，确保线程池环境安全） */
  private static final ThreadLocal<TraceContext> CONTEXT = new ThreadLocal<>();

  /**
   * 当前请求的业务关联维度上下文。
   *
   * @param botId 关联的 Agent 定义 ID
   * @param turnId 对话轮次 ID
   * @param conversationId 会话 ID
   * @param accountId 账号/用户 ID
   */
  public record TraceContext(String botId, String turnId, String conversationId, String accountId) {}

  /**
   * 设置当前线程的链路上下文。
   *
   * @param ctx 链路上下文，可为 null（等同于清除）
   */
  public static void set(TraceContext ctx) {
    CONTEXT.set(ctx);
  }

  /**
   * 获取当前线程的链路上下文（可能为 null）。
   *
   * @return 当前线程的链路上下文，未设置时返回 null
   */
  public static TraceContext get() {
    return CONTEXT.get();
  }

  /**
   * 清除当前线程的链路上下文（必须在 finally 中调用）。
   *
   * <p>在线程池环境中，不清除会导致后续请求污染到前一请求的业务维度。
   */
  public static void clear() {
    CONTEXT.remove();
  }
}
