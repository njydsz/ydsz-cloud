package com.njydsz.pmis.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
import com.njydsz.pmis.agent.domain.model.ToolCall;
import com.njydsz.pmis.agent.domain.tool.ToolRegistry;
import com.njydsz.pmis.agent.server.config.AgentProperties;

/**
 * ReAct Agent 执行器
 *
 * <p>实现 ReAct（Reasoning + Acting）模式：
 * <pre>
 * Thought → Action (Tool Call) → Observation (Tool Result) → Thought → ... → Final Answer
 * </pre>
 *
 * <p>执行流程：
 * <ol>
 *   <li>输入护栏检查</li>
 *   <li>构建 System Prompt（含工具描述）</li>
 *   <li>调用 LLM（携带 tools 参数）</li>
 *   <li>如果 LLM 返回 tool_calls：执行工具 → 将结果作为 Tool 消息追加 → 回到步骤 3</li>
 *   <li>如果 LLM 返回普通回复：输出护栏检查 → 返回最终答案</li>
 *   <li>超过最大迭代次数则强制终止</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class ReActAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentExecutor.class);

    private final LlmClient llmClient;
    private final ConversationMemory memory;
    private final ToolRegistry toolRegistry;
    private final AgentProperties properties;
    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;

    public ReActAgentExecutor(LlmClient llmClient, ConversationMemory memory,
                              ToolRegistry toolRegistry, AgentProperties properties,
                              List<InputGuardrail> inputGuardrails,
                              List<OutputGuardrail> outputGuardrails) {
        this.llmClient = llmClient;
        this.memory = memory;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.inputGuardrails = inputGuardrails != null ? inputGuardrails : List.of();
        this.outputGuardrails = outputGuardrails != null ? outputGuardrails : List.of();
    }

    @Override
    public ChatResponse execute(AgentExecutionRequest request) {
        String convId = request.getConversationId() != null
                ? request.getConversationId() : UUID.randomUUID().toString();
        log.info("[ReAct] 开始执行: convId={}, maxIterations={}", convId, request.getMaxIterations());

        String userInput = applyInputGuardrails(request.getUserInput());
        if (userInput == null) {
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

            ChatResponse response = llmClient.chat(llmRequest);
            if (response.getUsage() != null) {
                totalUsage = totalUsage.add(response.getUsage());
            }

            if (!response.hasToolCalls()) {
                String output = applyOutputGuardrails(response.getContent());
                memory.save(convId, ChatMessage.user(userInput, convId));
                memory.save(convId, ChatMessage.assistant(output, convId, response.getUsage()));
                log.info("[ReAct] 完成: convId={}, iterations={}, tokens={}",
                        convId, i + 1, totalUsage.getTotalTokens());
                return new ChatResponse(response.getId(), response.getModel(),
                        ChatMessage.assistant(output, convId, totalUsage),
                        totalUsage, "stop", List.of());
            }

            messages.add(response.getMessage());
            for (ToolCall toolCall : response.getToolCalls()) {
                log.info("[ReAct] 执行工具: {}", toolCall.getName());
                String result = toolRegistry.execute(toolCall);
                ChatMessage toolMsg = ChatMessage.tool(toolCall.getId(), result, convId);
                messages.add(toolMsg);
            }
        }

        log.warn("[ReAct] 超过最大迭代次数: convId={}", convId);
        return buildMaxIterationsResponse(convId, totalUsage);
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
        return "react";
    }

    @Override
    public boolean supports(String type) {
        return "react".equalsIgnoreCase(type) || "react_agent".equalsIgnoreCase(type);
    }

    private String applyInputGuardrails(String input) {
        String sanitized = input;
        for (InputGuardrail guard : inputGuardrails) {
            GuardrailResult result = guard.check(sanitized);
            if (result.isRejected()) {
                log.warn("[ReAct] 输入被护栏拒绝: {} - {}", guard.getName(), result.getReason());
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
                log.warn("[ReAct] 输出被护栏拒绝: {} - {}", guard.getName(), result.getReason());
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
            sb.append("你是 PMIS 项目管理信息系统的智能助手。你可以使用工具来帮助用户完成任务。");
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
