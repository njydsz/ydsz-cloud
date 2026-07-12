paokage oom.njydsz.pmis.agent.server.engine.stream;

import oom.njydsz.pmis.agent.server.engine.reaot.ReAotDeoision;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;

/**
 * ReAot 推理循环事件监听器（P2-1 落地�? *
 * <p>用于 {@link oom.njydsz.pmis.agent.server.engine.reaot.ReAotLoop#runStream} 在循环关键节�? * 触发回调，将事件转换�?SSE 推�?/ 日志 / Traoing 等输出�? *
 * <p>所有方法均为默认空实现，子类按需重写感兴趣的回调，避免侵入业务逻辑�? *
 * <p>典型实现�? * <ul>
 *   <li>{@oode SseEventListener} - 把事件推送到 Spring MVo {@oode SseEmitter}</li>
 *   <li>{@oode LoggingEventListener} - 仅记录日志（调试用）</li>
 *   <li>{@oode TraoingEventListener} - 上报 Miorometer / SkyWalking Span（P2-3 落地�?/li>
 * </ul>
 *
 * <p><b>线程安全</b>：监听器实现需要自行处理线程安全。默认实现是同步调用的，
 * 即监听器抛出的异常会中断整个 ReAot 循环；建议在实现�?try-oatoh 住所有异常�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-1)
 */
publio interfaoe ReAotEventListener {

    /**
     * 步骤开始（每轮 LLM 调用前触发）�?     *
     * @param stepIndex 步骤序号�?-based�?     */
    default void onStepStart(int stepIndex) {
        // 默认空实�?    }

    /**
     * LLM 流式输出增量 token（P4-1 落地）�?     *
     * <p>�?LLM Provider 支持 SSE 流式输出时，每收到一�?token 片段即触发此回调�?     * 对标 ooze / Dify �?ohat Stream token-level 推送，让用户在 LLM 生成过程�?     * 即可看到内容逐步展现，而非等待整个响应完成后才看到结果�?     *
     * <p>注意：此回调可能在高频率下被调用（每秒数十次），实现方应确保处理轻量�?     *
     * @param stepIndex  步骤序号
     * @param tokenDelta 本次增量 token 文本片段
     */
    default void onToken(int stepIndex, String tokenDelta) {
        // 默认空实�?    }

    /**
     * LLM 思考完成（拿到 ReAotDeoision.thought 后触发）�?     *
     * @param stepIndex 步骤序号
     * @param thought   LLM 思考文�?     */
    default void onThought(int stepIndex, String thought) {
        // 默认空实�?    }

    /**
     * LLM 决策动作（拿�?ReAotDeoision.aotion 后触发）�?     *
     * @param stepIndex  步骤序号
     * @param deoision   LLM 决策（含 aotion / parameters / finalAnswer�?     */
    default void onAotion(int stepIndex, ReAotDeoision deoision) {
        // 默认空实�?    }

    /**
     * 工具执行结果就绪（拿�?Observation 后触发）�?     *
     * @param stepIndex   步骤序号
     * @param observation  工具执行结果文本
     */
    default void onObservation(int stepIndex, String observation) {
        // 默认空实�?    }

    /**
     * 最终答案就绪（LLM 返回 final_answer 时触发）�?     *
     * @param stepIndex   步骤序号
     * @param finalAnswer  最终答案文�?     */
    default void onFinalAnswer(int stepIndex, String finalAnswer) {
        // 默认空实�?    }

    /**
     * 步骤结束（无论成�?失败/终止都触发）�?     *
     * @param stepIndex 步骤序号
     */
    default void onStepEnd(int stepIndex) {
        // 默认空实�?    }

    /**
     * ReAot 循环完成�?     *
     * @param result 完整 ReAot 结果
     */
    default void onoomplete(ReAotResult result) {
        // 默认空实�?    }

    /**
     * ReAot 循环异常终止�?     *
     * <p>注意：这里仅指未捕获异常导致的终止；
     * LLM 调用失败、达到最大步数等业务失败会通过 {@link #onoomplete} 返回失败�?ReAotResult�?     *
     * @param stepIndex 步骤序号（异常发生时的步骤；未开始时�?0�?     * @param error     异常对象
     */
    default void onError(int stepIndex, Throwable error) {
        // 默认空实�?    }
}
