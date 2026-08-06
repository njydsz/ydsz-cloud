package com.remisoft.agent.server.agent;

import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.agent.domain.agent.AgentDefinition;
import com.remisoft.agent.domain.agent.AgentExecutionRequest;
import com.remisoft.agent.domain.agent.AgentExecutor;
import com.remisoft.agent.domain.gateway.LlmClient;
import com.remisoft.agent.domain.model.ChatChunk;
import com.remisoft.agent.domain.model.ChatMessage;
import com.remisoft.agent.domain.model.ChatRequest;
import com.remisoft.agent.domain.model.ChatResponse;
import com.remisoft.agent.domain.trace.TraceRecorder;
import com.remisoft.agent.server.config.AgentProperties;
import com.remisoft.agent.server.metrics.AgentMetrics;
import com.remisoft.common.util.id.IdGenerator;

/**
 * Router Agent 执行器
 *
 * <p>根据用户输入的意图，将请求路由到最合适的子 Agent 执行器。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class RouterAgentExecutor implements AgentExecutor {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(RouterAgentExecutor.class);

    /** LLM 客户端（用于意图分类） */
    private final LlmClient llmClient;
    /** Agent 配置属性 */
    private final AgentProperties properties;
    /** Agent 工厂（用于创建路由到的子执行器） */
    private final AgentFactory agentFactory;
    /** 链路追踪记录器 */
    private final TraceRecorder traceRecorder;
    /** Agent 监控指标采集器 */
    private final AgentMetrics agentMetrics;

    public RouterAgentExecutor(LlmClient llmClient, AgentProperties properties,
                                AgentFactory agentFactory,
                                TraceRecorder traceRecorder,
                                AgentMetrics agentMetrics) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.agentFactory = agentFactory;
        this.traceRecorder = traceRecorder;
        this.agentMetrics = agentMetrics;
    }

    /**
     * {@inheritDoc}
     * <p>路由流程：LLM 意图分类 → 创建子 Agent 执行器 → 委托执行 → 追踪记录。
     * 意图分类失败时降级到 CHAT 类型。
     */
    @Override
    public ChatResponse execute(AgentExecutionRequest request) {
        String convId = request.getConversationId() != null
                ? request.getConversationId() : IdGenerator.nextIdStr();
        String traceId = traceRecorder.startTrace(convId, "ROUTER");
        log.info("[Router] 分析意图: convId={}, traceId={}", convId, traceId);

        long routeStart = System.currentTimeMillis();
        String agentType = routeIntent(request.getUserInput());
        long routeDuration = System.currentTimeMillis() - routeStart;

        traceRecorder.recordStep(traceId, "ROUTE",
                "Routed to " + agentType, request.getUserInput(),
                agentType, routeDuration);
        log.info("[Router] 路由到: {} Agent", agentType);

        AgentExecutor executor = agentFactory.getExecutor(
                new AgentDefinition(
                        IdGenerator.nextIdStr(), "router-dispatched", "Router",
                        AgentDefinition.Type.valueOf(agentType),
                        request.getSystemPrompt(), List.of(),
                        properties.getLlm().getTemperature(),
                        properties.getLlm().getMaxTokens(),
                        request.getMaxIterations(),
                        properties.getLlm().getDefaultModel()));

        ChatResponse response = executor.execute(request);
        traceRecorder.endTrace(traceId, "SUCCESS");
        return response;
    }

    /**
     * {@inheritDoc}
     * <p>流式路由流程：LLM 意图分类 → 创建子 Agent 执行器 → 委托流式执行 → 追踪记录。
     * 委托给路由到的子执行器的 executeStream 方法，实现真正的流式输出。
     */
    @Override
    public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
        String convId = request.getConversationId() != null
                ? request.getConversationId() : IdGenerator.nextIdStr();
        String traceId = traceRecorder.startTrace(convId, "ROUTER_STREAM");
        log.info("[Router-Stream] 分析意图: convId={}, traceId={}", convId, traceId);

        long routeStart = System.currentTimeMillis();
        String agentType = routeIntent(request.getUserInput());
        long routeDuration = System.currentTimeMillis() - routeStart;

        traceRecorder.recordStep(traceId, "ROUTE",
                "Routed to " + agentType, request.getUserInput(),
                agentType, routeDuration);
        log.info("[Router-Stream] 路由到: {} Agent", agentType);

        AgentExecutor executor = agentFactory.getExecutor(
                new AgentDefinition(
                        IdGenerator.nextIdStr(), "router-dispatched", "Router",
                        AgentDefinition.Type.valueOf(agentType),
                        request.getSystemPrompt(), List.of(),
                        properties.getLlm().getTemperature(),
                        properties.getLlm().getMaxTokens(),
                        request.getMaxIterations(),
                        properties.getLlm().getDefaultModel()));

        executor.executeStream(request, chunkConsumer);
        traceRecorder.endTrace(traceId, "SUCCESS");
    }

    /**
     * {@inheritDoc}
     *
     * @return "router"
     */
    @Override
    public String getType() {
        return "router";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(String type) {
        return "router".equalsIgnoreCase(type);
    }

    /**
     * 使用 LLM 分析用户输入意图，返回最适合的 Agent 类型。
     * <p>构造意图分类 prompt（CHAT/REACT/RAG/PLAN_EXECUTE），设置 temperature=0 确保确定性输出。
     * LLM 调用失败时降级返回 "CHAT"。
     *
     * @param userInput 用户输入文本
     * @return Agent 类型名称（CHAT / REACT / RAG / PLAN_EXECUTE）
     */
    private String routeIntent(String userInput) {
        String routingPrompt = """
                你是 REMI 智能助手的路由器。请分析用户意图，选择最合适的 Agent 类型。

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
            agentMetrics.recordLlmCall(llmClient.getProvider(),
                    properties.getLlm().getDefaultModel(),
                    0, response, null);
            String content = response.getContent() != null ? response.getContent().trim().toUpperCase() : "CHAT";
            for (AgentDefinition.Type type : AgentDefinition.Type.values()) {
                if (content.contains(type.name())) {
                    return type.name();
                }
            }
            return "CHAT";
        } catch (Exception e) {
            agentMetrics.recordLlmCall(llmClient.getProvider(),
                    properties.getLlm().getDefaultModel(),
                    0, null, e);
            log.warn("[Router] 意图分析失败，降级到 CHAT: {}", e.getMessage());
            return "CHAT";
        }
    }
}
