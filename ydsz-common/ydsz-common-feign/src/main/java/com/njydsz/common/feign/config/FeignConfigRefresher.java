package com.njydsz.common.feign.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;

import lombok.extern.slf4j.Slf4j;

/**
 * Feign 配置刷新监听器。
 *
 * <p>监听 Spring Cloud 的 {@link EnvironmentChangeEvent} 事件，
 * 当 Feign 相关配置发生变化时，智能决策刷新策略：
 * <ul>
 *   <li><b>per-client 超时变更</b>（{@code ydsz.feign.client-timeouts.<clientName>.}）→ 增量刷新，仅清除受影响客户端</li>
 *   <li><b>全局配置变更</b>（{@code ydsz.feign.timeout}、{@code ydsz.feign.circuit-breaker} 等）→ 全量刷新</li>
 * </ul>
 *
 * <p>增量刷新优势：避免全局配置变更时的"误伤"问题，per-client 超时调整不会
 * 导致其他无辜客户端的缓存失效，减少不必要的代理重建开销。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DynamicFeignClientFactory
 * @see FeignProperties
 */
@Slf4j
@Order(100)
public class FeignConfigRefresher implements ApplicationListener<EnvironmentChangeEvent> {

    private static final String FEIGN_CONFIG_PREFIX = "ydsz.feign";

    /** 全局配置键集合——这些键的变更会触发全量刷新 */
    private static final Set<String> GLOBAL_CONFIG_KEYS = Set.of(
            "ydsz.feign.enabled",
            "ydsz.feign.logger-level",
            "ydsz.feign.timeout.connect",
            "ydsz.feign.timeout.read",
            "ydsz.feign.retry.enabled",
            "ydsz.feign.retry.max-attempts",
            "ydsz.feign.retry.backoff.delay",
            "ydsz.feign.retry.backoff.max-delay",
            "ydsz.feign.retry.backoff.multiplier",
            "ydsz.feign.error.include-body",
            "ydsz.feign.error.max-body-bytes",
            "ydsz.feign.client.max-connections",
            "ydsz.feign.client.max-per-route",
            "ydsz.feign.client.keep-alive",
            "ydsz.feign.compress.enabled",
            "ydsz.feign.compress.min-size",
            "ydsz.feign.circuit-breaker.enabled",
            "ydsz.feign.circuit-breaker.failure-rate-threshold",
            "ydsz.feign.circuit-breaker.slow-call-rate-threshold",
            "ydsz.feign.circuit-breaker.slow-call-duration-threshold",
            "ydsz.feign.circuit-breaker.sliding-window-size",
            "ydsz.feign.circuit-breaker.sliding-window-type",
            "ydsz.feign.circuit-breaker.wait-duration-in-open-state",
            "ydsz.feign.bulkhead.enabled",
            "ydsz.feign.bulkhead.default-max-concurrent-calls",
            "ydsz.feign.bulkhead.acquire-timeout-millis",
            "ydsz.feign.propagation.enabled"
    );

    private final ApplicationContext applicationContext;
    private final DynamicFeignClientFactory clientFactory;

    /**
     * 构造函数。
     *
     * @param applicationContext Spring 应用上下文
     * @param clientFactory      动态 Feign 客户端工厂
     */
    public FeignConfigRefresher(ApplicationContext applicationContext,
                                DynamicFeignClientFactory clientFactory) {
        this.applicationContext = applicationContext;
        this.clientFactory = clientFactory;
    }

