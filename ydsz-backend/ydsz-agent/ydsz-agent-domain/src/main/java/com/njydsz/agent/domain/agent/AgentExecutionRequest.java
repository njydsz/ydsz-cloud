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

    /** 对话 ID，用于从记忆组件回溯历史消息；为 {@code null} 时按单轮无记忆会话处理 */
    private final String conversationId;
    /** 本轮用户输入原文，不可为 {@code null}，构造时强校验 */
    private final String userInput;
    /** 系统提示词，非空时覆盖 Agent 的默认人设；为 {@code null} 时沿用 Agent 定义 */
    private final String systemPrompt;
    /** Prompt 模板渲染变量，不可变映射；未传入时为空 Map 而非 {@code null} */
    private final Map<String, Object> variables;
    /** ReAct 循环最大迭代轮次，非正数按默认 10 处理，用于兜底防止工具调用死循环与 token 失控 */
    private final int maxIterations;
    /** 本次允许调用的工具名白名单，不可变列表；为空表示不限制、使用 Agent 注册的全部工具 */
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

        /** 绑定对话 ID 以启用历史记忆检索；不设置则本次按无上下文的单轮会话执行。 */
        public Builder conversationId(String conversationId) { this.conversationId = conversationId; return this; }
        /** 设置用户输入原文，必填；未设置时 {@link #build()} 会抛出 {@link NullPointerException}。 */
        public Builder userInput(String userInput) { this.userInput = userInput; return this; }
        /** 覆盖 Agent 默认系统提示词，仅对本次执行生效；不设置则沿用 Agent 定义的人设。 */
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        /** 设置 Prompt 模板渲染变量；传 {@code null} 时按空 Map 处理，模板占位符将保持未替换。 */
        public Builder variables(Map<String, Object> variables) { this.variables = variables; return this; }
        /** 设置 ReAct 循环迭代上限，传非正数时回落为默认 10；该值直接决定单次执行的 token 消耗上界。 */
        public Builder maxIterations(int maxIterations) { this.maxIterations = maxIterations; return this; }
        /** 设置本次可调用的工具白名单；传 {@code null} 或空列表表示不限制，放开 Agent 已注册的全部工具。 */
        public Builder enabledTools(List<String> enabledTools) { this.enabledTools = enabledTools; return this; }

        public AgentExecutionRequest build() {
            return new AgentExecutionRequest(conversationId, userInput, systemPrompt,
                    variables, maxIterations, enabledTools);
        }
    }
}
