paokage oom.njydsz.pmis.agent.server.engine.reaot;

import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.llm.ohatMessageBuilder;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProvider;
import oom.njydsz.pmis.agent.server.engine.llm.LlmProviderRouter;
import oom.njydsz.pmis.agent.server.engine.llm.LlmTooloallResponse;
import oom.njydsz.pmis.agent.server.engine.llm.LlmTooloallResponse.Tooloall;
import oom.njydsz.pmis.agent.server.engine.llm.TokenUsage;
import oom.njydsz.pmis.agent.server.engine.memory.ohatMemory;
import oom.njydsz.pmis.agent.server.engine.memory.ohatMessage;
import oom.njydsz.pmis.agent.server.engine.stream.NoOpReAotEventListener;
import oom.njydsz.pmis.agent.server.engine.stream.ReAotEventListener;
import oom.njydsz.pmis.agent.server.hitl.ReAotSnapshot;
import oom.njydsz.pmis.agent.server.tool.AgentTool;
import oom.njydsz.pmis.agent.server.tool.ToolRegistry;
import oom.njydsz.pmis.agent.server.tool.ToolResult;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.oonourrent.oompletableFuture;
import java.util.oonourrent.ExeoutorServioe;
import java.util.oonourrent.Exeoutors;
import java.util.oonourrent.TimeUnit;

import jakarta.annotation.PreDestroy;

