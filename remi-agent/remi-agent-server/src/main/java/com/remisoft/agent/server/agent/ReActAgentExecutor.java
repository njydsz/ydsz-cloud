package com.remisoft.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.agent.domain.agent.AgentExecutionRequest;
import com.remisoft.agent.domain.agent.AgentExecutor;
import com.remisoft.agent.domain.conversation.ConversationMemory;
import com.remisoft.agent.domain.gateway.LlmClient;
import com.remisoft.agent.domain.guardrail.GuardrailResult;
import com.remisoft.agent.domain.guardrail.InputGuardrail;
import com.remisoft.agent.domain.guardrail.OutputGuardrail;
import com.remisoft.agent.domain.model.ChatChunk;
import com.remisoft.agent.domain.model.ChatMessage;
import com.remisoft.agent.domain.model.ChatRequest;
import com.remisoft.agent.domain.model.ChatResponse;
import com.remisoft.agent.domain.model.TokenUsage;
import com.remisoft.agent.domain.model.ToolCall;
import com.remisoft.agent.domain.tool.ToolRegistry;
import com.remisoft.agent.domain.trace.TraceRecorder;
import com.remisoft.agent.server.analytics.CostAnalysisService;
import com.remisoft.agent.server.config.AgentProperties;
import com.remisoft.agent.server.metrics.AgentMetrics;

