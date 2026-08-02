package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * LLM 聊天补全请求
 *
 * <p>对标 OpenAI Chat Completions API 请求体，支持消息列表、温度、最大 Token 等参数。
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

    public ChatRequest withMessages(List<ChatMessage> newMessages) {
        return new ChatRequest(model, newMessages, temperature, maxTokens, topP, stop,
                stream, tools, toolChoice);
    }

    public ChatRequest withStream(boolean newStream) {
        return new ChatRequest(model, messages, temperature, maxTokens, topP, stop,
                newStream, tools, toolChoice);
    }

    public static Builder builder() {
        return new Builder();
    }

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

        public Builder model(String model) { this.model = model; return this; }
        public Builder messages(List<ChatMessage> messages) { this.messages = messages; return this; }
        public Builder temperature(double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder topP(double topP) { this.topP = topP; return this; }
        public Builder stop(List<String> stop) { this.stop = stop; return this; }
        public Builder stream(boolean stream) { this.stream = stream; return this; }
        public Builder tools(List<ToolDefinition> tools) { this.tools = tools; return this; }
        public Builder toolChoice(String toolChoice) { this.toolChoice = toolChoice; return this; }

        public ChatRequest build() {
            return new ChatRequest(model, messages, temperature, maxTokens, topP, stop,
                    stream, tools, toolChoice);
        }
    }
}
