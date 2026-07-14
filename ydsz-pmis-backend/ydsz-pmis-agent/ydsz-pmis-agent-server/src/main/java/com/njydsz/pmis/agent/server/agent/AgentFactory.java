package com.njydsz.pmis.agent.server.agent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.agent.domain.agent.AgentDefinition;
import com.njydsz.pmis.agent.domain.agent.AgentExecutor;
import com.njydsz.pmis.agent.domain.conversation.ConversationMemory;
import com.njydsz.pmis.agent.domain.gateway.LlmClient;
import com.njydsz.pmis.agent.domain.guardrail.InputGuardrail;
import com.njydsz.pmis.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.pmis.agent.domain.tool.ToolRegistry;
import com.njydsz.pmis.agent.server.config.AgentProperties;
import com.njydsz.pmis.agent.server.rag.RagService;

/**
 * Agent 工厂
 *
 * <p>根据 {@link AgentDefinition} 创建对应的 {@link AgentExecutor} 实现。
 * 支持按类型路由到不同的执行器实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final LlmClient llmClient;
    private final ConversationMemory memory;
    private final ToolRegistry toolRegistry;
    private final AgentProperties properties;
    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;
    private final RagService ragService;
    private final Map<String, AgentExecutor> executorCache = new ConcurrentHashMap<>();

    public AgentFactory(LlmClient llmClient, ConversationMemory memory,
                        ToolRegistry toolRegistry, AgentProperties properties,
                        List<InputGuardrail> inputGuardrails,
                        List<OutputGuardrail> outputGuardrails,
                        RagService ragService) {
        this.llmClient = llmClient;
        this.memory = memory;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.ragService = ragService;
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
                    inputGuardrails, outputGuardrails);
        }
        if ("CHAT".equalsIgnoreCase(type)) {
            return new SimpleAgentExecutor(llmClient, memory, properties,
                    inputGuardrails, outputGuardrails);
        }
        if ("RAG".equalsIgnoreCase(type)) {
            return new RagAgentExecutor(llmClient, memory, properties, ragService,
                    inputGuardrails, outputGuardrails);
        }
        if ("PLAN_EXECUTE".equalsIgnoreCase(type)) {
            return new PlanExecuteAgentExecutor(llmClient, memory, properties);
        }
        if ("ROUTER".equalsIgnoreCase(type)) {
            return new RouterAgentExecutor(llmClient, properties, this);
        }
        log.warn("[Agent-Factory] 未知 Agent 类型: {}，回退到 ReAct", type);
        return new ReActAgentExecutor(llmClient, memory, toolRegistry, properties,
                inputGuardrails, outputGuardrails);
    }
}