    /**
     * 处理环境配置变更事件，智能决策刷新策略。
     *
     * <p>判断逻辑：
     * <ol>
     *   <li>若变更键中包含 per-client 超时配置（{@code ydsz.feign.client-timeouts.<name>.}）→ 增量刷新</li>
     *   <li>若变更键中包含全局配置 → 全量刷新</li>
     *   <li>否则跳过</li>
     * </ol>
     *
     * @param event 环境配置变更事件
     */
    @Override
    public void onApplicationEvent(@NonNull EnvironmentChangeEvent event) {
        FeignProperties properties = applicationContext.getBean(FeignProperties.class);
        if (!properties.getRefresh().isEnabled()) {
            log.debug("[Feign] 动态刷新未启用，跳过配置刷新");
            return;
        }

        Set<String> changedKeys = event.getKeys();
        Set<String> relevantKeys = filterRelevantKeys(changedKeys);
        if (relevantKeys.isEmpty()) {
            return;
        }

        log.info("[Feign] 检测到 Feign 配置变更: {}", relevantKeys);

        // 判断刷新策略
        RefreshStrategy strategy = determineRefreshStrategy(relevantKeys);
        switch (strategy) {
            case INCREMENTAL_TIMEOUT_ONLY:
                // 仅 per-client 超时变更，增量刷新
                clientFactory.clearTimeoutAffectedCache(relevantKeys);
                log.info("[Feign] 增量刷新完成（per-client 超时配置变更）");
                break;
            case GLOBAL_REFRESH:
                // 全局配置变更，全量刷新
                Set<String> excludeSet = new HashSet<>(properties.getRefresh().getExclude());
                clientFactory.clearCache(excludeSet);
                log.info("[Feign] 全量刷新完成（全局配置变更）");
                break;
            default:
                log.debug("[Feign] 无有效变更需要刷新");
                break;
        }
    }

    /**
     * 过滤出与 Feign 相关的配置变更键。
     *
     * @param keys 所有变更的配置键
     * @return 与 Feign 相关的配置键集合
     */
    private Set<String> filterRelevantKeys(Set<String> keys) {
        Set<String> relevant = new HashSet<>();
        for (String key : keys) {
            if (key.startsWith(FEIGN_CONFIG_PREFIX)) {
                relevant.add(key);
            }
        }
        return relevant;
    }

    /**
     * 根据变更键判断刷新策略。
     *
     * <p>若存在非超时的全局配置变更则触发全量刷新；
     * 若仅 per-client 超时配置变更则仅增量刷新。
     *
     * @param relevantKeys 变更的配置键
     * @return 刷新策略
     */
    private RefreshStrategy determineRefreshStrategy(Set<String> relevantKeys) {
        boolean hasGlobalChange = false;
        boolean hasTimeoutChange = false;

        for (String key : relevantKeys) {
            if (key.startsWith("ydsz.feign.client-timeouts.")) {
                hasTimeoutChange = true;
            } else if (GLOBAL_CONFIG_KEYS.contains(key) || isGlobalPerClientOverrideKey(key)) {
                hasGlobalChange = true;
            }
        }

        if (hasGlobalChange) {
            return RefreshStrategy.GLOBAL_REFRESH;
        }
        if (hasTimeoutChange) {
            return RefreshStrategy.INCREMENTAL_TIMEOUT_ONLY;
        }
        return RefreshStrategy.NONE;
    }

    /**
     * 判断是否为全局 per-client 覆盖配置的顶层键（非超时类）。
     *
     * <p>例如 {@code ydsz.feign.bulkhead.client-config.message.max-concurrent-calls} 属于全局性变更，
     * 因为这些配置在每次 Builder 创建时被读取，需要全量刷新才能生效。
     *
     * @param key 配置键
     * @return 是否为全局性 per-client 覆盖配置
     */
    private boolean isGlobalPerClientOverrideKey(String key) {
        return key.startsWith("ydsz.feign.circuit-breaker.client-config.")
                || key.startsWith("ydsz.feign.bulkhead.client-config.");
    }

    /**
     * 刷新策略枚举。
     */
    private enum RefreshStrategy {
        /** 无需刷新 */
        NONE,
        /** 仅增量刷新超时配置受影响的客户端 */
        INCREMENTAL_TIMEOUT_ONLY,
        /** 全量刷新所有客户端 */
        GLOBAL_REFRESH
    }
}
