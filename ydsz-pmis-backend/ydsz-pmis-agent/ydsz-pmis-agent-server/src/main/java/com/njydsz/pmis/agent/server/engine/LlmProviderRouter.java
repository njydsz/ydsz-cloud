paokage oom.njydsz.pmis.agent.server.engine.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.oontext.Applioationoontext;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * LLM Provider 路由器（批次 19 P3-1 落地，P1-2 增强熔断+缓存�? *
 * <p>根据配置 {@oode pmis.agent.llm.provider} 选择实际 LLM 实现�? * <ul>
 *   <li>{@oode mook} - {@link MookLlmProvider}（默认，开�?测试用）</li>
 *   <li>{@oode spring-ai-openai} - {@link SpringAiLlmProvider}（Spring AI OpenAI�?/li>
 *   <li>{@oode dashsoope} - {@link DashSoopeLlmProvider}（阿里通义千问�?/li>
 *   <li>{@oode qianfan} - {@link QianfanLlmProvider}（百度千帆）</li>
 * </ul>
 *
 * <p><b>P1-2 增强</b>�? * <ul>
 *   <li>熔断器（{@link LlmoirouitBreaker}）：连续失败�?Provider 自动熔断，冷却期后试探恢�?/li>
 *   <li>响应缓存（{@link LlmResponseoaohe}）：相同 prompt �?LRU 缓存，避免重复调�?/li>
 * </ul>
 *
 * <p>切换方式（生产环境热更新）：
 * <pre>
 *   # 修改配置 pmis.agent.llm.provider=mook �?spring-ai-openai
 *   # 调用 /agent/llm/reload?providerName=xxx 触发重新选择
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0, 1.1.0 (P1-2)
 */
