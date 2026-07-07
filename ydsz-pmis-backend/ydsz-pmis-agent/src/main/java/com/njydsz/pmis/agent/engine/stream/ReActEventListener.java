package com.njydsz.pmis.agent.engine.stream;

import com.njydsz.pmis.agent.engine.react.ReActDecision;
import com.njydsz.pmis.agent.engine.react.ReActResult;

/**
 * ReAct 推理循环事件监听器（P2-1 落地）
 *
 * <p>用于 {@link com.njydsz.pmis.agent.engine.react.ReActLoop#runStream} 在循环关键节点
 * 触发回调，将事件转换为 SSE 推送 / 日志 / Tracing 等输出。
 *
 * <p>所有方法均为默认空实现，子类按需重写感兴趣的回调，避免侵入业务逻辑。
 *
 * <p>典型实现：
 * <ul>
 *   <li>{@code SseEventListener} - 把事件推送到 Spring MVC {@code SseEmitter}</li>
 *   <li>{@code LoggingEventListener} - 仅记录日志（调试用）</li>
 *   <li>{@code TracingEventListener} - 上报 Micrometer / SkyWalking Span（P2-3 落地）</li>
 * </ul>
 *
 * <p><b>线程安全</b>：监听器实现需要自行处理线程安全。默认实现是同步调用的，
 * 即监听器抛出的异常会中断整个 ReAct 循环；建议在实现中 try-catch 住所有异常。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-1)
 */
public interface ReActEventListener {

    /**
     * 步骤开始（每轮 LLM 调用前触发）。
     *
     * @param stepIndex 步骤序号（1-based）
     */
    default void onStepStart(int stepIndex) {
        // 默认空实现
    }

    /**
     * LLM 思考完成（拿到 ReActDecision.thought 后触发）。
     *
     * @param stepIndex 步骤序号
     * @param thought   LLM 思考文本
     */
    default void onThought(int stepIndex, String thought) {
        // 默认空实现
    }

    /**
     * LLM 决策动作（拿到 ReActDecision.action 后触发）。
     *
     * @param stepIndex  步骤序号
     * @param decision   LLM 决策（含 action / parameters / finalAnswer）
     */
    default void onAction(int stepIndex, ReActDecision decision) {
        // 默认空实现
    }

    /**
     * 工具执行结果就绪（拿到 Observation 后触发）。
     *
     * @param stepIndex   步骤序号
     * @param observation  工具执行结果文本
     */
    default void onObservation(int stepIndex, String observation) {
        // 默认空实现
    }

    /**
     * 最终答案就绪（LLM 返回 final_answer 时触发）。
     *
     * @param stepIndex   步骤序号
     * @param finalAnswer  最终答案文本
     */
    default void onFinalAnswer(int stepIndex, String finalAnswer) {
        // 默认空实现
    }

    /**
     * 步骤结束（无论成功/失败/终止都触发）。
     *
     * @param stepIndex 步骤序号
     */
    default void onStepEnd(int stepIndex) {
        // 默认空实现
    }

    /**
     * ReAct 循环完成。
     *
     * @param result 完整 ReAct 结果
     */
    default void onComplete(ReActResult result) {
        // 默认空实现
    }

    /**
     * ReAct 循环异常终止。
     *
     * <p>注意：这里仅指未捕获异常导致的终止；
     * LLM 调用失败、达到最大步数等业务失败会通过 {@link #onComplete} 返回失败的 ReActResult。
     *
     * @param stepIndex 步骤序号（异常发生时的步骤；未开始时为 0）
     * @param error     异常对象
     */
    default void onError(int stepIndex, Throwable error) {
        // 默认空实现
    }
}
