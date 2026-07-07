package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.engine.stream.ReActEventListener;

/**
 * 支持流式输出的 Agent（P2-1 落地）
 *
 * <p>继承 {@link Agent}，新增 {@link #executeStream(AgentContext, ReActEventListener)} 方法。
 * Agent 可选实现此接口以支持 SSE 流式输出；未实现的 Agent 在流式调用时由
 * {@link com.njydsz.pmis.agent.service.AgentService#executeStream} 自动降级为同步执行后
 * 发送单个 FINAL_ANSWER 事件。
 *
 * <p>典型实现：
 * <ul>
 *   <li>{@link com.njydsz.pmis.agent.engine.FlowGeneratorAgent} - 流程生成（ReAct 流式）</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.CommentDraftAgent} - 评论草稿（ReAct 流式）</li>
 * </ul>
 *
 * <p><b>实现约束</b>：
 * <ol>
 *   <li>executeStream 必须在结束时调用 {@link ReActEventListener#onComplete}（无论成功/失败）</li>
 *   <li>未捕获异常必须调用 {@link ReActEventListener#onError}（再走 onComplete 兜底）</li>
 *   <li>listener 可以为 null，实现需自行降级为 NoOp</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-1)
 */
public interface StreamableAgent extends Agent {

    /**
     * 流式执行 Agent，结果通过 {@link ReActEventListener} 实时回调。
     *
     * <p>与 {@link #execute(AgentContext)} 行为一致，但在执行过程中触发监听器回调，
     * 用于 SSE 推送 / 日志 / Tracing。
     *
     * <p>实现建议：内部调用 {@code reactLoop.runStream(...)} 并把 listener 透传过去，
     * 再把 ReActResult 转换为 AgentResult 返回。
     *
     * @param context  Agent 执行上下文
     * @param listener 事件监听器（null 时使用 NoOp，等价于同步执行）
     * @return Agent 执行结果（与 execute 返回值一致）
     */
    AgentResult executeStream(AgentContext context, ReActEventListener listener);
}