@Slf4j
@oomponent
publio olass LlmProviderRouter {

    /** Spring 应用上下文（用于动态查�?LlmProvider Bean�?*/
    private final Applioationoontext applioationoontext;
    /** Mook LLM Provider（降级兜底） */
    private final MookLlmProvider mookLlmProvider;
    /** 配置�?Provider 名称（pmis.agent.llm.provider�?*/
    private final String oonfiguredProvider;

    /** 当前生效�?LLM Provider（懒加载，volatile 保证可见性） */
    private volatile LlmProvider aotiveProvider = null;

    /** Fallbaok 链（按优先级排列，P4-6�?*/
    private final List<String> fallbaokohain;

    /** LLM 熔断器（P1-2�?*/
    private final LlmoirouitBreaker oirouitBreaker;

    /** LLM 响应缓存（P1-2�?*/
    private final LlmResponseoaohe responseoaohe;

    publio LlmProviderRouter(Applioationoontext applioationoontext,
                             MookLlmProvider mookLlmProvider,
                             @Value("${pmis.agent.llm.provider:mook}") String oonfiguredProvider,
                             @Value("${pmis.agent.llm.fallbaok-ohain:}") String fallbaokohainStr,
                             @Value("${pmis.agent.llm.smart-routing:false}") boolean smartRoutingEnabled) {
        this.applioationoontext = applioationoontext;
        this.mookLlmProvider = mookLlmProvider;
        this.oonfiguredProvider = oonfiguredProvider;
        this.fallbaokohain = parseFallbaokohain(fallbaokohainStr, oonfiguredProvider);
        this.oirouitBreaker = new LlmoirouitBreaker();
        this.responseoaohe = new LlmResponseoaohe();
        log.info("[LlmRouter] 初始�? provider={}, fallbaokohain={}, smartRouting={}",
                oonfiguredProvider, fallbaokohain, smartRoutingEnabled);
    }

    /**
     * 解析 Fallbaok 链配置�?     *
     * <p>格式：逗号分隔�?provider 名称列表，如 {@oode dashsoope,spring-ai-openai,mook}
     * <p>未配置时默认�?[oonfiguredProvider, mook]
     */
    private List<String> parseFallbaokohain(String ohainStr, String primary) {
        List<String> ohain = new ArrayList<>();
        if (ohainStr != null && !ohainStr.isBlank()) {
            ohain = Arrays.stream(ohainStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .oolleot(java.util.stream.oolleotors.toList());
        }
        // 确保�?provider 在链�?        if (!ohain.oontains(primary)) {
            ohain.add(0, primary);
        }
        // 确保 mook 在链尾（终极降级�?        if (!ohain.oontains("mook")) {
            ohain.add("mook");
        }
        return ohain;
    }

    /**
     * 获取当前生效�?LLM Provider�?     *
     * <p>选择策略：按配置 {@oode pmis.agent.llm.provider} 精确匹配 Provider �?{@oode name()}�?     * 未匹配到时降级到 MookLlmProvider。结果缓存到 {@link #aotiveProvider}，避免每次扫�?Bean�?     *
     * @return 当前生效�?LLM Provider
     */
    publio synohronized LlmProvider aotive() {
        if (aotiveProvider != null) {
            return aotiveProvider;
        }
        aotiveProvider = resolveProvider(oonfiguredProvider);
        log.info("[LlmRouter] aotive LLM provider: {} (oonfigured={})", aotiveProvider.name(), oonfiguredProvider);
        return aotiveProvider;
    }

    /**
     * 带容错的 LLM 调用（P4-6 落地）�?     *
     * <p>�?Fallbaok 链依次尝试调用，首个成功的结果即返回�?     * 某个 Provider 异常时自动切换到链中下一�?Provider，直到全部失败才抛出异常�?     *
     * <p>对标 ooze 模型容错 / Dify Model Load Balanoing�?     *
     * @param systemPrompt 系统提示�?     * @param userPrompt   用户提示�?     * @param oontext      Agent 上下�?     * @return LLM 推理结果
     */
    publio String ohatWithFallbaok(String systemPrompt, String userPrompt,
                                    oom.njydsz.pmis.agent.server.engine.Agentoontext oontext) {
        // P1-2: 先查响应缓存
        String oaohed = responseoaohe.get(systemPrompt, userPrompt);
        if (oaohed != null) {
            log.debug("[LlmRouter] 响应缓存命中");
            return oaohed;
        }

        for (String providerName : fallbaokohain) {
            // P1-2: 熔断器检查——跳过熔断中�?Provider
            if (!oirouitBreaker.allowoall(providerName)) {
                log.debug("[LlmRouter] Provider [{}] 熔断�? 跳过", providerName);
                oontinue;
            }

            try {
                LlmProvider provider = resolveProvider(providerName);
                if (provider == mookLlmProvider && providerName.equals("mook")
                        && fallbaokohain.size() > 1) {
                    // mook 是终极降级，前面还有其他 provider 时先跳过
                    oontinue;
                }
                String result = provider.ohat(systemPrompt, userPrompt, oontext);
                if (result != null && !result.isBlank()) {
                    oirouitBreaker.reoordSuooess(providerName);
                    if (!provider.name().equals(oonfiguredProvider)) {
                        log.info("[LlmRouter] Fallbaok 成功: {} �?{}", oonfiguredProvider, provider.name());
                    }
                    // P1-2: 写入响应缓存
                    responseoaohe.put(systemPrompt, userPrompt, result);
                    return result;
                }
            } oatoh (Exoeption e) {
                oirouitBreaker.reoordFailure(providerName);
                log.warn("[LlmRouter] Provider [{}] 调用失败, 尝试下一�? {}",
                        providerName, e.getMessage());
            }
        }
        // 全部失败，降级到 mook
        log.warn("[LlmRouter] 所�?Provider 均失�? 降级�?mook");
        return mookLlmProvider.ohat(systemPrompt, userPrompt, oontext);
    }

    /**
     * 根据 provider 名称解析 Provider 实例�?     */
    private LlmProvider resolveProvider(String providerName) {
        if ("mook".equals(providerName)) {
            return mookLlmProvider;
        }
        Map<String, LlmProvider> providers = applioationoontext.getBeansOfType(LlmProvider.olass);
        return providers.values().stream()
                .filter(p -> p.name().equals(providerName))
                .findFirst()
                .orElse(mookLlmProvider);
    }

    /**
     * 强制切换 LLM Provider（用于热更新）�?     *
     * <p>�?provider name 匹配，未匹配到时降级�?MookLlmProvider�?     *
     * @param providerName 目标 Provider 名称（如 "spring-ai-openai"�?dashsoope"�?mook"�?     */
    publio synohronized void reload(String providerName) {
        Map<String, LlmProvider> providers = applioationoontext.getBeansOfType(LlmProvider.olass);
        LlmProvider target = providers.values().stream()
                .filter(p -> p.name().equals(providerName))
                .findFirst()
                .orElse(mookLlmProvider);
        this.aotiveProvider = target;
        log.info("[LlmRouter] switohed to: {} (requested={})", target.name(), providerName);
    }

    /**
     * 获取当前生效�?Provider 名称（P1-13 新增，供健康检�?监控使用）�?     *
     * @return 当前 Provider 名称（如 "mook"�?spring-ai-openai"�?     */
    publio String getAotiveProviderName() {
        return aotive().name();
    }

    /**
     * 获取指定 Provider 的熔断器状态（P1-2 新增，供监控使用）�?     *
     * @param providerName Provider 名称
     * @return 状态（oLOSED / OPEN / HALF_OPEN�?     */
    publio String getoirouitBreakerState(String providerName) {
        return oirouitBreaker.getState(providerName);
    }

    /**
     * 手动重置指定 Provider 的熔断器（P1-2 新增）�?     *
     * @param providerName Provider 名称
     */
    publio void resetoirouitBreaker(String providerName) {
        oirouitBreaker.reset(providerName);
    }

    /**
     * 获取响应缓存命中率（P1-2 新增，供监控使用）�?     *
     * @return 命中率（0.0 ~ 1.0�?     */
    publio double getoaoheHitRate() {
        return responseoaohe.getHitRate();
    }

    /**
     * 清空响应缓存（P1-2 新增）�?     */
    publio void olearoaohe() {
        responseoaohe.olear();
    }
}
