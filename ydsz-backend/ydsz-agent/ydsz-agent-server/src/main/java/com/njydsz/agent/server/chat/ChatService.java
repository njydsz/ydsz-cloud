package com.njydsz.agent.server.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.guardrail.GuardrailResult;
import com.njydsz.agent.domain.guardrail.InputGuardrail;
import com.njydsz.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;

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
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final LlmClient llmClient;
    private final ConversationMemory memory;
    private final AgentProperties properties;
    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;
    private final AgentMetrics metrics;

    public ChatService(LlmClient llmClient, ConversationMemory memory, AgentProperties properties,
                       List<InputGuardrail> inputGuardrails,
                       List<OutputGuardrail> outputGuardrails,
                       AgentMetrics metrics) {
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
        log.info("[Chat] 同步对话: convId={}, messageLen={}", convId, userMessage.length());

        String sanitizedInput = applyInputGuardrails(userMessage);
        if (sanitizedInput == null) {
            log.warn("[Chat] 输入被安全护栏拒绝: convId={}", convId);
            metrics.recordGuardrailRejection("input-guardrail", "input");
            ChatMessage rejectedMsg = ChatMessage.assistant(
                    "抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero());
            memory.save(convId, rejectedMsg);
            return new ChatResponse(UUID.randomUUID().toString(), "guardrail",
                    rejectedMsg, TokenUsage.zero(), "guardrail_rejected", List.of());
        }

        memory.save(convId, ChatMessage.user(sanitizedInput, convId));

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
            log.error("[Chat] LLM 调用失败，保存错误消息: convId={}, error={}", convId, e.getMessage());
            ChatMessage errorMsg = ChatMessage.assistant(
                    "[错误] LLM 调用失败: " + e.getMessage(), convId, TokenUsage.zero());
            memory.save(convId, errorMsg);
            throw e;
        }
        metrics.recordLlmCall(provider, model, System.currentTimeMillis() - startTime, response, null);

        String output = applyOutputGuardrails(response.getContent());
        ChatMessage assistantMsg = ChatMessage.assistant(output, convId, response.getUsage());
        memory.save(convId, assistantMsg);

        log.info("[Chat] 对话完成: convId={}, tokens={}", convId,
                response.getUsage() != null ? response.getUsage().getTotalTokens() : 0);
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
        log.info("[Chat] 流式对话: convId={}, messageLen={}", convId, userMessage.length());

        String sanitizedInput = applyInputGuardrails(userMessage);
        if (sanitizedInput == null) {
            log.warn("[Chat] 流式输入被安全护栏拒绝: convId={}", convId);
            metrics.recordGuardrailRejection("input-guardrail", "input");
            memory.save(convId, ChatMessage.assistant(
                    "抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero()));
            chunkConsumer.accept(ChatChunk.content("", "guardrail",
                    "抱歉，您的输入被安全护栏拒绝。"));
            chunkConsumer.accept(ChatChunk.finish("", "guardrail", "guardrail_rejected", null));
            return;
        }

        memory.save(convId, ChatMessage.user(sanitizedInput, convId));

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

        try {
            llmClient.stream(request, chunk -> {
                if (chunk.hasContent()) {
                    contentBuilder.append(chunk.getDeltaContent());
                }
                if (chunk.isFinished() && chunk.getUsage() != null) {
                    usage[0] = chunk.getUsage();
                }
                chunkConsumer.accept(chunk);
            });
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordLlmStream(provider, model, duration, null, e);
            log.error("[Chat] 流式 LLM 调用失败，保存错误消息: convId={}, error={}", convId, e.getMessage());
            memory.save(convId, ChatMessage.assistant(
                    "[错误] LLM 流式调用失败: " + e.getMessage(), convId, TokenUsage.zero()));
            throw e;
        }
        metrics.recordLlmStream(provider, model, System.currentTimeMillis() - startTime, usage[0], null);

        String output = applyOutputGuardrails(contentBuilder.toString());
        ChatMessage assistantMsg = ChatMessage.assistant(output, convId, usage[0]);
        memory.save(convId, assistantMsg);

        log.info("[Chat] 流式对话完成: convId={}, tokens={}", convId, usage[0].getTotalTokens());
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
        return "你是 YDSZ 项目管理信息系统的智能助手。你可以帮助用户查询项目信息、"
                + "分析项目进度、发起审批流程、发送消息通知等。请用中文回答。";
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
}
