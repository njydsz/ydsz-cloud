package com.njydsz.pmis.agent.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 模块配置属性
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "pmis.agent")
public class AgentProperties {

    /** 是否启用 Agent 模块 */
    private boolean enabled = true;

    /** LLM 配置 */
    private Llm llm = new Llm();

    /** 记忆配置 */
    private Memory memory = new Memory();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }
    public Memory getMemory() { return memory; }
    public void setMemory(Memory memory) { this.memory = memory; }

    public static class Llm {
        /** 默认 Provider（openai / deepseek / qwen / ollama） */
        private String defaultProvider = "openai";
        /** 默认模型名称 */
        private String defaultModel = "gpt-4o-mini";
        /** API Key */
        private String apiKey = "";
        /** API Base URL */
        private String baseUrl = "https://api.openai.com/v1";
        /** 默认温度 */
        private double temperature = 0.7;
        /** 默认最大 Token */
        private int maxTokens = 2048;
        /** 调用超时（秒） */
        private int timeoutSeconds = 60;

        public String getDefaultProvider() { return defaultProvider; }
        public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }
        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Memory {
        /** 对话记忆 TTL（小时） */
        private int ttlHours = 24;
        /** 滑动窗口最大消息数 */
        private int maxMessages = 20;

        public int getTtlHours() { return ttlHours; }
        public void setTtlHours(int ttlHours) { this.ttlHours = ttlHours; }
        public int getMaxMessages() { return maxMessages; }
        public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
    }
}
