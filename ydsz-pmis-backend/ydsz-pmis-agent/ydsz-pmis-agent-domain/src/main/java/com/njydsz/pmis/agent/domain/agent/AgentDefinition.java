package com.njydsz.pmis.agent.domain.agent;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Agent 定义值对象
 *
 * <p>描述一个 Agent 的完整配置，包括类型、系统提示词、绑定工具、模型参数等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class AgentDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Agent 类型枚举 */
    public enum Type {
        /** 单轮对话 */
        CHAT,
        /** ReAct 模式（Thought→Action→Observation） */
        REACT,
        /** RAG 检索增强生成 */
        RAG,
        /** Plan-and-Execute 模式 */
        PLAN_EXECUTE,
        /** 路由器（多 Agent 分发） */
        ROUTER
    }

    private final String agentId;
    private final String code;
    private final String name;
    private final Type type;
    private final String systemPrompt;
    private final List<String> toolNames;
    private final double temperature;
    private final int maxTokens;
    private final int maxIterations;
    private final String modelId;

    public AgentDefinition(String agentId, String code, String name, Type type,
                           String systemPrompt, List<String> toolNames,
                           double temperature, int maxTokens, int maxIterations,
                           String modelId) {
        this.agentId = Objects.requireNonNull(agentId, "agentId 不能为 null");
        this.code = Objects.requireNonNull(code, "code 不能为 null");
        this.name = Objects.requireNonNull(name, "name 不能为 null");
        this.type = type != null ? type : Type.CHAT;
        this.systemPrompt = systemPrompt;
        this.toolNames = toolNames != null ? List.copyOf(toolNames) : List.of();
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.maxIterations = maxIterations > 0 ? maxIterations : 10;
        this.modelId = modelId;
    }

    public String getAgentId() { return agentId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public String getSystemPrompt() { return systemPrompt; }
    public List<String> getToolNames() { return toolNames; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public int getMaxIterations() { return maxIterations; }
    public String getModelId() { return modelId; }

    public boolean hasTools() {
        return !toolNames.isEmpty();
    }

    @Override
    public String toString() {
        return "AgentDefinition{code='" + code + "', type=" + type + ", tools=" + toolNames.size() + "}";
    }
}
