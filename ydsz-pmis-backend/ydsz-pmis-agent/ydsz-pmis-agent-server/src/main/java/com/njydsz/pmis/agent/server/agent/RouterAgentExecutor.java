package com.njydsz.pmis.agent.server.agent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.agent.domain.agent.AgentDefinition;
import com.njydsz.pmis.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.pmis.agent.domain.agent.AgentExecutor;
import com.njydsz.pmis.agent.domain.gateway.LlmClient;
import com.njydsz.pmis.agent.domain.model.ChatChunk;
import com.njydsz.pmis.agent.domain.model.ChatMessage;
import com.njydsz.pmis.agent.domain.model.ChatRequest;
import com.njydsz.pmis.agent.domain.model.ChatResponse;
import com.njydsz.pmis.agent.server.config.AgentProperties;

/**
 * Router Agent 执行器
 *
 * <p>根据用户输入的意图，将请求路由到最合适的子 Agent 执行器。
 *
 * <p>执行流程：
 * <ol>
 *   <li>调用 LLM 分析用户意图，选择最合适的 Agent 类型</li>
 *   <li>将请求转发到选中的 Agent 执行器</li>
 * </ol>
 *
 * <p>路由策略：
 * <ul>
 *   <li>需要查知识库 → RAG Agent</li>
 *   <li>需要使用工具 → ReAct Agent</li>
 *   <li>复杂多步任务 → Plan-Execute Agent</li>
 *   <li>简单问答 → Simple Agent</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public class RouterAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(RouterAgentExecutor.class);

    private final LlmClient llmClient;
    private final AgentProperties properties;
    private final AgentFactory agentFactory;

    public RouterAgentExecutor(LlmClient llmClient, AgentProperties properties,
                                AgentFactory agentFactory) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.agentFactory = agentFactory;
    }

    @Override
    public ChatResponse execute(AgentExecutionRequest request) {
        String convId = request.getConversationId() != null
                ? request.getConversationId() : UUID.randomUUID().toString();
        log.info("[Router] 分析意图: convId={}", convId);

        String agentType = routeIntent(request.getUserInput());
        log.info("[Router] 路由到: {} Agent", agentType);

        AgentExecutor executor = agentFactory.getExecutor(
                new AgentDefinition(
                        UUID.randomUUID().toString(), "router-dispatched", "Router",
                        AgentDefinition.Type.valueOf(agentType),
                        request.getSystemPrompt(), List.of(),
                        properties.getLlm().getTemperature(),
                        properties.getLlm().getMaxTokens(),
                        request.getMaxIterations(),
                        properties.getLlm().getDefaultModel()));

        return executor.execute(request);
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
        return "router";
    }

    @Override
    public boolean supports(String type) {
        return "router".equalsIgnoreCase(type);
    }

    private String routeIntent(String userInput) {
        String routingPrompt = """
                你是 PMIS 智能助手的路由器。请分析用户意图，选择最合适的 Agent 类型。

                可选类型：
                - CHAT: 简单问答（如问候、常识问题）
                - REACT: 需要使用工具完成任务（如查询项目、发起审批）
                - RAG: 需要检索知识库回答问题（如查询文档内容、政策规定）
                - PLAN_EXECUTE: 复杂多步任务（如"帮我分析项目进度并生成报告"）

                用户输入: %s

                只输出类型名称（CHAT / REACT / RAG / PLAN_EXECUTE），不要其他内容。
                """.formatted(userInput);

        ChatRequest routeRequest = ChatRequest.builder()
                .model(properties.getLlm().getDefaultModel())
                .messages(List.of(
                        ChatMessage.system("你是意图分类器，只输出分类结果。"),
                        ChatMessage.user(routingPrompt, null)))
                .temperature(0.0)
                .maxTokens(20)
                .build();

        try {
            ChatResponse response = llmClient.chat(routeRequest);
            String content = response.getContent() != null ? response.getContent().trim().toUpperCase() : "CHAT";
            for (AgentDefinition.Type type : AgentDefinition.Type.values()) {
                if (content.contains(type.name())) {
                    return type.name();
                }
            }
            return "CHAT";
        } catch (Exception e) {
            log.warn("[Router] 意图分析失败，降级到 CHAT: {}", e.getMessage());
            return "CHAT";
        }
    }
}
