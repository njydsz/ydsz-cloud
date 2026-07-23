package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.AgentExecutor;
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
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.rag.RagService;

/**
 * RAG 增强 Agent 执行器
 *
 * <p>在 LLM 调用前先检索知识库，将检索到的上下文注入 System Prompt，
 * 使 LLM 能够基于私有知识回答问题。
 *
 * <p>执行流程：
 * <ol>
 *   <li>输入护栏检查</li>
 *   <li>RAG 检索（向量相似度搜索）</li>
 *   <li>构建 System Prompt = 基础指令 + 检索上下文</li>
 *   <li>调用 LLM（含历史消息）</li>
 *   <li>输出护栏检查</li>
 *   <li>返回响应 + 引用来源</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class RagAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(RagAgentExecutor.class);

    private final LlmClient llmClient;
    private final ConversationMemory memory;
    private final AgentProperties properties;
    private final RagService ragService;
    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;

    public RagAgentExecutor(LlmClient llmClient, ConversationMemory memory,
                            AgentProperties properties, RagService ragService,
                            List<InputGuardrail> inputGuardrails,
                            List<OutputGuardrail> outputGuardrails) {
        this.llmClient = llmClient;
        this.memory = memory;
        this.properties = properties;
        this.ragService = ragService;
        this.inputGuardrails = inputGuardrails != null ? inputGuardrails : List.of();
        this.outputGuardrails = outputGuardrails != null ? outputGuardrails : List.of();
    }

    @Override
    public ChatResponse execute(AgentExecutionRequest request) {
        String convId = request.getConversationId() != null
                ? request.getConversationId() : UUID.randomUUID().toString();
        log.info("[RAG-Agent] 执行: convId={}", convId);

        String userInput = applyInputGuardrails(request.getUserInput());
        if (userInput == null) {
            ChatMessage msg = ChatMessage.assistant("抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero());
            return new ChatResponse(UUID.randomUUID().toString(), "guardrail",
                    msg, TokenUsage.zero(), "guardrail_rejected", List.of());
        }

        List<TextChunk> retrievedChunks = ragService.retrieve(userInput);
        String ragContext = ragService.buildContext(retrievedChunks);

        String systemPrompt = buildSystemPrompt(request, ragContext);
        List<ChatMessage> messages = new ArrayList<>();
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

        log.info("[RAG-Agent] 完成: convId={}, retrieved={}, tokens={}",
                convId, retrievedChunks.size(),
                response.getUsage() != null ? response.getUsage().getTotalTokens() : 0);
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
        return "rag";
    }

    @Override
    public boolean supports(String type) {
        return "rag".equalsIgnoreCase(type);
    }

    private String buildSystemPrompt(AgentExecutionRequest request, String ragContext) {
        StringBuilder sb = new StringBuilder();
        if (request.getSystemPrompt() != null) {
            sb.append(request.getSystemPrompt());
        } else {
            sb.append("你是 YDSZ 项目管理信息系统的智能助手。请基于知识库内容回答用户问题。");
        }
        if (ragContext != null && !ragContext.isBlank()) {
            sb.append("\n\n").append(ragContext);
        }
        return sb.toString();
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
