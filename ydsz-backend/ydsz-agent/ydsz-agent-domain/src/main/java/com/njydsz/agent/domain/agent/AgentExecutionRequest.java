package com.njydsz.agent.domain.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 执行请求
 *
 * <p>封装一次 Agent 调用所需的全部上下文：
 * <ul>
 *   <li>用户输入消息</li>
 *   <li>对话 ID（用于记忆检索）</li>
 *   <li>系统提示词（覆盖 Agent 默认）</li>
 *   <li>额外变量（Prompt 模板渲染）</li>
 *   <li>最大迭代次数（ReAct 模式）</li>
 * </ul>
 *
 * <p><b>线程安全</b>：全部字段 final 且集合经不可变封装，实例不可变、可安全跨线程传递。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class AgentExecutionRequest {

    private final String conversationId;
    private final String userInput;
    private final String systemPrompt;
    private final Map<String, Object> variables;
    private final int maxIterations;
    private final List<String> enabledTools;

    public AgentExecutionRequest(String conversationId, String userInput, String systemPrompt,
                                 Map<String, Object> variables, int maxIterations,
                                 List<String> enabledTools) {
        this.conversationId = conversationId;
        this.userInput = Objects.requireNonNull(userInput, "userInput 不能为 null");
        this.systemPrompt = systemPrompt;
        this.variables = variables != null ? Map.copyOf(variables) : Collections.emptyMap();
        // 未指定迭代上限时默认 10 轮，作为 ReAct 循环的兜底上限，避免工具调用死循环
        this.maxIterations = maxIterations > 0 ? maxIterations : 10;
        this.enabledTools = enabledTools != null ? List.copyOf(enabledTools) : Collections.emptyList();
    }

    public String getConversationId() { return conversationId; }
    public String getUserInput() { return userInput; }
    public String getSystemPrompt() { return systemPrompt; }
    public Map<String, Object> getVariables() { return variables; }
    public int getMaxIterations() { return maxIterations; }
    public List<String> getEnabledTools() { return enabledTools; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String conversationId;
        private String userInput;
        private String systemPrompt;
        private Map<String, Object> variables;
        private int maxIterations = 10; // Builder 默认值，与构造兜底保持一致，避免未设值时陷入无限迭代
        private List<String> enabledTools;

        public Builder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
        public Builder userInput(String userInput) { this.userInput = userInput; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder variables(Map<String, Object> variables) { this.variables = variables; return this; }
        public Builder maxIterations(int maxIterations) { this.maxIterations = maxIterations; return this; }
        public Builder enabledTools(List<String> enabledTools) { this.enabledTools = enabledTools; return this; }

        public AgentExecutionRequest build() {
            return new AgentExecutionRequest(conversationId, userInput, systemPrompt,
                    variables, maxIterations, enabledTools);
        }
    }
}
