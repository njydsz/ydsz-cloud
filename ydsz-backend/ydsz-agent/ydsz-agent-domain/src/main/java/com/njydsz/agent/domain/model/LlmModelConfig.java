package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * LLM 模型配置值对象
 *
 * <p>描述一个 LLM 模型的 Provider、API 地址、模型名称、调用参数等。
 *
 * <p><b>线程安全</b>：字段 final 且停止词列表不可变，构造后只读，可安全被多个 LLM 调用线程并发读取。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class LlmModelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模型唯一标识 */
    private final String modelId;
    /** Provider 标识（openai/deepseek/qwen/ollama 等） */
    private final String provider;
    /** 模型名称 */
    private final String modelName;
    /** API Key */
    private final String apiKey;
    /** API 基础地址 */
    private final String baseUrl;
    /** 温度参数 */
    private final double temperature;
    /** 最大生成 Token 数 */
    private final int maxTokens;
    /** Top-P 采样参数 */
    private final double topP;
    /** 停止序列列表 */
    private final List<String> stop;
    /** 调用超时时间（秒） */
    private final int timeoutSeconds;

    public LlmModelConfig(String modelId, String provider, String modelName, String apiKey,
                          String baseUrl, double temperature, int maxTokens, double topP,
                          List<String> stop, int timeoutSeconds) {
        this.modelId = Objects.requireNonNull(modelId, "modelId 不能为 null");
        this.provider = Objects.requireNonNull(provider, "provider 不能为 null");
        this.modelName = Objects.requireNonNull(modelName, "modelName 不能为 null");
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.stop = stop != null ? List.copyOf(stop) : List.of();
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getModelId() { return modelId; }
    public String getProvider() { return provider; }
    public String getModelName() { return modelName; }
    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public double getTopP() { return topP; }
    public List<String> getStop() { return stop; }
    public int getTimeoutSeconds() { return timeoutSeconds; }

    public LlmModelConfig withApiKey(String newApiKey) {
        return new LlmModelConfig(modelId, provider, modelName, newApiKey, baseUrl,
                temperature, maxTokens, topP, stop, timeoutSeconds);
    }

    @Override
    public String toString() {
        return "LlmModelConfig{provider='" + provider + "', model='" + modelName +
                "', temp=" + temperature + ", maxTokens=" + maxTokens + "}";
    }
}
