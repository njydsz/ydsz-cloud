package com.njydsz.pmis.literule.agent;

import java.util.Collections;
import java.util.List;

/**
 * AgentRuleNode 工厂（P3-5）
 *
 * <p>提供快速创建 {@link AgentRuleNode} 的便捷方法，封装执行器注入和默认参数。
 * 对标 LiteFlow 的 NodeFactory，简化 Agent 节点的构建过程。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public class AgentRuleNodeFactory {

    /** 默认最大迭代次数 */
    private static final int DEFAULT_MAX_ITERATIONS = 3;

    /** 默认超时（毫秒） */
    private static final long DEFAULT_TIMEOUT_MS = 5000L;

    private final ReActAgentExecutor executor;

    public AgentRuleNodeFactory(ReActAgentExecutor executor) {
        this.executor = executor;
    }

    /**
     * 快速创建 Agent 节点（无工具）
     *
     * @param agentName          Agent 名称
     * @param systemPrompt       系统提示词
     * @param userPromptTemplate 用户提示词模板（支持 ${var} 变量替换）
     * @return AgentRuleNode 实例
     */
    public AgentRuleNode create(String agentName, String systemPrompt, String userPromptTemplate) {
        return createWithTools(agentName, systemPrompt, userPromptTemplate, null, null, null);
    }

    /**
     * 快速创建 Agent 节点（带工具）
     *
     * @param agentName          Agent 名称
     * @param systemPrompt       系统提示词
     * @param userPromptTemplate 用户提示词模板
     * @param toolRuleCodes      可用工具列表（规则编码列表）
     * @return AgentRuleNode 实例
     */
    public AgentRuleNode createWithTools(String agentName, String systemPrompt,
                                         String userPromptTemplate, List<String> toolRuleCodes) {
        return createWithTools(agentName, systemPrompt, userPromptTemplate, toolRuleCodes, null, null);
    }

    /**
     * 创建 Agent 节点（全参数）
     *
     * @param agentName          Agent 名称
     * @param systemPrompt       系统提示词
     * @param userPromptTemplate 用户提示词模板
     * @param toolRuleCodes      可用工具列表（规则编码）
     * @param toolExecutor       工具执行回调（ruleCode -> observation）
     * @param outputVariable     输出变量名（写入 context 的变量名）
     * @return AgentRuleNode 实例
     */
    public AgentRuleNode createWithTools(String agentName, String systemPrompt,
                                         String userPromptTemplate, List<String> toolRuleCodes,
                                         java.util.function.Function<String, String> toolExecutor,
                                         String outputVariable) {
        String nodeId = "agent-" + (agentName != null ? agentName : "default");
        List<String> tools = toolRuleCodes != null ? toolRuleCodes : Collections.emptyList();
        return new AgentRuleNode(
                nodeId,
                agentName,
                systemPrompt,
                userPromptTemplate,
                DEFAULT_MAX_ITERATIONS,
                tools,
                outputVariable,
                DEFAULT_TIMEOUT_MS,
                executor,
                toolExecutor
        );
    }
}
