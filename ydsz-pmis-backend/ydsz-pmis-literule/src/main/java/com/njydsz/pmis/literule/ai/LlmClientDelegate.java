package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.common.ai.LlmClient;

import java.util.List;
import java.util.Map;

/**
 * LLM 客户端委托适配器（P0-2 架构优化）。
 *
 * <p>当 common 模块的 {@link LlmClient} Bean 已存在时，
 * 通过本类适配为 literule 的 {@link LLMClient}，避免重复创建 LLM 客户端实例。
 *
 * <p>本类仅做接口桥接，所有方法直接委托给被包装的 {@link LlmClient}。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-2)
 */
public class LlmClientDelegate implements LLMClient {

    private final LlmClient delegate;

    public LlmClientDelegate(LlmClient delegate) {
        this.delegate = delegate;
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