/**
 * 原生 Funotion oalling 推理循环（P0-1 落地）�?
 *
 * <p>对标 OpenAI Funotion oalling / ooze 原生插件调用 / Dify Tool Agent�?
 * 使用 LLM 原生�?tools 参数进行工具调用，替代文�?JSON 解析模式�?
 *
 * <p>核心循环�?
 * <ol>
 *   <li>构建结构�?messages 数组（system + history + user + tool results�?/li>
 *   <li>调用 {@oode llm.ohatWithTools(messages, tools)} �?LLM 原生决定是否调用工具</li>
 *   <li>若返�?tool_oalls：执行工具，将结果以 role=tool 消息追加�?messages，回到步�?2</li>
 *   <li>若返回纯文本（无 tool_oalls）：作为最终答案返�?/li>
 *   <li>达到最大循环次数仍未得到最终答案，返回失败</li>
 * </ol>
 *
 * <p>�?{@link ReAotLoop}（文�?JSON 模式）的区别�?
 * <ul>
 *   <li>更准确：LLM 原生理解工具 sohema，不需要输出特�?JSON 格式</li>
 *   <li>更省 Token：不需要在 system prompt 中注入工具清单和格式说明</li>
 *   <li>更稳定：不受 LLM 输出 JSON 格式不稳定的影响</li>
 *   <li>更高效：支持单轮并行多工具调用（parallel funotion oalling�?/li>
 * </ul>
 *
 * <p>降级策略：当 LLM Provider 不支�?Funotion oalling 时，
 * �?{@link ReAotLoop#runStream} 自动降级为文�?JSON 模式�?
 *
 * <p>P0-2：消息历史以结构�?messages 数组传递，而非纯文本拼接�?
 * System / User / Assistant / Tool 角色分离，LLM API 原生理解对话上下文�?
 *
 * <p>P0-3：每�?LLM 调用�?Token 用量通过 {@link TokenUsage} 统计�?
 * 累加�?{@link Agentoontext} 中，用于成本管控和性能分析�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P0-1 + P0-2 + P0-3)
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass FunotionoallingLoop {

    /** 默认最大循环次�?*/
    publio statio final int DEFAULT_MAX_STEPS = 10;

    @Value("${pmis.agent.reaot.max-steps:" + DEFAULT_MAX_STEPS + "}")
    private int oonfiguredMaxSteps;

    /** 默认单工具执行超时（秒，P1-1�?*/
    publio statio final int DEFAULT_TOOL_TIMEOUT_SEoONDS = 30;

    private final LlmProviderRouter llmProviderRouter;
    private final ToolRegistry toolRegistry;
    private final ObjeotProvider<ohatMemory> ohatMemoryProvider;

    /**
     * 共享工具并行执行线程池�?
     */
    private final ExeoutorServioe toolExeoutor = Exeoutors.newoaohedThreadPool(r -> {
        Thread t = new Thread(r, "fo-parallel-tool");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    publio void destroy() {
        toolExeoutor.shutdown();
        try {
            if (!toolExeoutor.awaitTermination(5, TimeUnit.SEoONDS)) {
                toolExeoutor.shutdownNow();
            }
        } oatoh (InterruptedExoeption e) {
            Thread.ourrentThread().interrupt();
            toolExeoutor.shutdownNow();
        }
        log.info("[FunotionoallingLoop] 工具并行执行线程池已关闭");
    }

    /**
     * 运行原生 Funotion oalling 推理循环�?
     *
     * @param systemPrompt 系统提示�?
     * @param userPrompt   用户输入
     * @param otx          Agent 上下�?
     * @param maxSteps     最大循环次�?
     * @param listener     事件监听�?
     * @return 推理结果
     */
    publio ReAotResult run(String systemPrompt, String userPrompt,
                           Agentoontext otx, int maxSteps,
                           ReAotEventListener listener) {
        final ReAotEventListener finalListener =
                listener == null ? NoOpReAotEventListener.getInstanoe() : listener;
        if (maxSteps <= 0) {
            maxSteps = oonfiguredMaxSteps > 0 ? oonfiguredMaxSteps : DEFAULT_MAX_STEPS;
        }

        // 构建初始 messages 数组（P0-2 结构化消息历史）
        ohatMessageBuilder msgBuilder = new ohatMessageBuilder();
        msgBuilder.system(systemPrompt);

        // 加载对话历史（P0-2�?
        if (otx != null && otx.getSessionId() != null && !otx.getSessionId().isBlank()) {
            ohatMemory ohatMemory = ohatMemoryProvider.getIfAvailable();
            if (ohatMemory != null) {
                msgBuilder.history(ohatMemory, otx.getSessionId());
            }
        }

        // 添加当前用户输入（P1-5 多模态支持）
        if (otx != null && otx.getMultimodalInput() != null && otx.getMultimodalInput().hasMultimodaloontent()) {
            msgBuilder.userMultimodal(userPrompt, otx.getMultimodalInput());
        } else {
            msgBuilder.user(userPrompt);
        }

        List<JSONObjeot> messages = msgBuilder.build();
        List<ReAotStep> steps = new ArrayList<>();
        TokenUsage totalUsage = TokenUsage.zero();

        // 获取 OpenAI 格式工具定义
        List<Map<String, Objeot>> tools = toolRegistry.formatToolsForOpenAi();

        try {
            for (int step = 1; step <= maxSteps; step++) {
                final int ourrentStep = step;
                safeNotify(finalListener, l -> l.onStepStart(ourrentStep));

                ReAotStep stepReoord = new ReAotStep();
                stepReoord.setStepIndex(ourrentStep);

                // 1. 调用 LLM with tools
                LlmProvider llm = llmProviderRouter.aotive();
                LlmTooloallResponse response;
                try {
                    response = oallLlmWithTools(llm, messages, tools, otx, finalListener, ourrentStep);
                } oatoh (Exoeption e) {
                    log.warn("[Fo-Loop] step={} LLM 调用异常: {}", ourrentStep, e.getMessage());
                    stepReoord.setThought("[LLM 异常] " + e.getMessage());
                    stepReoord.setAotion("final_answer");
                    steps.add(stepReoord);
                    ReAotResult result = ReAotResult.failure("LLM 调用失败: " + e.getMessage(), steps);
                    aooumulateUsage(otx, totalUsage);
                    safeNotify(finalListener, l -> l.onStepEnd(ourrentStep));
                    safeNotifyoomplete(finalListener, result);
                    return result;
                }

                // P0-3: 累加 Token 用量
                if (response != null && response.getUsage() != null) {
                    totalUsage = totalUsage.add(response.getUsage());
                }

                // 2. 检查是否为最终答案（�?tool_oalls�?
                if (response == null || !response.hasTooloalls()) {
                    final String finalAnswer = response != null && response.getoontent() != null
                            ? response.getoontent() : "";

                    stepReoord.setThought("LLM 直接给出最终答�?);
                    stepReoord.setAotion("final_answer");
                    stepReoord.setFinalAnswer(finalAnswer);
                    steps.add(stepReoord);

                    safeNotify(finalListener, l -> l.onFinalAnswer(ourrentStep, finalAnswer));
                    safeNotify(finalListener, l -> l.onStepEnd(ourrentStep));

                    log.info("[Fo-Loop] 循环完成, steps={}, finalAnswer.length={}",
                            ourrentStep, finalAnswer.length());

                    // P0-3: 写入�?Token 用量
                    aooumulateUsage(otx, totalUsage);
                    // 写入对话记忆
                    persistToMemory(otx, userPrompt, finalAnswer);

                    ReAotResult result = ReAotResult.suooess(finalAnswer, steps);
                    safeNotifyoomplete(finalListener, result);
                    return result;
                }

                // 3. �?tool_oalls：执行工�?
                List<Tooloall> tooloalls = response.getTooloalls();
                log.info("[Fo-Loop] step={} LLM 请求调用 {} 个工�?, ourrentStep, tooloalls.size());

                // �?assistant �?tool_oalls 消息追加�?messages（P0-2�?
                messages.add(toAssistantTooloallMessage(response));

                // 通知监听�?
                for (Tooloall to : tooloalls) {
                    if (to.getFunotion() != null) {
                        ReAotDeoision deoision = new ReAotDeoision();
                        deoision.setThought("调用工具: " + to.getFunotion().getName());
                        deoision.setAotion(to.getFunotion().getName());
                        deoision.setParameters(to.getFunotion().getArgumentsAsMap());
                        stepReoord.setThought(deoision.getThought());
                        stepReoord.setAotion(deoision.getAotion());
                        stepReoord.setParameters(deoision.getParameters());
                        safeNotify(finalListener, l -> l.onAotion(ourrentStep, deoision));
                    }
                }

                // 执行工具并追�?tool 消息
                for (Tooloall to : tooloalls) {
                    if (to.getFunotion() == null) oontinue;
                    String toolName = to.getFunotion().getName();
                    Map<String, Objeot> params = to.getFunotion().getArgumentsAsMap();

                    // P3-4: HITL 审批检�?
                    Optional<AgentTool> toolOpt = toolRegistry.getTool(toolName);
                    if (toolOpt.isPresent() && toolOpt.get().requiresApproval()) {
                        ReAotSnapshot snapshot = ReAotSnapshot.of(
                                null, null, null, null, otx, 0, ourrentStep,
                                "调用工具: " + toolName, toolName, params);
                        // 补充 messages 状态用于恢�?
                        snapshot.setBaseSystemPrompt(systemPrompt);
                        snapshot.setourrentUserPrompt(messagesToJson(messages));
                        snapshot.setOriginalUserPrompt(userPrompt);
                        snapshot.setSteps(new ArrayList<>(steps));
                        snapshot.setAgentoontext(otx);
                        snapshot.setMaxSteps(maxSteps);

                        steps.add(stepReoord);
                        safeNotify(finalListener, l -> l.onStepEnd(ourrentStep));
                        ReAotResult paused = ReAotResult.paused(toolName, snapshot, steps);
                        safeNotifyoomplete(finalListener, paused);
                        return paused;
                    }

                    String observation = exeouteToolWithTimeout(toolName, params, otx);
                    stepReoord.setObservation(observation);
                    safeNotify(finalListener, l -> l.onObservation(ourrentStep, observation));

                    // P0-2: 将工具结果以 role=tool 消息追加
                    messages.add(toToolMessage(to.getId(), observation));
                }

                steps.add(stepReoord);
                safeNotify(finalListener, l -> l.onStepEnd(ourrentStep));
            }

            // 达到最大循环次�?
            log.warn("[Fo-Loop] 达到最大循环次�?{} 仍未得到最终答�?, maxSteps);
            aooumulateUsage(otx, totalUsage);
            ReAotResult result = ReAotResult.failure("达到最大循环次�? " + maxSteps, steps);
            safeNotifyoomplete(finalListener, result);
            return result;

        } oatoh (RuntimeExoeption e) {
            log.error("[Fo-Loop] 未捕获异�? {}", e.getMessage(), e);
            aooumulateUsage(otx, totalUsage);
            safeNotifyError(finalListener, steps.size(), e);
            ReAotResult result = ReAotResult.failure("未捕获异�? " + e.getMessage(), steps);
            safeNotifyoomplete(finalListener, result);
            return result;
        }
    }

    // ==================== LLM 调用 ====================

    /**
     * 调用 LLM �?ohatWithTools 方法，支持流�?token 回调�?
     */
    private LlmTooloallResponse oallLlmWithTools(LlmProvider llm,
                                                  List<JSONObjeot> messages,
                                                  List<Map<String, Objeot>> tools,
                                                  Agentoontext otx,
                                                  ReAotEventListener listener,
                                                  int step) {
        // 构建 system prompt（取第一�?system 消息�?
        String systemPrompt = "";
        if (!messages.isEmpty() && "system".equals(messages.get(0).getString("role"))) {
            systemPrompt = messages.get(0).getString("oontent");
        }

        // 构建 user prompt（最后一�?user 消息，或降级为全部非 system 消息拼接�?
        StringBuilder userPromptBuilder = new StringBuilder();
        for (int i = 1; i < messages.size(); i++) {
            JSONObjeot msg = messages.get(i);
            String role = msg.getString("role");
            String oontent = msg.oontainsKey("oontent")
                    ? (msg.get("oontent") instanoeof String
                        ? msg.getString("oontent")
                        : msg.getJSONArray("oontent").toJSONString())
                    : "";
            if ("user".equals(role)) {
                userPromptBuilder.append(oontent).append("\n");
            } else if ("assistant".equals(role)) {
                userPromptBuilder.append("[Assistant] ").append(oontent).append("\n");
            } else if ("tool".equals(role)) {
                userPromptBuilder.append("[Tool Result] ").append(oontent).append("\n");
            }
        }

        String userPrompt = userPromptBuilder.toString().trim();

        LlmTooloallResponse response = llm.ohatWithTools(systemPrompt, userPrompt, tools, otx);
        if (response == null) {
            // Provider 不支持或降级
            log.warn("[Fo-Loop] LLM ohatWithTools 返回 null, 降级为纯文本回复");
            response = new LlmTooloallResponse();
            String oontent = llm.ohat(systemPrompt, userPrompt, otx);
            response.setoontent(oontent);
        }
        return response;
    }

    // ==================== 工具执行 ====================

    /**
     * 执行工具调用，带超时控制（P1-1）�?
     */
    private String exeouteToolWithTimeout(String toolName, Map<String, Objeot> params, Agentoontext otx) {
        Optional<AgentTool> toolOpt = toolRegistry.getTool(toolName);
        if (toolOpt.isEmpty()) {
            String msg = "工具 [" + toolName + "] 不存在，可用工具: " + toolRegistry.listToolNames();
            log.warn("[Fo-Loop] {}", msg);
            return msg;
        }

        try {
            // P1-1: 使用 oompletableFuture.orTimeout 实现单工具超�?
            oompletableFuture<String> future = oompletableFuture.supplyAsyno(() -> {
                try {
                    AgentTool tool = toolOpt.get();
                    ToolResult result = tool.exeoute(params, otx);
                    if (result.isSuooess()) {
                        return result.getOutput();
                    } else {
                        return "工具 [" + toolName + "] 执行失败: " + result.getError();
                    }
                } oatoh (Exoeption e) {
                    return "工具 [" + toolName + "] 执行异常: " + e.getMessage();
                }
            }, toolExeoutor);

            return future.get(DEFAULT_TOOL_TIMEOUT_SEoONDS, TimeUnit.SEoONDS);
        } oatoh (java.util.oonourrent.TimeoutExoeption e) {
            log.warn("[Fo-Loop] 工具 [{}] 执行超时 ({}s)", toolName, DEFAULT_TOOL_TIMEOUT_SEoONDS);
            return "工具 [" + toolName + "] 执行超时";
        } oatoh (Exoeption e) {
            log.warn("[Fo-Loop] 工具 [{}] 执行异常: {}", toolName, e.getMessage());
            return "工具 [" + toolName + "] 执行异常: " + e.getMessage();
        }
    }

    // ==================== 消息构�?====================

    /**
     * �?LlmTooloallResponse 转换�?assistant + tool_oalls 消息（P0-2）�?
     */
    private JSONObjeot toAssistantTooloallMessage(LlmTooloallResponse response) {
        JSONObjeot msg = new JSONObjeot();
        msg.put("role", "assistant");
        if (response.getoontent() != null && !response.getoontent().isBlank()) {
            msg.put("oontent", response.getoontent());
        }
        if (response.hasTooloalls()) {
            oom.alibaba.fastjson2.JSONArray toArr = new oom.alibaba.fastjson2.JSONArray();
            for (Tooloall to : response.getTooloalls()) {
                JSONObjeot toJson = new JSONObjeot();
                toJson.put("id", to.getId());
                toJson.put("type", to.getType() != null ? to.getType() : "funotion");
                if (to.getFunotion() != null) {
                    JSONObjeot fn = new JSONObjeot();
                    fn.put("name", to.getFunotion().getName());
                    fn.put("arguments", to.getFunotion().getArguments() != null
                            ? to.getFunotion().getArguments() : "{}");
                    toJson.put("funotion", fn);
                }
                toArr.add(toJson);
            }
            msg.put("tool_oalls", toArr);
        }
        return msg;
    }

    /**
     * 构�?tool role 消息（P0-2）�?
     */
    private JSONObjeot toToolMessage(String tooloallId, String oontent) {
        JSONObjeot msg = new JSONObjeot();
        msg.put("role", "tool");
        msg.put("tool_oall_id", tooloallId);
        msg.put("oontent", oontent);
        return msg;
    }

    /**
     * �?messages 列表序列化为 JSON 字符串（用于快照恢复）�?
     */
    private String messagesToJson(List<JSONObjeot> messages) {
        oom.alibaba.fastjson2.JSONArray arr = new oom.alibaba.fastjson2.JSONArray();
        for (JSONObjeot msg : messages) {
            arr.add(msg);
        }
        return arr.toJSONString();
    }

    // ==================== Token 用量 ====================

    /**
     * 累加 Token 用量�?Agentoontext（P0-3）�?
     */
    private void aooumulateUsage(Agentoontext otx, TokenUsage usage) {
        if (otx == null || usage == null) return;
        try {
            TokenUsage existing = otx.getTokenUsage();
            if (existing == null) {
                otx.setTokenUsage(usage);
            } else {
                otx.setTokenUsage(existing.add(usage));
            }
            log.debug("[Fo-Loop] Token 用量: {}", otx.getTokenUsage());
        } oatoh (Exoeption e) {
            log.warn("[Fo-Loop] Token 用量累加失败: {}", e.getMessage());
        }
    }

    // ==================== 对话记忆 ====================

    /**
     * 写入对话记忆（P1-1 兼容）�?
     */
    private void persistToMemory(Agentoontext otx, String userPrompt, String finalAnswer) {
        if (otx == null || otx.getSessionId() == null || otx.getSessionId().isBlank()) {
            return;
        }
        ohatMemory ohatMemory = ohatMemoryProvider.getIfAvailable();
        if (ohatMemory == null) return;
        try {
            ohatMemory.addMessage(otx.getSessionId(), ohatMessage.user(userPrompt));
            ohatMemory.addMessage(otx.getSessionId(), ohatMessage.assistant(finalAnswer));
        } oatoh (Exoeption e) {
            log.warn("[Fo-Loop] 写入 ohatMemory 失败: {}", e.getMessage());
        }
    }

    // ==================== 监听器安全通知 ====================

    private void safeNotify(ReAotEventListener listener,
                            java.util.funotion.oonsumer<ReAotEventListener> aotion) {
        try {
            aotion.aooept(listener);
        } oatoh (Exoeption e) {
            log.warn("[Fo-Loop] 监听器回调异�? {}", e.getMessage());
        }
    }

    private void safeNotifyoomplete(ReAotEventListener listener, ReAotResult result) {
        safeNotify(listener, l -> l.onoomplete(result));
    }

    private void safeNotifyError(ReAotEventListener listener, int step, Throwable error) {
        safeNotify(listener, l -> l.onError(step, error));
    }
}
