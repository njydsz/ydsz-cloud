package com.njydsz.agent.domain.trace;

import com.njydsz.common.core.context.RequestContext;

/**
 * 链路上下文持有者。
 *
 * <p>已收敛为公共 {@link RequestContext} 的委托器，不再自行持有 ThreadLocal，所有读写都经
 * {@link RequestContext#setBotId(String)} / {@link RequestContext#getBotId()} 等强类型存取器完成。
 *
 * <p>收敛动机：
 *
 * <ul>
 *   <li>与请求级 {@link RequestContext} 生命周期一致，{@code RequestContext.clear()} 自动兜底清理，
 *       消除原 ThreadLocal 在异步/线程池场景下未清理导致的上下文泄漏
 *   <li>复用 TransmittableThreadLocal 的线程池传播能力，避免自实现未 capture/restore 的链路断裂
 * </ul>
 *
 * <p>典型用法（保持不变）：
 *
 * <pre>{@code
 * try {
 *     TraceContextHolder.set(new TraceContextHolder.TraceContext(botId, turnId, conversationId, accountId));
 *     // ... 业务处理 ...
 * } finally {
 *     TraceContextHolder.clear();
 * }
 * }</pre>
 *
 * <p>新代码应优先直接调用 {@link RequestContext#setBotId(String)} / {@link RequestContext#setTurnId(String)} /
 * {@link RequestContext#setConversationId(String)} / {@link RequestContext#setAccountId(String)}，
 * 本持有器仅作为存量调用方的兼容适配。
 *
 * @author ydsz-team
 * @since 26.09.17
 * @deprecated 26.09.27 仅保留为适配层，新代码直接读写 {@link RequestContext}
 */
@Deprecated
public final class TraceContextHolder {

  /** 工具类禁止实例化 */
  private TraceContextHolder() {
    throw new UnsupportedOperationException("agent.error.util_class_instantiation");
  }

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
   * <p>写入 {@link RequestContext} 的四个 Agent 业务维度，支持 {@code null}（等同于清除对应维度）。
   *
   * @param ctx 链路上下文，可为 null（等同于清除）
   */
  public static void set(TraceContext ctx) {
    if (ctx == null) {
      RequestContext.clearAgentDimensions();
      return;
    }
    RequestContext.setBotId(ctx.botId());
    RequestContext.setTurnId(ctx.turnId());
    RequestContext.setConversationId(ctx.conversationId());
    RequestContext.setAccountId(ctx.accountId());
  }

  /**
   * 获取当前线程的链路上下文。
   *
   * <p>从 {@link RequestContext} 读取四个 Agent 业务维度并组装为 record，任一维度为 null 时对应字段为 null。
   *
   * @return 当前线程的链路上下文，未设置时四个字段均可能为 null
   */
  public static TraceContext get() {
    return new TraceContext(
        RequestContext.getBotId(),
        RequestContext.getTurnId(),
        RequestContext.getConversationId(),
        RequestContext.getAccountId());
  }

  /**
   * 清除当前线程的链路上下文。
   *
   * <p>推荐使用 {@link RequestContext#clear()} 兜底清理（已在 Web 拦截器统一调用），
   * 本方法作为显式清理的快捷入口，语义等价于仅清除四个 Agent 维度。
   */
  public static void clear() {
    RequestContext.clearAgentDimensions();
  }
}
