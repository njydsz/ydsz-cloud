package com.njydsz.pmis.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.pmis.agent.domain.agent.AgentExecutor;
import com.njydsz.pmis.agent.domain.conversation.ConversationMemory;
import com.njydsz.pmis.agent.domain.gateway.LlmClient;
import com.njydsz.pmis.agent.domain.guardrail.GuardrailResult;
import com.njydsz.pmis.agent.domain.guardrail.InputGuardrail;
import com.njydsz.pmis.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.pmis.agent.domain.model.ChatChunk;
import com.njydsz.pmis.agent.domain.model.ChatMessage;
import com.njydsz.pmis.agent.domain.model.ChatRequest;
import com.njydsz.pmis.agent.domain.model.ChatResponse;
import com.njydsz.pmis.agent.domain.model.TokenUsage;
import com.njydsz.pmis.agent.server.config.AgentProperties;

/**
 * 简单 Agent 执行器（单轮 LLM 调用，无工具）
 *
 * <p>最基础的 Agent 模式：System Prompt + 历史消息 + 用户消息 → LLM → 响应。
 * 适用于不需要工具调用的纯对话场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class SimpleAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(SimpleAgentExecutor.class);

    private final LlmClient llmClient;
    private final ConversationMemory memory;
    private final AgentProperties properties;
    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;

    public SimpleAgentExecutor(LlmClient llmClient, ConversationMemory memory,
                               AgentProperties properties,
                               List<InputGuardrail> inputGuardrails,
                               List<OutputGuardrail> outputGuardrails) {
        this.llmClient = llmClient;
        this.memory = memory;
        this.properties = properties;
        this.inputGuardrails = inputGuardrails != null ? inputGuardrails : List.of();
        this.outputGuardrails = outputGuardrails != null ? outputGuardrails : List.of();
    }

    @Override
    public ChatResponse execute(AgentExecutionRequest request) {
        String convId = request.getConversationId() != null
                ? request.getConversationId() : UUID.randomUUID().toString();
        log.info("[Simple-Agent] 执行: convId={}", convId);

        String userInput = applyInputGuardrails(request.getUserInput());
        if (userInput == null) {
            ChatMessage msg = ChatMessage.assistant("抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero());
            return new ChatResponse(UUID.randomUUID().toString(), "guardrail",
                    msg, TokenUsage.zero(), "guardrail_rejected", List.of());
        }

        List<ChatMessage> messages = new ArrayList<>();
        String systemPrompt = request.getSystemPrompt() != null
                ? request.getSystemPrompt()
                : "你是 PMIS 项目管理信息系统的智能助手。请用中文回答。";
        messages.add(ChatMessage.system(systemPrompt));
        messages.addAll(memory.load(convId, properties.getMemory().getMaxMessages()));
        messages.add(ChatMessage.user(userInput, convId));

        ChatRequest llmRequest = ChatRequest.builder()
                .model(properties.getLlm().getDefaultModel())
                .messages(messages)
                .temperature(properties.getLlm().getTemperature())
                .maxTokens(properties.getLlm().getMaxTokens())
                .build();

        ChatResponse response = llmClient.chat(llmRequest);
        String output = applyOutputGuardrails(response.getContent());

        memory.save(convId, ChatMessage.user(userInput, convId));
        memory.save(convId, ChatMessage.assistant(output, convId, response.getUsage()));

        log.info("[Simple-Agent] 完成: convId={}, tokens={}",
                convId, response.getUsage() != null ? response.getUsage().getTotalTokens() : 0);
        return new ChatResponse(response.getId(), response.getModel(),
                ChatMessage.assistant(output, convId, response.getUsage()),
                response.getUsage(), response.getFinishReason(), List.of());
    }

    @Override
    public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
        ChatResponse response = execute(request);
        chunkConsumer.accept(ChatChunk.content(response.getId(), response.getModel(),
                response.getContent()));
        chunkConsumer.accept(ChatChunk.finish(response.getId(), response.getModel(),
                "stop", response.getUsage()));
    }

    @Override
    public String getType() {
        return "chat";
    }

    @Override
    public boolean supports(String type) {
        return "chat".equalsIgnoreCase(type) || "simple".equalsIgnoreCase(type);
    }

    private String applyInputGuardrails(String input) {
        String sanitized = input;
        for (InputGuardrail guard : inputGuardrails) {
            GuardrailResult result = guard.check(sanitized);
            if (result.isRejected()) {
                return null;
            }
            if (result.getSanitizedInput() != null) {
                sanitized = result.getSanitizedInput();
            }
        }
        return sanitized;
    }

    private String applyOutputGuardrails(String output) {
        String sanitized = output;
        for (OutputGuardrail guard : outputGuardrails) {
            GuardrailResult result = guard.check(sanitized);
            if (result.isRejected()) {
                return "抱歉，我无法回答这个问题。";
            }
            if (result.getSanitizedInput() != null) {
                sanitized = result.getSanitizedInput();
            }
        }
        return sanitized;
    }
}
