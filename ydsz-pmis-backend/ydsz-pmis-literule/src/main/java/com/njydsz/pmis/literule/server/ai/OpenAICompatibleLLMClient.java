package com.njydsz.pmis.literule.server.ai;

import com.njydsz.pmis.common.ai.LlmClientConfig;
import com.njydsz.pmis.common.ai.impl.OpenAICompatibleLlmClient;
import com.njydsz.pmis.literule.server.config.LiteRuleProperties;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议 LLM 客户端（P2-15 AI 增强）
 *
 * <p>符合 OpenAI Chat Completions 接口规范（POST /v1/chat/completions），
 * 可直接对接 OpenAI / DeepSeek / 通义千问 / Ollama / vLLM / LocalAI 等
 * 所有兼容同一协议的服务。
 *
 * <p><b>P0-2 架构优化</b>：委托给 {@link OpenAICompatibleLlmClient}（common 模块统一实现），
 * 本类仅负责将 literule 专属配置 {@link LiteRuleProperties.Ai} 转换为
 * 通用配置 {@link LlmClientConfig}。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class OpenAICompatibleLLMClient implements LLMClient {

    private final OpenAICompatibleLlmClient delegate;

    public OpenAICompatibleLLMClient(LiteRuleProperties.Ai config) {
        LlmClientConfig clientConfig = new LlmClientConfig();
        clientConfig.setClientType("OPENAI_COMPATIBLE");
        clientConfig.setApiUrl(config.getLlmApiUrl());
        clientConfig.setApiKey(config.getLlmApiKey());
        clientConfig.setModel(config.getLlmModel());
        clientConfig.setTemperature(config.getLlmTemperature());
        clientConfig.setTimeoutMs(config.getLlmTimeoutMs());
        this.delegate = new OpenAICompatibleLlmClient(clientConfig);
    }

    /** 测试用构造函数 */
    OpenAICompatibleLLMClient(LiteRuleProperties.Ai config,
                              java.net.http.HttpClient httpClient) {
        LlmClientConfig clientConfig = new LlmClientConfig();
        clientConfig.setClientType("OPENAI_COMPATIBLE");
        clientConfig.setApiUrl(config.getLlmApiUrl());
        clientConfig.setApiKey(config.getLlmApiKey());
        clientConfig.setModel(config.getLlmModel());
        clientConfig.setTemperature(config.getLlmTemperature());
        clientConfig.setTimeoutMs(config.getLlmTimeoutMs());
        this.delegate = new OpenAICompatibleLlmClient(clientConfig, httpClient);
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, Map<String, Object> options) {
        return delegate.chat(systemPrompt, userPrompt, options);
    }

    @Override
    public String chatWithHistory(List<Map<String, String>> messages, Map<String, Object> options) {
        return delegate.chatWithHistory(messages, options);
    }

    @Override
    public String provider() {
        return delegate.provider();
    }

    @Override
    public String model() {
        return delegate.model();
    }
}
