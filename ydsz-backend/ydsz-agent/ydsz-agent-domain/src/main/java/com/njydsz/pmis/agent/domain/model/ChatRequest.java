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

    private final String model;
    private final List<ChatMessage> messages;
    private final double temperature;
    private final int maxTokens;
    private final double topP;
    private final List<String> stop;
    private final boolean stream;
    private final List<ToolDefinition> tools;
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
        private double temperature = 0.7;
        private int maxTokens = 2048;
        private double topP = 1.0;
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
