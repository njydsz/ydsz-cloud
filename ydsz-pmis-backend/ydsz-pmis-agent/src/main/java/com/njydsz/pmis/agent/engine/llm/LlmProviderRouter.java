package com.njydsz.pmis.agent.engine.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * LLM Provider 路由器（批次 19 P3-1 落地）
 *
 * <p>根据 Nacos 配置 {@code pmis.agent.llm.provider} 选择实际 LLM 实现：
 * <ul>
 *   <li>{@code mock} - {@link MockLlmProvider}（默认）</li>
 *   <li>{@code spring-ai-openai} - {@link SpringAiLlmProvider}</li>
 *   <li>{@code spring-ai-dashscope} - 通义千问（待实现）</li>
 *   <li>{@code spring-ai-qianfan} - 百度千帆（待实现）</li>
 * </ul>
 *
 * <p>切换方式（生产环境热更新）：
 * <pre>
 *   # Nacos 配置 pmis.agent.llm.provider=mock → spring-ai-openai
 *   # 调用 /api/v1/agent/llm/reload 触发 Bean 重建
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmProviderRouter {

    private final ApplicationContext applicationContext;
    private final MockLlmProvider mockLlmProvider;

    private volatile LlmProvider activeProvider = null;

    /**
     * 获取当前生效的 LLM Provider。
     *
     * <p>选择策略：优先取 name 以 "spring-ai" 开头的 Provider，否则降级到 MockLlmProvider。
     * 结果会缓存到 {@link #activeProvider}，避免每次扫描 Bean。
     *
     * @return 当前生效的 LLM Provider
     */
    public LlmProvider active() {
        if (activeProvider != null) {
            return activeProvider;
        }
        // 查找所有 LlmProvider Bean（Spring AI / 自定义）
        Map<String, LlmProvider> providers = applicationContext.getBeansOfType(LlmProvider.class);
        if (providers.isEmpty()) {
            log.warn("[LlmRouter] no LlmProvider bean found, fallback to mock");
            activeProvider = mockLlmProvider;
        } else {
            // 优先取 SpringAiLlmProvider，再 fallback mock
            for (Map.Entry<String, LlmProvider> entry : providers.entrySet()) {
                if (entry.getValue().name().startsWith("spring-ai")) {
                    activeProvider = entry.getValue();
                    break;
                }
            }
            if (activeProvider == null) {
                activeProvider = mockLlmProvider;
            }
        }
        log.info("[LlmRouter] active LLM provider: {}", activeProvider.name());
        return activeProvider;
    }

    /**
     * 强制切换 LLM Provider（用于热更新）。
     *
     * <p>按 provider name 匹配，未匹配到时降级到 MockLlmProvider。
     *
     * @param providerName 目标 Provider 名称（如 "spring-ai-openai"、"dashscope"、"mock"）
     */
    public void reload(String providerName) {
        Map<String, LlmProvider> providers = applicationContext.getBeansOfType(LlmProvider.class);
        LlmProvider target = providers.values().stream()
                .filter(p -> p.name().equals(providerName))
                .findFirst()
                .orElse(mockLlmProvider);
        this.activeProvider = target;
        log.info("[LlmRouter] switched to: {}", target.name());
    }
}
