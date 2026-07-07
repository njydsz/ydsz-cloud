package com.njydsz.pmis.agent.engine.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * LLM Provider 路由器（批次 19 P3-1 落地）
 *
 * <p>根据配置 {@code pmis.agent.llm.provider} 选择实际 LLM 实现：
 * <ul>
 *   <li>{@code mock} - {@link MockLlmProvider}（默认，开发/测试用）</li>
 *   <li>{@code spring-ai-openai} - {@link SpringAiLlmProvider}（Spring AI OpenAI）</li>
 *   <li>{@code dashscope} - {@link DashScopeLlmProvider}（阿里通义千问）</li>
 *   <li>{@code qianfan} - {@link QianfanLlmProvider}（百度千帆）</li>
 * </ul>
 *
 * <p>切换方式（生产环境热更新）：
 * <pre>
 *   # 修改配置 pmis.agent.llm.provider=mock → spring-ai-openai
 *   # 调用 /agent/llm/reload?providerName=xxx 触发重新选择
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class LlmProviderRouter {

    /** Spring 应用上下文（用于动态查找 LlmProvider Bean） */
    private final ApplicationContext applicationContext;
    /** Mock LLM Provider（降级兜底） */
    private final MockLlmProvider mockLlmProvider;
    /** 配置的 Provider 名称（pmis.agent.llm.provider） */
    private final String configuredProvider;

    /** 当前生效的 LLM Provider（懒加载，volatile 保证可见性） */
    private volatile LlmProvider activeProvider = null;

    public LlmProviderRouter(ApplicationContext applicationContext,
                             MockLlmProvider mockLlmProvider,
                             @Value("${pmis.agent.llm.provider:mock}") String configuredProvider) {
        this.applicationContext = applicationContext;
        this.mockLlmProvider = mockLlmProvider;
        this.configuredProvider = configuredProvider;
    }

    /**
     * 获取当前生效的 LLM Provider。
     *
     * <p>选择策略：按配置 {@code pmis.agent.llm.provider} 精确匹配 Provider 的 {@code name()}，
     * 未匹配到时降级到 MockLlmProvider。结果缓存到 {@link #activeProvider}，避免每次扫描 Bean。
     *
     * @return 当前生效的 LLM Provider
     */
    public synchronized LlmProvider active() {
        if (activeProvider != null) {
            return activeProvider;
        }
        Map<String, LlmProvider> providers = applicationContext.getBeansOfType(LlmProvider.class);
        if (providers.isEmpty()) {
            log.warn("[LlmRouter] no LlmProvider bean found, fallback to mock");
            activeProvider = mockLlmProvider;
        } else {
            // 按配置的 provider name 精确匹配
            for (Map.Entry<String, LlmProvider> entry : providers.entrySet()) {
                if (entry.getValue().name().equals(configuredProvider)) {
                    activeProvider = entry.getValue();
                    break;
                }
            }
            if (activeProvider == null) {
                log.warn("[LlmRouter] configured provider '{}' not found, fallback to mock", configuredProvider);
                activeProvider = mockLlmProvider;
            }
        }
        log.info("[LlmRouter] active LLM provider: {} (configured={})", activeProvider.name(), configuredProvider);
        return activeProvider;
    }

    /**
     * 强制切换 LLM Provider（用于热更新）。
     *
     * <p>按 provider name 匹配，未匹配到时降级到 MockLlmProvider。
     *
     * @param providerName 目标 Provider 名称（如 "spring-ai-openai"、"dashscope"、"mock"）
     */
    public synchronized void reload(String providerName) {
        Map<String, LlmProvider> providers = applicationContext.getBeansOfType(LlmProvider.class);
        LlmProvider target = providers.values().stream()
                .filter(p -> p.name().equals(providerName))
                .findFirst()
                .orElse(mockLlmProvider);
        this.activeProvider = target;
        log.info("[LlmRouter] switched to: {} (requested={})", target.name(), providerName);
    }

    /**
     * 获取当前生效的 Provider 名称（P1-13 新增，供健康检查/监控使用）。
     *
     * @return 当前 Provider 名称（如 "mock"、"spring-ai-openai"）
     */
    public String getActiveProviderName() {
        return active().name();
    }
}
