package com.njydsz.pmis.agent.engine.react;

import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.engine.stream.NoOpReActEventListener;
import com.njydsz.pmis.agent.engine.stream.ReActEventListener;
import com.njydsz.pmis.agent.tool.AgentTool;
import com.njydsz.pmis.agent.tool.ToolRegistry;
import com.njydsz.pmis.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ReAct 推理循环（P1-2 落地）
 *
 * <p>对标 LangGraph / Coze / Dify 的 ReAct 推理引擎，实现 Thought → Action → Observation
 * 循环，让 LLM 能够主动调用工具获取外部信息，再基于观察结果给出最终答案。
 *
 * <p>核心循环：
 * <ol>
 *   <li>构建 system prompt（包含工具清单 + ReAct 输出格式说明）</li>
 *   <li>调用 LLM，解析得到 {@link ReActDecision}（Thought + Action）</li>
 *   <li>若 action == {@code final_answer}，结束循环，返回 finalAnswer</li>
 *   <li>否则按 action 名称查找工具，执行得到 Observation</li>
 *   <li>将 Observation 拼接到下一轮 user prompt，回到步骤 2</li>
 *   <li>达到最大循环次数仍未得到 final_answer，返回失败</li>
 * </ol>
 *
 * <p>异常处理策略：
 * <ul>
 *   <li>LLM 调用 / JSON 解析异常 → 直接返回失败（不可恢复）</li>
 *   <li>工具不存在 / 工具执行异常 → 将错误信息作为 Observation 反馈给 LLM（可恢复）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-2)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActLoop {

    /** 默认最大循环次数（防止无限循环） */
    public static final int DEFAULT_MAX_STEPS = 5;

    /** 终止动作标识 */
    public static final String ACTION_FINAL_ANSWER = "final_answer";

    private final LlmProviderRouter llmProviderRouter;
    private final ToolRegistry toolRegistry;

    /**
     * 运行 ReAct 推理循环（使用默认最大步数）。
     *
     * @param baseSystemPrompt 业务系统提示词（工具清单会自动拼接）
     * @param userPrompt       用户问题
     * @param ctx              Agent 上下文
     * @return 推理结果（包含步骤轨迹 + 最终答案）
     */
    public ReActResult run(String baseSystemPrompt, String userPrompt, AgentContext ctx) {
        return run(baseSystemPrompt, userPrompt, ctx, DEFAULT_MAX_STEPS);
    }

    /**
     * 运行 ReAct 推理循环（指定最大步数）。
     *
     * <p>不带事件监听器，等价于 {@link #runStream(String, String, AgentContext, int, ReActEventListener)}
     * 传入 {@link NoOpReActEventListener}。
     *
     * @param baseSystemPrompt 业务系统提示词（工具清单会自动拼接）
     * @param userPrompt       用户问题
     * @param ctx              Agent 上下文
     * @param maxSteps         最大循环次数（防止无限循环，建议 3-10）
     * @return 推理结果（包含步骤轨迹 + 最终答案）
     */
    public ReActResult run(String baseSystemPrompt, String userPrompt,
                           AgentContext ctx, int maxSteps) {
        return runStream(baseSystemPrompt, userPrompt, ctx, maxSteps,
                NoOpReActEventListener.getInstance());
    }

    /**
     * 运行 ReAct 推理循环（流式版本，P2-1 落地）。
     *
     * <p>与 {@link #run(String, String, AgentContext, int)} 行为一致，但在循环关键节点
     * 触发 {@link ReActEventListener} 回调，用于 SSE 推送 / 日志 / Tracing 等输出。
     *
     * <p>监听器回调顺序（每步）：
     * <ol>
     *   <li>{@link ReActEventListener#onStepStart(int)}</li>
     *   <li>{@link ReActEventListener#onThought(int, String)}（拿到 thought 后）</li>
     *   <li>{@link ReActEventListener#onAction(int, ReActDecision)}（拿到 action 后）</li>
     *   <li>{@link ReActEventListener#onObservation(int, String)}（拿到工具结果后，非终止步骤）</li>
     *   <li>{@link ReActEventListener#onFinalAnswer(int, String)}（终止步骤）</li>
     *   <li>{@link ReActEventListener#onStepEnd(int)}</li>
     * </ol>
     *
     * <p>循环结束时触发：
     * <ul>
     *   <li>正常结束：{@link ReActEventListener#onComplete(ReActResult)}</li>
     *   <li>未捕获异常：{@link ReActEventListener#onError(int, Throwable)} +
     *       {@link ReActEventListener#onComplete(ReActResult)}（返回失败结果）</li>
     * </ul>
     *
     * <p><b>线程安全</b>：监听器实现的异常会被捕获并记录日志，不会中断主流程。
     *
     * @param baseSystemPrompt 业务系统提示词（工具清单会自动拼接）
     * @param userPrompt       用户问题
     * @param ctx              Agent 上下文
     * @param maxSteps         最大循环次数（&lt;= 0 时使用默认值）
     * @param listener         事件监听器（null 时使用 NoOp）
     * @return 推理结果（包含步骤轨迹 + 最终答案）
     */
    public ReActResult runStream(String baseSystemPrompt, String userPrompt,
                                 AgentContext ctx, int maxSteps,
                                 ReActEventListener listener) {
        // 处理 null 监听器（赋值给 final 变量供 lambda 引用）
        final ReActEventListener finalListener;
        if (listener == null) {
            finalListener = NoOpReActEventListener.getInstance();
        } else {
            finalListener = listener;
        }
        if (maxSteps <= 0) {
            maxSteps = DEFAULT_MAX_STEPS;
        }

        // 拼接完整 system prompt：业务提示词 + ReAct 格式说明 + 工具清单
        String fullSystemPrompt = buildFullSystemPrompt(baseSystemPrompt);
        // 当前轮次的 user prompt（会随循环迭代追加 Observation）
        StringBuilder currentUserPrompt = new StringBuilder(userPrompt);

        List<ReActStep> steps = new ArrayList<>();
        LlmProvider llm = llmProviderRouter.active();

        ReActResult finalResult;
        try {
            for (int step = 1; step <= maxSteps; step++) {
                final int currentStep = step;
                safeNotify(listener, l -> l.onStepStart(currentStep));

                ReActStep stepRecord = new ReActStep();
                stepRecord.setStepIndex(currentStep);

                // 1. 调用 LLM，获取决策
                ReActDecision decision;
                try {
                    decision = llm.chatForJson(fullSystemPrompt,
                            currentUserPrompt.toString(), ReActDecision.class, ctx);
                } catch (Exception e) {
                    log.warn("[ReAct] step={} LLM 调用或 JSON 解析异常: {}", currentStep, e.getMessage());
                    stepRecord.setThought("[LLM 异常] " + e.getMessage());
                    stepRecord.setAction(ACTION_FINAL_ANSWER);
                    stepRecord.setFinalAnswer(null);
                    steps.add(stepRecord);
                    finalResult = ReActResult.failure(
                            "LLM 调用失败: " + e.getMessage(), steps);
                    safeNotify(listener, l -> l.onStepEnd(currentStep));
                    safeNotifyComplete(listener, finalResult);
                    return finalResult;
                }

                // 防御：LLM 返回 null
                if (decision == null || decision.getAction() == null) {
                    log.warn("[ReAct] step={} LLM 返回空决策", currentStep);
                    stepRecord.setThought("[空决策]");
                    stepRecord.setAction(ACTION_FINAL_ANSWER);
                    stepRecord.setFinalAnswer(null);
                    steps.add(stepRecord);
                    finalResult = ReActResult.failure("LLM 返回空决策", steps);
                    safeNotify(listener, l -> l.onStepEnd(currentStep));
                    safeNotifyComplete(listener, finalResult);
                    return finalResult;
                }

                // 记录 Thought + Action
                stepRecord.setThought(decision.getThought());
                stepRecord.setAction(decision.getAction());
                stepRecord.setParameters(decision.getParameters());

                log.info("[ReAct] step={} thought={} action={}", currentStep,
                        truncate(decision.getThought(), 80), decision.getAction());

                // 通知 thought + action
                safeNotify(listener, l -> l.onThought(currentStep, decision.getThought()));
                safeNotify(listener, l -> l.onAction(currentStep, decision));

                // 2. 判断是否为终止步骤
                if (decision.isTerminal()) {
                    stepRecord.setFinalAnswer(decision.getFinalAnswer());
                    steps.add(stepRecord);
                    safeNotify(listener, l -> l.onFinalAnswer(currentStep, decision.getFinalAnswer()));
                    safeNotify(listener, l -> l.onStepEnd(currentStep));
                    log.info("[ReAct] 循环完成, steps={}, finalAnswer.length={}",
                            currentStep, decision.getFinalAnswer() == null ? 0 : decision.getFinalAnswer().length());
                    finalResult = ReActResult.success(decision.getFinalAnswer(), steps);
                    safeNotifyComplete(listener, finalResult);
                    return finalResult;
                }

                // 3. 执行工具调用，得到 Observation
                String observation = executeTool(decision, ctx);
                stepRecord.setObservation(observation);
                steps.add(stepRecord);
                safeNotify(listener, l -> l.onObservation(currentStep, observation));

                // 4. 将 Observation 拼接到下一轮 user prompt
                currentUserPrompt.append("\n\n[步骤 ").append(currentStep).append(" 观察]\n")
                        .append(observation);

                safeNotify(listener, l -> l.onStepEnd(currentStep));
            }

            // 达到最大循环次数仍未得到 final_answer
            log.warn("[ReAct] 达到最大循环次数 {} 仍未得到 final_answer", maxSteps);
            finalResult = ReActResult.failure("达到最大循环次数: " + maxSteps, steps);
        } catch (RuntimeException e) {
            log.error("[ReAct] 未捕获异常: {}", e.getMessage(), e);
            safeNotifyError(listener, steps.size(), e);
            finalResult = ReActResult.failure("未捕获异常: " + e.getMessage(), steps);
        }

        safeNotifyComplete(listener, finalResult);
        return finalResult;
    }

    /**
     * 安全触发监听器回调（捕获所有异常，仅记录日志）。
     *
     * @param listener 监听器
     * @param action   回调动作
     */
    private void safeNotify(ReActEventListener listener,
                            java.util.function.Consumer<ReActEventListener> action) {
        try {
            action.accept(listener);
        } catch (Exception e) {
            log.warn("[ReAct] 监听器回调异常: {}", e.getMessage(), e);
        }
    }

    /** 安全触发 onComplete */
    private void safeNotifyComplete(ReActEventListener listener, ReActResult result) {
        safeNotify(listener, l -> l.onComplete(result));
    }

    /** 安全触发 onError */
    private void safeNotifyError(ReActEventListener listener, int step, Throwable error) {
        safeNotify(listener, l -> l.onError(step, error));
    }

    /**
     * 执行工具调用并返回 Observation 文本。
     *
     * <p>异常处理：工具不存在 / 执行异常时，将错误信息作为 Observation 返回，
     * 让 LLM 有机会在下一轮纠正。
     *
     * @param decision LLM 决策
     * @param ctx      Agent 上下文
     * @return Observation 文本
     */
    private String executeTool(ReActDecision decision, AgentContext ctx) {
        String toolName = decision.getAction();
        Map<String, Object> parameters = decision.getParameters();

        // 查找工具
        Optional<AgentTool> toolOpt = toolRegistry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            String msg = "工具 [" + toolName + "] 不存在，可用工具: " + toolRegistry.listToolNames();
            log.warn("[ReAct] {}", msg);
            return msg;
        }

        // 执行工具
        try {
            AgentTool tool = toolOpt.get();
            ToolResult result = tool.execute(parameters, ctx);
            if (result.isSuccess()) {
                return result.getOutput();
            } else {
                return "工具 [" + toolName + "] 执行失败: " + result.getError();
            }
        } catch (Exception e) {
            log.warn("[ReAct] 工具 [{}] 执行异常: {}", toolName, e.getMessage());
            return "工具 [" + toolName + "] 执行异常: " + e.getMessage();
        }
    }

    /**
     * 构建完整 system prompt：业务提示词 + ReAct 格式说明 + 工具清单。
     *
     * @param baseSystemPrompt 业务系统提示词
     * @return 完整 system prompt
     */
    private String buildFullSystemPrompt(String baseSystemPrompt) {
        return (baseSystemPrompt == null ? "" : baseSystemPrompt)
                + "\n\n" + REACT_FORMAT_INSTRUCTION + "\n\n"
                + toolRegistry.formatToolsForPrompt();
    }

    /** 截断字符串用于日志输出 */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /** ReAct 输出格式说明（固定文本） */
    private static final String REACT_FORMAT_INSTRUCTION = """
            你正在参与 ReAct 推理循环（Thought → Action → Observation）。
            每一步你必须输出以下 JSON 结构（不要使用 markdown 代码块包裹）：
            {
              "thought": "对当前步骤的思考（为何选择此 Action）",
              "action": "工具名 或 final_answer",
              "parameters": { "参数名": "参数值" },
              "finalAnswer": null
            }

            规则：
            1. 若需要调用工具获取信息，action 填写工具名，parameters 填写工具参数，finalAnswer 必须为 null。
            2. 若已得到最终答案，action 必须填写 "final_answer"，parameters 必须为 null，finalAnswer 填写最终答案。
            3. 你可以最多思考 5 步，请合理规划工具调用顺序。
            4. 工具执行结果会以 "[步骤 N 观察]" 的形式追加在用户问题之后。""";
}
