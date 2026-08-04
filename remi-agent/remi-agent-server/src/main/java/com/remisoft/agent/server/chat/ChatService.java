package com.remisoft.agent.server.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

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
import com.remisoft.agent.domain.trace.TraceRecorder;
import com.remisoft.agent.server.analytics.CostAnalysisService;
import com.remisoft.agent.server.config.AgentProperties;
import com.remisoft.agent.server.metrics.AgentMetrics;
import com.remisoft.agent.server.metrics.AgentRuntimeMetrics;
import com.remisoft.common.event.model.StandardEventTypes;
import com.remisoft.common.event.service.OutboxService;
import com.remisoft.common.json.YdszJson;

/**
 * 对话服务
 *
 * <p>提供同步和流式两种对话模式：
 * <ul>
 *   <li>{@link #chat} — 同步调用，返回完整响应</li>
 *   <li>{@link #stream} — 流式调用，逐 token 回调</li>
 * </ul>
 *
 * <p>对话流程：
 * <ol>
 *   <li>加载历史消息（滑动窗口）</li>
 *   <li>拼接 System Prompt + 历史消息 + 用户消息</li>
 *   <li>调用 LLM 获取响应</li>
 *   <li>保存用户消息和助手响应到记忆</li>
 *   <li>返回响应</li>
 * </ol>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** LLM 客户端 */
    private final LlmClient llmClient;
    /** 对话记忆 */
    private final ConversationMemory memory;
    /** Agent 配置属性 */
    private final AgentProperties properties;
    /** 输入护栏列表（按优先级排序） */
    private final List<InputGuardrail> inputGuardrails;
    /** 输出护栏列表（按优先级排序） */
    private final List<OutputGuardrail> outputGuardrails;
    /** Agent 指标采集 */
    private final AgentMetrics metrics;
    /** Agent 运行态指标采集（P2 增强：活跃度、执行耗时、消息量等） */
    private final AgentRuntimeMetrics runtimeMetrics;
    /** 成本分析服务 */
    private final CostAnalysisService costAnalysisService;
    /** 链路记录器 */
    private final TraceRecorder traceRecorder;
    /** Outbox 事件服务（可选依赖） */
    private final ObjectProvider<OutboxService> outboxServiceProvider;

    public ChatService(LlmClient llmClient, ConversationMemory memory, AgentProperties properties,
                       List<InputGuardrail> inputGuardrails,
                       List<OutputGuardrail> outputGuardrails,
                       AgentMetrics metrics,
                       AgentRuntimeMetrics runtimeMetrics,
                       CostAnalysisService costAnalysisService,
                       TraceRecorder traceRecorder,
                       ObjectProvider<OutboxService> outboxServiceProvider) {
        this.llmClient = llmClient;
        this.memory = memory;
        this.properties = properties;
        this.inputGuardrails = inputGuardrails != null
                ? inputGuardrails.stream().sorted(Comparator.comparingInt(InputGuardrail::getPriority)).toList()
                : List.of();
        this.outputGuardrails = outputGuardrails != null
                ? outputGuardrails.stream().sorted(Comparator.comparingInt(OutputGuardrail::getPriority)).toList()
                : List.of();
        this.metrics = metrics;
        this.runtimeMetrics = runtimeMetrics;
        this.costAnalysisService = costAnalysisService;
        this.traceRecorder = traceRecorder;
        this.outboxServiceProvider = outboxServiceProvider;
    }

    /**
     * 同步对话
     *
     * <p>流程：
     * <ol>
     *   <li>应用输入护栏（Prompt 注入检测、PII 脱敏）</li>
     *   <li>预保存用户消息（LLM 调用前持久化，避免崩溃丢失）</li>
     *   <li>调用 LLM</li>
     *   <li>应用输出护栏</li>
     *   <li>保存助手响应</li>
     * </ol>
     *
     * @param conversationId 对话 ID（null 则新建）
     * @param userMessage    用户消息
     * @param systemPrompt   系统提示词（null 则使用默认）
     * @return 助手回复
     */
    public ChatResponse chat(String conversationId, String userMessage, String systemPrompt) {
        String convId = conversationId != null ? conversationId : UUID.randomUUID().toString();
        String traceId = traceRecorder.startTrace(convId, "CHAT");
        log.info("[Chat] 同步对话: convId={}, traceId={}, messageLen={}", convId, traceId, userMessage.length());

        // P2: 运行态指标埋点 — 标记会话活跃
        runtimeMetrics.markConversationActive();

        String sanitizedInput = applyInputGuardrails(userMessage);
        if (sanitizedInput == null) {
            log.warn("[Chat] 输入被安全护栏拒绝: convId={}", convId);
            metrics.recordGuardrailRejection("input-guardrail", "input");
            traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
            ChatMessage rejectedMsg = ChatMessage.assistant(
                    "抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero());
            memory.save(convId, rejectedMsg);
            runtimeMetrics.recordMessage("assistant");
            runtimeMetrics.recordExecution("simple", false, 0);
            return new ChatResponse(UUID.randomUUID().toString(), "guardrail",
                    rejectedMsg, TokenUsage.zero(), "guardrail_rejected", List.of());
        }

        memory.save(convId, ChatMessage.user(sanitizedInput, convId));
        // P2: 记录用户消息
        runtimeMetrics.recordMessage("user");

        List<ChatMessage> messages = buildMessages(convId, sanitizedInput, systemPrompt);
        ChatRequest request = ChatRequest.builder()
                .model(properties.getLlm().getDefaultModel())
                .messages(messages)
                .temperature(properties.getLlm().getTemperature())
                .maxTokens(properties.getLlm().getMaxTokens())
                .build();

        String model = properties.getLlm().getDefaultModel();
        String provider = llmClient.getProvider();
        long startTime = System.currentTimeMillis();
        ChatResponse response;
        try {
            response = llmClient.chat(request);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordLlmCall(provider, model, duration, null, e);
            traceRecorder.recordStep(traceId, "LLM_CALL_ERROR", "LLM call failed", request, e.getMessage(), duration);
            traceRecorder.endTrace(traceId, "FAILED");
            // P2: 运行态指标埋点 — 执行失败
            runtimeMetrics.recordExecution("simple", false, duration);
            log.error("[Chat] LLM 调用失败，保存错误消息: convId={}, error={}", convId, e.getMessage());
            ChatMessage errorMsg = ChatMessage.assistant(
                    "[错误] LLM 调用失败: " + e.getMessage(), convId, TokenUsage.zero());
            memory.save(convId, errorMsg);
            throw e;
        }
        long duration = System.currentTimeMillis() - startTime;
        metrics.recordLlmCall(provider, model, duration, response, null);
        if (response.getUsage() != null && costAnalysisService != null) {
            costAnalysisService.recordUsage(convId, model, response.getUsage());
        }
        traceRecorder.recordStep(traceId, "LLM_CALL", "Chat LLM call", request, response, duration);

        String output = applyOutputGuardrails(response.getContent());
        ChatMessage assistantMsg = ChatMessage.assistant(output, convId, response.getUsage());
        memory.save(convId, assistantMsg);
        // P2: 运行态指标埋点 — 助手消息 + Agent 执行成功
        runtimeMetrics.recordMessage("assistant");
        runtimeMetrics.recordExecution("simple", true, duration);

        log.info("[Chat] 对话完成: convId={}, tokens={}", convId,
                response.getUsage() != null ? response.getUsage().getTotalTokens() : 0);
        traceRecorder.endTrace(traceId, "SUCCESS");
        publishEvent(StandardEventTypes.CONVERSATION_CREATED, convId, response);
        return new ChatResponse(response.getId(), response.getModel(),
                assistantMsg, response.getUsage(), response.getFinishReason(), List.of());
    }

    /**
     * 流式对话
     *
     * <p>流程与 {@link #chat} 一致，区别在于 LLM 响应通过 SSE 逐 token 推送。
     * 用户消息在 LLM 调用前预保存，LLM 失败时保存错误消息。
     *
     * @param conversationId 对话 ID（null 则新建）
     * @param userMessage    用户消息
     * @param systemPrompt   系统提示词（null 则使用默认）
     * @param chunkConsumer  流式片段消费者
     */
    public void stream(String conversationId, String userMessage, String systemPrompt,
                       Consumer<ChatChunk> chunkConsumer) {
        String convId = conversationId != null ? conversationId : UUID.randomUUID().toString();
        String traceId = traceRecorder.startTrace(convId, "CHAT_STREAM");
        log.info("[Chat-Stream] 流式对话: convId={}, traceId={}, messageLen={}", convId, traceId, userMessage.length());

        // P2: 运行态指标埋点 — 标记会话活跃
        runtimeMetrics.markConversationActive();

        String sanitizedInput = applyInputGuardrails(userMessage);
        if (sanitizedInput == null) {
            log.warn("[Chat-Stream] 流式输入被安全护栏拒绝: convId={}", convId);
            metrics.recordGuardrailRejection("input-guardrail", "input");
            traceRecorder.recordStep(traceId, "GUARDRAIL_REJECT_INPUT",
                    "Input rejected by guardrail", userMessage, "rejected", 0);
            traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
            memory.save(convId, ChatMessage.assistant(
                    "抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero()));
            runtimeMetrics.recordMessage("assistant");
            runtimeMetrics.recordExecution("simple", false, 0);
            chunkConsumer.accept(ChatChunk.content("", "guardrail",
                    "抱歉，您的输入被安全护栏拒绝。"));
            chunkConsumer.accept(ChatChunk.finish("", "guardrail", "guardrail_rejected", null));
            return;
        }

        memory.save(convId, ChatMessage.user(sanitizedInput, convId));
        // P2: 记录用户消息
        runtimeMetrics.recordMessage("user");

        List<ChatMessage> messages = buildMessages(convId, sanitizedInput, systemPrompt);
        ChatRequest request = ChatRequest.builder()
                .model(properties.getLlm().getDefaultModel())
                .messages(messages)
                .temperature(properties.getLlm().getTemperature())
                .maxTokens(properties.getLlm().getMaxTokens())
                .stream(true)
                .build();

        String model = properties.getLlm().getDefaultModel();
        String provider = llmClient.getProvider();
        long startTime = System.currentTimeMillis();
        StringBuilder contentBuilder = new StringBuilder();
        final TokenUsage[] usage = {TokenUsage.zero()};
        // P2: 流式首 Token 测 TTFT
        final boolean[] firstTokenRecorded = {false};

        try {
            llmClient.stream(request, chunk -> {
                if (!firstTokenRecorded[0] && chunk.hasContent()) {
                    long ttftMs = System.currentTimeMillis() - startTime;
                    runtimeMetrics.recordTtft(provider, model, ttftMs);
                    firstTokenRecorded[0] = true;
                }
                if (chunk.hasContent()) {
                    contentBuilder.append(chunk.getDeltaContent());
                }
                if (chunk.isFinished() && chunk.getUsage() != null) {
                    usage[0] = chunk.getUsage();
                    // P2: 记录完整流式调用结果
                    long duration = System.currentTimeMillis() - startTime;
                    if (!firstTokenRecorded[0]) {
                        runtimeMetrics.recordTtft(provider, model, duration);
                    }
                }
                chunkConsumer.accept(chunk);
            });
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordLlmStream(provider, model, duration, null, e);
            traceRecorder.recordStep(traceId, "LLM_CALL_ERROR",
                    "Stream LLM call failed", request, e.getMessage(), duration);
            traceRecorder.endTrace(traceId, "FAILED");
            // P2: 运行态指标埋点 — 执行失败
            runtimeMetrics.recordExecution("simple", false, duration);
            log.error("[Chat-Stream] 流式 LLM 调用失败，保存错误消息: convId={}, error={}", convId, e.getMessage());
            memory.save(convId, ChatMessage.assistant(
                    "[错误] LLM 流式调用失败: " + e.getMessage(), convId, TokenUsage.zero()));
            throw e;
        }
        long duration = System.currentTimeMillis() - startTime;
        metrics.recordLlmStream(provider, model, duration, usage[0], null);
        if (usage[0] != null && !usage[0].equals(TokenUsage.zero()) && costAnalysisService != null) {
            costAnalysisService.recordUsage(convId, model, usage[0]);
        }
        traceRecorder.recordStep(traceId, "LLM_CALL",
                "Stream LLM call", request, contentBuilder.toString(), duration);

        String output = applyOutputGuardrails(contentBuilder.toString());
        ChatMessage assistantMsg = ChatMessage.assistant(output, convId, usage[0]);
        memory.save(convId, assistantMsg);
        // P2: 运行态指标埋点 — 助手消息 + Agent 执行成功
        runtimeMetrics.recordMessage("assistant");
        runtimeMetrics.recordExecution("simple", true, duration);

        traceRecorder.endTrace(traceId, "SUCCESS");
        log.info("[Chat-Stream] 流式对话完成: convId={}, tokens={}", convId, usage[0].getTotalTokens());
    }

    /**
     * 获取对话历史
     */
    public List<ChatMessage> getHistory(String conversationId) {
        return memory.load(conversationId, properties.getMemory().getMaxMessages());
    }

    /**
     * 清除对话历史
     */
    public void clearHistory(String conversationId) {
        memory.clear(conversationId);
    }

    private List<ChatMessage> buildMessages(String conversationId, String userMessage,
                                             String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        String prompt = systemPrompt != null ? systemPrompt : getDefaultSystemPrompt();
        messages.add(ChatMessage.system(prompt));
        List<ChatMessage> history = memory.load(conversationId,
                properties.getMemory().getMaxMessages());
        messages.addAll(history);
        messages.add(ChatMessage.user(userMessage, conversationId));
        return messages;
    }

    private String getDefaultSystemPrompt() {
        return properties.getDefaultSystemPrompt();
    }

    /**
     * 应用输入护栏（按优先级排序执行）
     *
     * @return 脱敏后的输入；null 表示被拒绝
     */
    private String applyInputGuardrails(String input) {
        String sanitized = input;
        for (InputGuardrail guard : inputGuardrails) {
            GuardrailResult result = guard.check(sanitized);
            if (result.isRejected()) {
                log.warn("[Chat] 输入护栏拒绝: guard={}, reason={}", guard.getName(), result.getReason());
                metrics.recordGuardrailRejection(guard.getName(), "input");
                return null;
            }
            if (result.getSanitizedInput() != null) {
                sanitized = result.getSanitizedInput();
            }
        }
        return sanitized;
    }

    /**
     * 应用输出护栏（按优先级排序执行）
     *
     * @return 脱敏后的输出
     */
    private String applyOutputGuardrails(String output) {
        String sanitized = output;
        for (OutputGuardrail guard : outputGuardrails) {
            GuardrailResult result = guard.check(sanitized);
            if (result.isRejected()) {
                log.warn("[Chat] 输出护栏拒绝: guard={}, reason={}", guard.getName(), result.getReason());
                metrics.recordGuardrailRejection(guard.getName(), "output");
                return "抱歉，我无法回答这个问题。";
            }
            if (result.getSanitizedInput() != null) {
                sanitized = result.getSanitizedInput();
            }
        }
        return sanitized;
    }

    /**
     * 发布领域事件到 Outbox（可选依赖，不存在时安全降级）
     */
    private void publishEvent(String eventType, String aggregateId, Object payload) {
        OutboxService outboxService = outboxServiceProvider.getIfAvailable();
        if (outboxService == null) {
            log.debug("[Chat] OutboxService not available, skipping event: type={}, id={}", eventType, aggregateId);
            return;
        }
        try {
            outboxService.appendToOutbox("Conversation", aggregateId, eventType,
                    YdszJson.toJson(payload));
        } catch (Exception e) {
            log.warn("[Chat] Failed to publish outbox event: type={}, id={}, error={}",
                    eventType, aggregateId, e.getMessage());
        }
    }
}
