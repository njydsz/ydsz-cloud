package com.njydsz.pmis.agent.engine.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    /** Fallback 链（按优先级排列，P4-6） */
    private final List<String> fallbackChain;

    public LlmProviderRouter(ApplicationContext applicationContext,
                             MockLlmProvider mockLlmProvider,
                             @Value("${pmis.agent.llm.provider:mock}") String configuredProvider,
                             @Value("${pmis.agent.llm.fallback-chain:}") String fallbackChainStr,
                             @Value("${pmis.agent.llm.smart-routing:false}") boolean smartRoutingEnabled) {
        this.applicationContext = applicationContext;
        this.mockLlmProvider = mockLlmProvider;
        this.configuredProvider = configuredProvider;
        this.fallbackChain = parseFallbackChain(fallbackChainStr, configuredProvider);
        log.info("[LlmRouter] 初始化, provider={}, fallbackChain={}, smartRouting={}",
                configuredProvider, fallbackChain, smartRoutingEnabled);
    }

    /**
     * 解析 Fallback 链配置。
     *
     * <p>格式：逗号分隔的 provider 名称列表，如 {@code dashscope,spring-ai-openai,mock}
     * <p>未配置时默认为 [configuredProvider, mock]
     */
    private List<String> parseFallbackChain(String chainStr, String primary) {
        List<String> chain = new ArrayList<>();
        if (chainStr != null && !chainStr.isBlank()) {
            chain = Arrays.stream(chainStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        }
        // 确保主 provider 在链首
        if (!chain.contains(primary)) {
            chain.add(0, primary);
        }
        // 确保 mock 在链尾（终极降级）
        if (!chain.contains("mock")) {
            chain.add("mock");
        }
        return chain;
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
        activeProvider = resolveProvider(configuredProvider);
        log.info("[LlmRouter] active LLM provider: {} (configured={})", activeProvider.name(), configuredProvider);
        return activeProvider;
    }

    /**
     * 带容错的 LLM 调用（P4-6 落地）。
     *
     * <p>按 Fallback 链依次尝试调用，首个成功的结果即返回。
     * 某个 Provider 异常时自动切换到链中下一个 Provider，直到全部失败才抛出异常。
     *
     * <p>对标 Coze 模型容错 / Dify Model Load Balancing。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param context      Agent 上下文
     * @return LLM 推理结果
     */
    public String chatWithFallback(String systemPrompt, String userPrompt,
                                    com.njydsz.pmis.agent.engine.AgentContext context) {
        for (String providerName : fallbackChain) {
            try {
                LlmProvider provider = resolveProvider(providerName);
                if (provider == mockLlmProvider && providerName.equals("mock")
                        && fallbackChain.size() > 1) {
                    // mock 是终极降级，前面还有其他 provider 时先跳过
                    continue;
                }
                String result = provider.chat(systemPrompt, userPrompt, context);
                if (result != null && !result.isBlank()) {
                    if (!provider.name().equals(configuredProvider)) {
                        log.info("[LlmRouter] Fallback 成功: {} → {}", configuredProvider, provider.name());
                    }
                    return result;
                }
            } catch (Exception e) {
                log.warn("[LlmRouter] Provider [{}] 调用失败, 尝试下一个: {}",
                        providerName, e.getMessage());
            }
        }
        // 全部失败，降级到 mock
        log.warn("[LlmRouter] 所有 Provider 均失败, 降级到 mock");
        return mockLlmProvider.chat(systemPrompt, userPrompt, context);
    }

    /**
     * 根据 provider 名称解析 Provider 实例。
     */
    private LlmProvider resolveProvider(String providerName) {
        if ("mock".equals(providerName)) {
            return mockLlmProvider;
        }
        Map<String, LlmProvider> providers = applicationContext.getBeansOfType(LlmProvider.class);
        return providers.values().stream()
                .filter(p -> p.name().equals(providerName))
                .findFirst()
                .orElse(mockLlmProvider);
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
