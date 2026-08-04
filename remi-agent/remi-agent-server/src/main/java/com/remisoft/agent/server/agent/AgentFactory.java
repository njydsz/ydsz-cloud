package com.remisoft.agent.server.agent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.agent.domain.agent.AgentDefinition;
import com.remisoft.agent.domain.agent.AgentExecutor;
import com.remisoft.agent.domain.conversation.ConversationMemory;
import com.remisoft.agent.domain.gateway.LlmClient;
import com.remisoft.agent.domain.guardrail.InputGuardrail;
import com.remisoft.agent.domain.guardrail.OutputGuardrail;
import com.remisoft.agent.domain.tool.ToolRegistry;
import com.remisoft.agent.domain.trace.TraceRecorder;
import com.remisoft.agent.server.analytics.CostAnalysisService;
import com.remisoft.agent.server.config.AgentProperties;
import com.remisoft.agent.server.metrics.AgentMetrics;
import com.remisoft.agent.server.rag.RagService;

/**
 * Agent 工厂
 *
 * <p>根据 {@link AgentDefinition} 创建对应的 {@link AgentExecutor} 实现。
 * 支持按类型路由到不同的执行器实现。
 *
 * <p>所有执行器统一注入 {@link TraceRecorder}、{@link AgentMetrics}、{@link CostAnalysisService}，
 * 确保执行链路可追踪、指标可采集、成本可核算。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

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
    /** RAG 服务 */
    private final RagService ragService;
    /** 链路记录器 */
    private final TraceRecorder traceRecorder;
    /** Agent 指标采集 */
    private final AgentMetrics agentMetrics;
    /** 成本分析服务 */
    private final CostAnalysisService costAnalysisService;
    /** 执行器缓存（key=Agent 类型） */
    private final Map<String, AgentExecutor> executorCache = new ConcurrentHashMap<>();

    public AgentFactory(LlmClient llmClient, ConversationMemory memory,
                        ToolRegistry toolRegistry, AgentProperties properties,
                        List<InputGuardrail> inputGuardrails,
                        List<OutputGuardrail> outputGuardrails,
                        RagService ragService,
                        TraceRecorder traceRecorder,
                        AgentMetrics agentMetrics,
                        CostAnalysisService costAnalysisService) {
        this.llmClient = llmClient;
        this.memory = memory;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.ragService = ragService;
        this.traceRecorder = traceRecorder;
        this.agentMetrics = agentMetrics;
        this.costAnalysisService = costAnalysisService;
    }

    /**
     * 获取 Agent 执行器
     *
     * @param definition Agent 定义
     * @return 执行器
     */
    public AgentExecutor getExecutor(AgentDefinition definition) {
        String type = definition.getType().name();
        return executorCache.computeIfAbsent(type, this::createExecutor);
    }

    /**
     * 获取默认 Agent 执行器（ReAct 模式）
     */
    public AgentExecutor getDefaultExecutor() {
        return executorCache.computeIfAbsent("REACT", this::createExecutor);
    }

    private AgentExecutor createExecutor(String type) {
        log.info("[Agent-Factory] 创建执行器: type={}", type);
        if ("REACT".equalsIgnoreCase(type) || "REACT_AGENT".equalsIgnoreCase(type)) {
            return new ReActAgentExecutor(llmClient, memory, toolRegistry, properties,
                    inputGuardrails, outputGuardrails,
                    traceRecorder, agentMetrics, costAnalysisService);
        }
        if ("CHAT".equalsIgnoreCase(type)) {
            return new SimpleAgentExecutor(llmClient, memory, properties,
                    inputGuardrails, outputGuardrails,
                    traceRecorder, agentMetrics, costAnalysisService);
        }
        if ("RAG".equalsIgnoreCase(type)) {
            return new RagAgentExecutor(llmClient, memory, properties, ragService,
                    inputGuardrails, outputGuardrails,
                    traceRecorder, agentMetrics, costAnalysisService);
        }
        if ("PLAN_EXECUTE".equalsIgnoreCase(type)) {
            return new PlanExecuteAgentExecutor(llmClient, memory, properties,
                    traceRecorder, agentMetrics, costAnalysisService);
        }
        if ("ROUTER".equalsIgnoreCase(type)) {
            return new RouterAgentExecutor(llmClient, properties, this,
                    traceRecorder, agentMetrics);
        }
        log.warn("[Agent-Factory] 未知 Agent 类型: {}，回退到 ReAct", type);
        return new ReActAgentExecutor(llmClient, memory, toolRegistry, properties,
                inputGuardrails, outputGuardrails,
                traceRecorder, agentMetrics, costAnalysisService);
    }
}