/**
 * ReAct Agent 执行器
 *
 * <p>实现 ReAct（Reasoning + Acting）模式：
 * <pre>
 * Thought → Action (Tool Call) → Observation (Tool Result) → Thought → ... → Final Answer
 * </pre>
 *
 * <p>可观测性：
 * <ul>
 *   <li>{@link TraceRecorder} — 记录每次 LLM 调用和工具执行步骤</li>
 *   <li>{@link AgentMetrics} — 采集 LLM 调用耗时/Token/状态指标</li>
 *   <li>{@link CostAnalysisService} — 核算 Token 用量成本</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class ReActAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentExecutor.class);

    /** LLM 客户端 */
    private final LlmClient llmClient;
    /** 对话记忆 */
    private final ConversationMemory memory;
    /** 工具注册中心 */
    private final ToolRegistry toolRegistry;
    /** Agent 配置属性 */
    private final AgentProperties properties;
    /** 输入护栏列表 */
    private final List<InputGuardrail> inputGuardrails;
    /** 输出护栏列表 */
    private final List<OutputGuardrail> outputGuardrails;
    /** 链路记录器 */
    private final TraceRecorder traceRecorder;
    /** Agent 指标采集 */
    private final AgentMetrics agentMetrics;
    /** 成本分析服务（Token 用量核算，可为 null，调用处已做空判断） */
    private final CostAnalysisService costAnalysisService;

    public ReActAgentExecutor(LlmClient llmClient, ConversationMemory memory,
                              ToolRegistry toolRegistry, AgentProperties properties,
                              List<InputGuardrail> inputGuardrails,
                              List<OutputGuardrail> outputGuardrails,
                              TraceRecorder traceRecorder,
                              AgentMetrics agentMetrics,
                              CostAnalysisService costAnalysisService) {
        this.llmClient = llmClient;
        this.memory = memory;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.inputGuardrails = inputGuardrails != null ? inputGuardrails : List.of();
        this.outputGuardrails = outputGuardrails != null ? outputGuardrails : List.of();
        this.traceRecorder = traceRecorder;
        this.agentMetrics = agentMetrics;
        this.costAnalysisService = costAnalysisService;
    }

    @Override
    public ChatResponse execute(AgentExecutionRequest request) {
        String convId = request.getConversationId() != null
                ? request.getConversationId() : UUID.randomUUID().toString();
        String traceId = traceRecorder.startTrace(convId, "REACT");
        log.info("[ReAct] 开始执行: convId={}, traceId={}, maxIterations={}",
                convId, traceId, request.getMaxIterations());

        String userInput = applyInputGuardrails(request.getUserInput(), traceId);
        if (userInput == null) {
            agentMetrics.recordGuardrailRejection("input-guardrail", "input");
            traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
            return buildRejectedResponse("输入被护栏拒绝");
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(request)));
        messages.addAll(memory.load(convId, properties.getMemory().getMaxMessages()));
        messages.add(ChatMessage.user(userInput, convId));

        TokenUsage totalUsage = TokenUsage.zero();

        for (int i = 0; i < request.getMaxIterations(); i++) {
            ChatRequest llmRequest = ChatRequest.builder()
                    .model(properties.getLlm().getDefaultModel())
                    .messages(messages)
                    .temperature(properties.getLlm().getTemperature())
                    .maxTokens(properties.getLlm().getMaxTokens())
                    .tools(toolRegistry.getToolDefinitions().stream()
                            .map(td -> td)
                            .collect(Collectors.toList()))
                    .build();

            long llmStart = System.currentTimeMillis();
            ChatResponse response;
            try {
                response = llmClient.chat(llmRequest);
            } catch (Exception e) {
                long llmDuration = System.currentTimeMillis() - llmStart;
                agentMetrics.recordLlmCall(llmClient.getProvider(),
                        properties.getLlm().getDefaultModel(),
                        llmDuration, null, e);
                traceRecorder.recordStep(traceId, "LLM_CALL_ERROR",
                        "LLM 调用失败 (iteration=" + i + ")",
                        request.getUserInput(), e.getMessage(), llmDuration);
                traceRecorder.endTrace(traceId, "FAILED");
                throw e;
            }
            long llmDuration = System.currentTimeMillis() - llmStart;

            if (response.getUsage() != null) {
                totalUsage = totalUsage.add(response.getUsage());
            }

            // P0-3: AgentMetrics 指标采集
            agentMetrics.recordLlmCall(llmClient.getProvider(),
                    properties.getLlm().getDefaultModel(),
                    llmDuration, response, null);

            // P0-2: CostAnalysisService 成本核算
            if (response.getUsage() != null && costAnalysisService != null) {
                costAnalysisService.recordUsage(convId,
                        properties.getLlm().getDefaultModel(), response.getUsage());
            }

            // P0-1: TraceRecorder 记录 LLM 调用步骤
            traceRecorder.recordStep(traceId, "LLM_CALL",
                    "ReAct iteration " + (i + 1),
                    messages, response, llmDuration);

            if (!response.hasToolCalls()) {
                String output = applyOutputGuardrails(response.getContent(), traceId);
                memory.save(convId, ChatMessage.user(userInput, convId));
                memory.save(convId, ChatMessage.assistant(output, convId, response.getUsage()));
                traceRecorder.endTrace(traceId, "SUCCESS");
                log.info("[ReAct] 完成: convId={}, iterations={}, tokens={}",
                        convId, i + 1, totalUsage.getTotalTokens());
                return new ChatResponse(response.getId(), response.getModel(),
                        ChatMessage.assistant(output, convId, totalUsage),
                        totalUsage, "stop", List.of());
            }

            messages.add(response.getMessage());
            for (ToolCall toolCall : response.getToolCalls()) {
                log.info("[ReAct] 执行工具: {}", toolCall.getName());
                long toolStart = System.currentTimeMillis();
                String result = toolRegistry.execute(toolCall);
                long toolDuration = System.currentTimeMillis() - toolStart;

                // P0-1: TraceRecorder 记录工具调用步骤
                traceRecorder.recordStep(traceId, "TOOL_CALL",
                        toolCall.getName(), toolCall.getArguments(),
                        result, toolDuration);

                ChatMessage toolMsg = ChatMessage.tool(toolCall.getId(), result, convId);
                messages.add(toolMsg);
            }
        }

        log.warn("[ReAct] 超过最大迭代次数: convId={}", convId);
        traceRecorder.endTrace(traceId, "MAX_ITERATIONS");
        return buildMaxIterationsResponse(convId, totalUsage);
    }

    @Override
    public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
        String convId = request.getConversationId() != null
                ? request.getConversationId() : UUID.randomUUID().toString();
        String traceId = traceRecorder.startTrace(convId, "REACT_STREAM");
        log.info("[ReAct-Stream] 开始流式执行: convId={}, traceId={}", convId, traceId);

        String responseId = UUID.randomUUID().toString();
        String model = properties.getLlm().getDefaultModel();
        TokenUsage totalUsage = TokenUsage.zero();

        String userInput = applyInputGuardrails(request.getUserInput(), traceId);
        if (userInput == null) {
            agentMetrics.recordGuardrailRejection("input-guardrail", "input");
            traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
            chunkConsumer.accept(ChatChunk.content(responseId, model,
                    "抱歉，您的输入被安全护栏拒绝。"));
            chunkConsumer.accept(ChatChunk.finish(responseId, model, "guardrail_rejected", null));
            return;
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(request)));
        messages.addAll(memory.load(convId, properties.getMemory().getMaxMessages()));
        messages.add(ChatMessage.user(userInput, convId));

        for (int i = 0; i < request.getMaxIterations(); i++) {
            ChatRequest llmRequest = ChatRequest.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(properties.getLlm().getTemperature())
                    .maxTokens(properties.getLlm().getMaxTokens())
                    .tools(toolRegistry.getToolDefinitions().stream()
                            .map(td -> td)
                            .collect(Collectors.toList()))
                    .build();

            long llmStart = System.currentTimeMillis();
            ChatResponse response;
            try {
                response = llmClient.chat(llmRequest);
            } catch (Exception e) {
                long llmDuration = System.currentTimeMillis() - llmStart;
                agentMetrics.recordLlmCall(llmClient.getProvider(), model, llmDuration, null, e);
                traceRecorder.endTrace(traceId, "FAILED");
                chunkConsumer.accept(ChatChunk.content(responseId, model,
                        "[错误] LLM 调用失败: " + e.getMessage()));
                chunkConsumer.accept(ChatChunk.finish(responseId, model, "error", null));
                throw e;
            }
            long llmDuration = System.currentTimeMillis() - llmStart;

            if (response.getUsage() != null) {
                totalUsage = totalUsage.add(response.getUsage());
            }
            agentMetrics.recordLlmCall(llmClient.getProvider(), model, llmDuration, response, null);
            if (response.getUsage() != null && costAnalysisService != null) {
                costAnalysisService.recordUsage(convId, model, response.getUsage());
            }
            traceRecorder.recordStep(traceId, "LLM_CALL",
                    "ReAct iteration " + (i + 1), messages, response, llmDuration);

            // P0-6: 推送 LLM 回复内容（Thought / Final Answer）
            if (response.getContent() != null && !response.getContent().isBlank()) {
                String prefix = i > 0 ? "\n\n[思考" + (i + 1) + "] " : "";
                chunkConsumer.accept(ChatChunk.content(responseId, model, prefix + response.getContent()));
            }

            if (!response.hasToolCalls()) {
                String output = applyOutputGuardrails(response.getContent(), traceId);
                memory.save(convId, ChatMessage.user(userInput, convId));
                memory.save(convId, ChatMessage.assistant(output, convId, totalUsage));
                traceRecorder.endTrace(traceId, "SUCCESS");
                chunkConsumer.accept(ChatChunk.finish(responseId, model, "stop", totalUsage));
                return;
            }

            // 推送工具调用事件
            messages.add(response.getMessage());
            for (ToolCall toolCall : response.getToolCalls()) {
                chunkConsumer.accept(ChatChunk.content(responseId, model,
                        "\n\n[工具调用] " + toolCall.getName() + "..."));
                long toolStart = System.currentTimeMillis();
                String result = toolRegistry.execute(toolCall);
                long toolDuration = System.currentTimeMillis() - toolStart;
                traceRecorder.recordStep(traceId, "TOOL_CALL",
                        toolCall.getName(), toolCall.getArguments(), result, toolDuration);
                chunkConsumer.accept(ChatChunk.content(responseId, model,
                        "\n[工具结果] " + truncateResult(result)));
                ChatMessage toolMsg = ChatMessage.tool(toolCall.getId(), result, convId);
                messages.add(toolMsg);
            }
        }

        log.warn("[ReAct-Stream] 超过最大迭代次数: convId={}", convId);
        traceRecorder.endTrace(traceId, "MAX_ITERATIONS");
        chunkConsumer.accept(ChatChunk.content(responseId, model,
                "\n\n抱歉，我已达到最大推理次数限制，无法完成此任务。"));
        chunkConsumer.accept(ChatChunk.finish(responseId, model, "max_iterations", totalUsage));
    }

    private String truncateResult(String result) {
        if (result == null) {
            return "";
        }
        return result.length() > 200 ? result.substring(0, 200) + "..." : result;
    }

    @Override
    public String getType() {
        return "react";
    }

    @Override
    public boolean supports(String type) {
        return "react".equalsIgnoreCase(type) || "react_agent".equalsIgnoreCase(type);
    }

    private String applyInputGuardrails(String input, String traceId) {
        String sanitized = input;
        for (InputGuardrail guard : inputGuardrails) {
            GuardrailResult result = guard.check(sanitized);
            if (result.isRejected()) {
                log.warn("[ReAct] 输入被护栏拒绝: {} - {}", guard.getName(), result.getReason());
                traceRecorder.recordStep(traceId, "GUARDRAIL_REJECT_INPUT",
                        guard.getName(), input, result.getReason(), 0);
                return null;
            }
            if (result.getSanitizedInput() != null) {
                sanitized = result.getSanitizedInput();
            }
        }
        return sanitized;
    }

    private String applyOutputGuardrails(String output, String traceId) {
        String sanitized = output;
        for (OutputGuardrail guard : outputGuardrails) {
            GuardrailResult result = guard.check(sanitized);
            if (result.isRejected()) {
                log.warn("[ReAct] 输出被护栏拒绝: {} - {}", guard.getName(), result.getReason());
                agentMetrics.recordGuardrailRejection(guard.getName(), "output");
                traceRecorder.recordStep(traceId, "GUARDRAIL_REJECT_OUTPUT",
                        guard.getName(), output, result.getReason(), 0);
                return "抱歉，我无法回答这个问题。";
            }
            if (result.getSanitizedInput() != null) {
                sanitized = result.getSanitizedInput();
            }
        }
        return sanitized;
    }

    private String buildSystemPrompt(AgentExecutionRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getSystemPrompt() != null) {
            sb.append(request.getSystemPrompt());
        } else {
            sb.append("你是 REMI 项目管理信息系统的智能助手。你可以使用工具来帮助用户完成任务。");
        }
        if (toolRegistry.size() > 0) {
            sb.append("\n\n你可以使用以下工具：\n");
            for (var tool : toolRegistry.getToolDefinitions()) {
                sb.append("- ").append(tool.getName());
                if (tool.getDescription() != null) {
                    sb.append(": ").append(tool.getDescription());
                }
                sb.append("\n");
            }
            sb.append("\n请根据用户需求决定是否使用工具。如果不需要工具，直接回答即可。");
        }
        return sb.toString();
    }

    private ChatResponse buildRejectedResponse(String reason) {
        ChatMessage msg = ChatMessage.assistant("抱歉，" + reason + "。", null, TokenUsage.zero());
        return new ChatResponse(UUID.randomUUID().toString(), "guardrail",
                msg, TokenUsage.zero(), "guardrail_rejected", List.of());
    }

    private ChatResponse buildMaxIterationsResponse(String convId, TokenUsage usage) {
        ChatMessage msg = ChatMessage.assistant(
                "抱歉，我已达到最大推理次数限制，无法完成此任务。请尝试简化您的问题。",
                convId, usage);
        return new ChatResponse(UUID.randomUUID().toString(),
                properties.getLlm().getDefaultModel(), msg, usage, "max_iterations", List.of());
    }
}
