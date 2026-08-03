package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * LLM 聊天补全请求
 *
 * <p>对标 OpenAI Chat Completions API 请求体，支持消息列表、温度、最大 Token 等参数。
 *
 * <p><b>线程安全</b>：所有字段 final 且集合不可变，构造后只读，可安全跨线程传递与复用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模型名称 */
    private final String model;
    /** 消息列表（按角色排序） */
    private final List<ChatMessage> messages;
    /** 温度参数（0-2） */
    private final double temperature;
    /** 最大生成 Token 数 */
    private final int maxTokens;
    /** Top-P 采样参数 */
    private final double topP;
    /** 停止序列列表 */
    private final List<String> stop;
    /** 是否流式输出 */
    private final boolean stream;
    /** 可用工具定义列表 */
    private final List<ToolDefinition> tools;
    /** 工具选择策略（auto/none/指定工具名） */
    private final String toolChoice;

    public ChatRequest(String model, List<ChatMessage> messages, double temperature,
                       int maxTokens, double topP, List<String> stop, boolean stream,
                       List<ToolDefinition> tools, String toolChoice) {
        this.model = Objects.requireNonNull(model, "model 不能为 null");
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages 不能为 null"));
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.stop = stop != null ? List.copyOf(stop) : List.of();
        this.stream = stream;
        this.tools = tools != null ? List.copyOf(tools) : List.of();
        this.toolChoice = toolChoice;
    }

    public String getModel() { return model; }
    public List<ChatMessage> getMessages() { return messages; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public double getTopP() { return topP; }
    public List<String> getStop() { return stop; }
    public boolean isStream() { return stream; }
    public List<ToolDefinition> getTools() { return tools; }
    public String getToolChoice() { return toolChoice; }

    /**
     * 复制请求并替换消息列表（不可变风格）。
     *
     * @param newMessages 新的消息序列（须按时序排列）
     * @return 携带新消息的新 ChatRequest 实例
     */
    public ChatRequest withMessages(List<ChatMessage> newMessages) {
        return new ChatRequest(model, newMessages, temperature, maxTokens, topP, stop,
                stream, tools, toolChoice);
    }

    /**
     * 复制请求并切换流式/非流式模式。
     *
     * @param newStream 是否流式输出
     * @return 携带新模式的新 ChatRequest 实例
     */
    public ChatRequest withStream(boolean newStream) {
        return new ChatRequest(model, messages, temperature, maxTokens, topP, stop,
                newStream, tools, toolChoice);
    }

    /**
     * 创建 Builder 以构建 ChatRequest。
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ChatRequest 构建器。
     *
     * <p>提供链式配置入口，{@code model} 与 {@code messages} 为必填项，
     * 其余参数均有合理默认值，适用于绝大多数对话场景。</p>
     */
    public static final class Builder {
        private String model;
        private List<ChatMessage> messages;
        private double temperature = 0.7; // 经验默认值：0.7 兼顾生成多样性与稳定性，低于 0 偏确定、高于 1 偏发散
        private int maxTokens = 2048;      // 单次响应 Token 上限默认值，防止超长输出耗尽配额
        private double topP = 1.0;         // Top-P 默认 1.0 即不做 nucleus 截断，由 temperature 主导采样
        private List<String> stop;
        private boolean stream = false;
        private List<ToolDefinition> tools;
        private String toolChoice;

        /** 指定目标模型标识，必填；未设置时 {@link #build()} 会抛出 {@link NullPointerException}。 */
        public Builder model(String model) { this.model = model; return this; }
        /** 设置对话消息序列，必填且需按时序排列；未设置时 {@link #build()} 会抛出 {@link NullPointerException}。 */
        public Builder messages(List<ChatMessage> messages) { this.messages = messages; return this; }
        /** 设置采样温度，取值区间随 Provider 而异（通常 [0, 2]），越大越发散；不校验越界，由 Provider 侧拒绝。 */
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        /** 设置单次响应生成 Token 上限，直接决定本次调用的成本封顶，不含 prompt 侧消耗。 */
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        /** 设置 nucleus 采样阈值，取值 (0, 1]；与 temperature 同时调低会显著降低多样性，建议只调其一。 */
        public Builder topP(double topP) { this.topP = topP; return this; }
        /** 设置停止序列，命中任一序列即截断生成；传 {@code null} 表示不设停止词。 */
        public Builder stop(List<String> stop) { this.stop = stop; return this; }
        /** 设置是否走流式输出；为 {@code true} 时必须使用 {@code LlmClient#stream} 调用，否则响应无法正常解析。 */
        public Builder stream(boolean stream) { this.stream = stream; return this; }
        /** 设置本次开放给模型的工具定义；传 {@code null} 或空列表表示关闭 Function Calling。 */
        public Builder tools(List<ToolDefinition> tools) { this.tools = tools; return this; }
        /** 设置工具选择策略，取值 {@code auto} / {@code none} / 具体工具名；{@code null} 时由 Provider 默认策略决定。 */
        public Builder toolChoice(String toolChoice) { this.toolChoice = toolChoice; return this; }

        /**
         * 校验并构建 ChatRequest 实例。
         *
         * @return 不可变的 ChatRequest 实例
         * @throws NullPointerException 当 {@code model} 或 {@code messages} 未设置时抛出
         */
        public ChatRequest build() {
            return new ChatRequest(model, messages, temperature, maxTokens, topP, stop,
                    stream, tools, toolChoice);
        }
    }
}
