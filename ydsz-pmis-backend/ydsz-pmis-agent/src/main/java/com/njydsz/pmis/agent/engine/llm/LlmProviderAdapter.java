package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.common.ai.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM Provider 适配器（P0-2 架构优化）。
 *
 * <p>将 common 模块的 {@link LlmClient} 适配为 agent 模块的 {@link LlmProvider}，
 * 使得 agent 可以复用 common 模块统一创建的 LLM 客户端实例，
 * 而无需在 agent 内部重复创建 OpenAI/DeepSeek 等连接。
 *
 * <p>使用方式：在配置中设置 {@code pmis.agent.llm.provider=common-llm}，
 * {@link LlmProviderRouter} 将自动路由到本 Adapter。
 *
 * <h3>能力映射</h3>
 * <ul>
 *   <li>{@link #chat} → {@link LlmClient#chat(String, String, Map)}</li>
 *   <li>{@link #supportsFunctionCalling} → false（基础 LlmClient 不支持）</li>
 *   <li>{@link #supportsStreaming} → false（基础 LlmClient 不支持）</li>
 *   <li>{@link #chatForJson} → 默认实现（追加 JSON 指令 + fastjson 反序列化）</li>
 * </ul>
 *
 * <p>若需要 Function Calling / Streaming 等高级能力，请使用 agent 原生的
 * {@link SpringAiLlmProvider} 或 {@link DashScopeLlmProvider}。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-2)
 */
@Slf4j
@Component
@ConditionalOnBean(LlmClient.class)
@ConditionalOnProperty(name = "pmis.common.ai.enabled", havingValue = "true")
public class LlmProviderAdapter implements LlmProvider {

    private final LlmClient delegate;

    public LlmProviderAdapter(LlmClient llmClient) {
        this.delegate = llmClient;
        log.info("[LlmProviderAdapter] 已初始化，委托给 common LlmClient（provider={}, model={}）",
                llmClient.provider(), llmClient.model());
    }

    @Override
    public String name() {
        return "common-llm";
    }

    @Override
    public boolean supportsFunctionCalling() {
        return false;
    }

    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, AgentContext context) {
        // 将 AgentContext 中的关键信息转换为 options
        Map<String, Object> options = null;
        if (context != null && context.getParams() != null && !context.getParams().isEmpty()) {
            // 从 params 中提取 LLM 相关参数（如 temperature / maxTokens）
            options = new HashMap<>();
            if (context.getParams().containsKey("temperature")) {
                options.put("temperature", context.getParams().get("temperature"));
            }
            if (context.getParams().containsKey("maxTokens")) {
                options.put("maxTokens", context.getParams().get("maxTokens"));
            }
            if (context.getTraceId() != null) {
                options.put("traceId", context.getTraceId());
            }
        }
        return delegate.chat(systemPrompt, userPrompt, options);
    }
}
