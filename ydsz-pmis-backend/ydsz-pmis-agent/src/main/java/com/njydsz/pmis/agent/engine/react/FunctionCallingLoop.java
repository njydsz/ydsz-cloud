package com.njydsz.pmis.agent.engine.react;

import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.llm.ChatMessageBuilder;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.engine.llm.LlmToolCallResponse;
import com.njydsz.pmis.agent.engine.llm.LlmToolCallResponse.ToolCall;
import com.njydsz.pmis.agent.engine.llm.TokenUsage;
import com.njydsz.pmis.agent.engine.memory.ChatMemory;
import com.njydsz.pmis.agent.engine.memory.ChatMessage;
import com.njydsz.pmis.agent.engine.stream.NoOpReActEventListener;
import com.njydsz.pmis.agent.engine.stream.ReActEventListener;
import com.njydsz.pmis.agent.hitl.ReActSnapshot;
import com.njydsz.pmis.agent.tool.AgentTool;
import com.njydsz.pmis.agent.tool.ToolRegistry;
import com.njydsz.pmis.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

/**
 * 原生 Function Calling 推理循环（P0-1 落地）。
 *
 * <p>对标 OpenAI Function Calling / Coze 原生插件调用 / Dify Tool Agent：
 * 使用 LLM 原生的 tools 参数进行工具调用，替代文本 JSON 解析模式。
 *
 * <p>核心循环：
 * <ol>
 *   <li>构建结构化 messages 数组（system + history + user + tool results）</li>
 *   <li>调用 {@code llm.chatWithTools(messages, tools)} 让 LLM 原生决定是否调用工具</li>
 *   <li>若返回 tool_calls：执行工具，将结果以 role=tool 消息追加到 messages，回到步骤 2</li>
 *   <li>若返回纯文本（无 tool_calls）：作为最终答案返回</li>
 *   <li>达到最大循环次数仍未得到最终答案，返回失败</li>
 * </ol>
 *
 * <p>与 {@link ReActLoop}（文本 JSON 模式）的区别：
 * <ul>
 *   <li>更准确：LLM 原生理解工具 schema，不需要输出特定 JSON 格式</li>
 *   <li>更省 Token：不需要在 system prompt 中注入工具清单和格式说明</li>
 *   <li>更稳定：不受 LLM 输出 JSON 格式不稳定的影响</li>
 *   <li>更高效：支持单轮并行多工具调用（parallel function calling）</li>
 * </ul>
 *
 * <p>降级策略：当 LLM Provider 不支持 Function Calling 时，
 * 由 {@link ReActLoop#runStream} 自动降级为文本 JSON 模式。
 *
 * <p>P0-2：消息历史以结构化 messages 数组传递，而非纯文本拼接。
 * System / User / Assistant / Tool 角色分离，LLM API 原生理解对话上下文。
 *
 * <p>P0-3：每轮 LLM 调用的 Token 用量通过 {@link TokenUsage} 统计，
 * 累加到 {@link AgentContext} 中，用于成本管控和性能分析。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P0-1 + P0-2 + P0-3)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionCallingLoop {

    /** 默认最大循环次数 */
    public static final int DEFAULT_MAX_STEPS = 10;

    @Value("${pmis.agent.react.max-steps:" + DEFAULT_MAX_STEPS + "}")
    private int configuredMaxSteps;

    /** 默认单工具执行超时（秒，P1-1） */
    public static final int DEFAULT_TOOL_TIMEOUT_SECONDS = 30;

    private final LlmProviderRouter llmProviderRouter;
    private final ToolRegistry toolRegistry;
    private final ObjectProvider<ChatMemory> chatMemoryProvider;

    /**
     * 共享工具并行执行线程池。
     */
    private final ExecutorService toolExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fc-parallel-tool");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void destroy() {
        toolExecutor.shutdown();
        try {
            if (!toolExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                toolExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            toolExecutor.shutdownNow();
        }
        log.info("[FunctionCallingLoop] 工具并行执行线程池已关闭");
    }

    /**
     * 运行原生 Function Calling 推理循环。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @param ctx          Agent 上下文
     * @param maxSteps     最大循环次数
     * @param listener     事件监听器
     * @return 推理结果
     */
    public ReActResult run(String systemPrompt, String userPrompt,
                           AgentContext ctx, int maxSteps,
                           ReActEventListener listener) {
        final ReActEventListener finalListener =
                listener == null ? NoOpReActEventListener.getInstance() : listener;
        if (maxSteps <= 0) {
            maxSteps = configuredMaxSteps > 0 ? configuredMaxSteps : DEFAULT_MAX_STEPS;
        }

        // 构建初始 messages 数组（P0-2 结构化消息历史）
        ChatMessageBuilder msgBuilder = new ChatMessageBuilder();
        msgBuilder.system(systemPrompt);

        // 加载对话历史（P0-2）
        if (ctx != null && ctx.getSessionId() != null && !ctx.getSessionId().isBlank()) {
            ChatMemory chatMemory = chatMemoryProvider.getIfAvailable();
            if (chatMemory != null) {
                msgBuilder.history(chatMemory, ctx.getSessionId());
            }
        }

        // 添加当前用户输入（P1-5 多模态支持）
        if (ctx != null && ctx.getMultimodalInput() != null && ctx.getMultimodalInput().hasMultimodalContent()) {
            msgBuilder.userMultimodal(userPrompt, ctx.getMultimodalInput());
        } else {
            msgBuilder.user(userPrompt);
        }

        List<JSONObject> messages = msgBuilder.build();
        List<ReActStep> steps = new ArrayList<>();
        TokenUsage totalUsage = TokenUsage.zero();

        // 获取 OpenAI 格式工具定义
        List<Map<String, Object>> tools = toolRegistry.formatToolsForOpenAi();

        try {
            for (int step = 1; step <= maxSteps; step++) {
                final int currentStep = step;
                safeNotify(finalListener, l -> l.onStepStart(currentStep));

                ReActStep stepRecord = new ReActStep();
                stepRecord.setStepIndex(currentStep);

                // 1. 调用 LLM with tools
                LlmProvider llm = llmProviderRouter.active();
                LlmToolCallResponse response;
                try {
                    response = callLlmWithTools(llm, messages, tools, ctx, finalListener, currentStep);
                } catch (Exception e) {
                    log.warn("[FC-Loop] step={} LLM 调用异常: {}", currentStep, e.getMessage());
                    stepRecord.setThought("[LLM 异常] " + e.getMessage());
                    stepRecord.setAction("final_answer");
                    steps.add(stepRecord);
                    ReActResult result = ReActResult.failure("LLM 调用失败: " + e.getMessage(), steps);
                    accumulateUsage(ctx, totalUsage);
                    safeNotify(finalListener, l -> l.onStepEnd(currentStep));
                    safeNotifyComplete(finalListener, result);
                    return result;
                }

                // P0-3: 累加 Token 用量
                if (response != null && response.getUsage() != null) {
                    totalUsage = totalUsage.add(response.getUsage());
                }

                // 2. 检查是否为最终答案（无 tool_calls）
                if (response == null || !response.hasToolCalls()) {
                    final String finalAnswer = response != null && response.getContent() != null
                            ? response.getContent() : "";

                    stepRecord.setThought("LLM 直接给出最终答案");
                    stepRecord.setAction("final_answer");
                    stepRecord.setFinalAnswer(finalAnswer);
                    steps.add(stepRecord);

                    safeNotify(finalListener, l -> l.onFinalAnswer(currentStep, finalAnswer));
                    safeNotify(finalListener, l -> l.onStepEnd(currentStep));

                    log.info("[FC-Loop] 循环完成, steps={}, finalAnswer.length={}",
                            currentStep, finalAnswer.length());

                    // P0-3: 写入总 Token 用量
                    accumulateUsage(ctx, totalUsage);
                    // 写入对话记忆
                    persistToMemory(ctx, userPrompt, finalAnswer);

                    ReActResult result = ReActResult.success(finalAnswer, steps);
                    safeNotifyComplete(finalListener, result);
                    return result;
                }

                // 3. 有 tool_calls：执行工具
                List<ToolCall> toolCalls = response.getToolCalls();
                log.info("[FC-Loop] step={} LLM 请求调用 {} 个工具", currentStep, toolCalls.size());

                // 将 assistant 的 tool_calls 消息追加到 messages（P0-2）
                messages.add(toAssistantToolCallMessage(response));

                // 通知监听器
                for (ToolCall tc : toolCalls) {
                    if (tc.getFunction() != null) {
                        ReActDecision decision = new ReActDecision();
                        decision.setThought("调用工具: " + tc.getFunction().getName());
                        decision.setAction(tc.getFunction().getName());
                        decision.setParameters(tc.getFunction().getArgumentsAsMap());
                        stepRecord.setThought(decision.getThought());
                        stepRecord.setAction(decision.getAction());
                        stepRecord.setParameters(decision.getParameters());
                        safeNotify(finalListener, l -> l.onAction(currentStep, decision));
                    }
                }

                // 执行工具并追加 tool 消息
                for (ToolCall tc : toolCalls) {
                    if (tc.getFunction() == null) continue;
                    String toolName = tc.getFunction().getName();
                    Map<String, Object> params = tc.getFunction().getArgumentsAsMap();

                    // P3-4: HITL 审批检查
                    Optional<AgentTool> toolOpt = toolRegistry.getTool(toolName);
                    if (toolOpt.isPresent() && toolOpt.get().requiresApproval()) {
                        ReActSnapshot snapshot = ReActSnapshot.of(
                                null, null, null, null, ctx, 0, currentStep,
                                "调用工具: " + toolName, toolName, params);
                        // 补充 messages 状态用于恢复
                        snapshot.setBaseSystemPrompt(systemPrompt);
                        snapshot.setCurrentUserPrompt(messagesToJson(messages));
                        snapshot.setOriginalUserPrompt(userPrompt);
                        snapshot.setSteps(new ArrayList<>(steps));
                        snapshot.setAgentContext(ctx);
                        snapshot.setMaxSteps(maxSteps);

                        steps.add(stepRecord);
                        safeNotify(finalListener, l -> l.onStepEnd(currentStep));
                        ReActResult paused = ReActResult.paused(toolName, snapshot, steps);
                        safeNotifyComplete(finalListener, paused);
                        return paused;
                    }

                    String observation = executeToolWithTimeout(toolName, params, ctx);
                    stepRecord.setObservation(observation);
                    safeNotify(finalListener, l -> l.onObservation(currentStep, observation));

                    // P0-2: 将工具结果以 role=tool 消息追加
                    messages.add(toToolMessage(tc.getId(), observation));
                }

                steps.add(stepRecord);
                safeNotify(finalListener, l -> l.onStepEnd(currentStep));
            }

            // 达到最大循环次数
            log.warn("[FC-Loop] 达到最大循环次数 {} 仍未得到最终答案", maxSteps);
            accumulateUsage(ctx, totalUsage);
            ReActResult result = ReActResult.failure("达到最大循环次数: " + maxSteps, steps);
            safeNotifyComplete(finalListener, result);
            return result;

        } catch (RuntimeException e) {
            log.error("[FC-Loop] 未捕获异常: {}", e.getMessage(), e);
            accumulateUsage(ctx, totalUsage);
            safeNotifyError(finalListener, steps.size(), e);
            ReActResult result = ReActResult.failure("未捕获异常: " + e.getMessage(), steps);
            safeNotifyComplete(finalListener, result);
            return result;
        }
    }

    // ==================== LLM 调用 ====================

    /**
     * 调用 LLM 的 chatWithTools 方法，支持流式 token 回调。
     */
    private LlmToolCallResponse callLlmWithTools(LlmProvider llm,
                                                  List<JSONObject> messages,
                                                  List<Map<String, Object>> tools,
                                                  AgentContext ctx,
                                                  ReActEventListener listener,
                                                  int step) {
        // 构建 system prompt（取第一条 system 消息）
        String systemPrompt = "";
        if (!messages.isEmpty() && "system".equals(messages.get(0).getString("role"))) {
            systemPrompt = messages.get(0).getString("content");
        }

        // 构建 user prompt（最后一条 user 消息，或降级为全部非 system 消息拼接）
        StringBuilder userPromptBuilder = new StringBuilder();
        for (int i = 1; i < messages.size(); i++) {
            JSONObject msg = messages.get(i);
            String role = msg.getString("role");
            String content = msg.containsKey("content")
                    ? (msg.get("content") instanceof String
                        ? msg.getString("content")
                        : msg.getJSONArray("content").toJSONString())
                    : "";
            if ("user".equals(role)) {
                userPromptBuilder.append(content).append("\n");
            } else if ("assistant".equals(role)) {
                userPromptBuilder.append("[Assistant] ").append(content).append("\n");
            } else if ("tool".equals(role)) {
                userPromptBuilder.append("[Tool Result] ").append(content).append("\n");
            }
        }

        String userPrompt = userPromptBuilder.toString().trim();

        LlmToolCallResponse response = llm.chatWithTools(systemPrompt, userPrompt, tools, ctx);
        if (response == null) {
            // Provider 不支持或降级
            log.warn("[FC-Loop] LLM chatWithTools 返回 null, 降级为纯文本回复");
            response = new LlmToolCallResponse();
            String content = llm.chat(systemPrompt, userPrompt, ctx);
            response.setContent(content);
        }
        return response;
    }

    // ==================== 工具执行 ====================

    /**
     * 执行工具调用，带超时控制（P1-1）。
     */
    private String executeToolWithTimeout(String toolName, Map<String, Object> params, AgentContext ctx) {
        Optional<AgentTool> toolOpt = toolRegistry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            String msg = "工具 [" + toolName + "] 不存在，可用工具: " + toolRegistry.listToolNames();
            log.warn("[FC-Loop] {}", msg);
            return msg;
        }

        try {
            // P1-1: 使用 CompletableFuture.orTimeout 实现单工具超时
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    AgentTool tool = toolOpt.get();
                    ToolResult result = tool.execute(params, ctx);
                    if (result.isSuccess()) {
                        return result.getOutput();
                    } else {
                        return "工具 [" + toolName + "] 执行失败: " + result.getError();
                    }
                } catch (Exception e) {
                    return "工具 [" + toolName + "] 执行异常: " + e.getMessage();
                }
            }, toolExecutor);

            return future.get(DEFAULT_TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("[FC-Loop] 工具 [{}] 执行超时 ({}s)", toolName, DEFAULT_TOOL_TIMEOUT_SECONDS);
            return "工具 [" + toolName + "] 执行超时";
        } catch (Exception e) {
            log.warn("[FC-Loop] 工具 [{}] 执行异常: {}", toolName, e.getMessage());
            return "工具 [" + toolName + "] 执行异常: " + e.getMessage();
        }
    }

    // ==================== 消息构造 ====================

    /**
     * 将 LlmToolCallResponse 转换为 assistant + tool_calls 消息（P0-2）。
     */
    private JSONObject toAssistantToolCallMessage(LlmToolCallResponse response) {
        JSONObject msg = new JSONObject();
        msg.put("role", "assistant");
        if (response.getContent() != null && !response.getContent().isBlank()) {
            msg.put("content", response.getContent());
        }
        if (response.hasToolCalls()) {
            com.alibaba.fastjson2.JSONArray tcArr = new com.alibaba.fastjson2.JSONArray();
            for (ToolCall tc : response.getToolCalls()) {
                JSONObject tcJson = new JSONObject();
                tcJson.put("id", tc.getId());
                tcJson.put("type", tc.getType() != null ? tc.getType() : "function");
                if (tc.getFunction() != null) {
                    JSONObject fn = new JSONObject();
                    fn.put("name", tc.getFunction().getName());
                    fn.put("arguments", tc.getFunction().getArguments() != null
                            ? tc.getFunction().getArguments() : "{}");
                    tcJson.put("function", fn);
                }
                tcArr.add(tcJson);
            }
            msg.put("tool_calls", tcArr);
        }
        return msg;
    }

    /**
     * 构造 tool role 消息（P0-2）。
     */
    private JSONObject toToolMessage(String toolCallId, String content) {
        JSONObject msg = new JSONObject();
        msg.put("role", "tool");
        msg.put("tool_call_id", toolCallId);
        msg.put("content", content);
        return msg;
    }

    /**
     * 将 messages 列表序列化为 JSON 字符串（用于快照恢复）。
     */
    private String messagesToJson(List<JSONObject> messages) {
        com.alibaba.fastjson2.JSONArray arr = new com.alibaba.fastjson2.JSONArray();
        for (JSONObject msg : messages) {
            arr.add(msg);
        }
        return arr.toJSONString();
    }

    // ==================== Token 用量 ====================

    /**
     * 累加 Token 用量到 AgentContext（P0-3）。
     */
    private void accumulateUsage(AgentContext ctx, TokenUsage usage) {
        if (ctx == null || usage == null) return;
        try {
            TokenUsage existing = ctx.getTokenUsage();
            if (existing == null) {
                ctx.setTokenUsage(usage);
            } else {
                ctx.setTokenUsage(existing.add(usage));
            }
            log.debug("[FC-Loop] Token 用量: {}", ctx.getTokenUsage());
        } catch (Exception e) {
            log.warn("[FC-Loop] Token 用量累加失败: {}", e.getMessage());
        }
    }

    // ==================== 对话记忆 ====================

    /**
     * 写入对话记忆（P1-1 兼容）。
     */
    private void persistToMemory(AgentContext ctx, String userPrompt, String finalAnswer) {
        if (ctx == null || ctx.getSessionId() == null || ctx.getSessionId().isBlank()) {
            return;
        }
        ChatMemory chatMemory = chatMemoryProvider.getIfAvailable();
        if (chatMemory == null) return;
        try {
            chatMemory.addMessage(ctx.getSessionId(), ChatMessage.user(userPrompt));
            chatMemory.addMessage(ctx.getSessionId(), ChatMessage.assistant(finalAnswer));
        } catch (Exception e) {
            log.warn("[FC-Loop] 写入 ChatMemory 失败: {}", e.getMessage());
        }
    }

    // ==================== 监听器安全通知 ====================

    private void safeNotify(ReActEventListener listener,
                            java.util.function.Consumer<ReActEventListener> action) {
        try {
            action.accept(listener);
        } catch (Exception e) {
            log.warn("[FC-Loop] 监听器回调异常: {}", e.getMessage());
        }
    }

    private void safeNotifyComplete(ReActEventListener listener, ReActResult result) {
        safeNotify(listener, l -> l.onComplete(result));
    }

    private void safeNotifyError(ReActEventListener listener, int step, Throwable error) {
        safeNotify(listener, l -> l.onError(step, error));
    }
}
