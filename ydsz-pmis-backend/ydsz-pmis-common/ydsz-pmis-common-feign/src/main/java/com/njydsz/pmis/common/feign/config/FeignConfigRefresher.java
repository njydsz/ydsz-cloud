package com.njydsz.pmis.common.feign.config;

import java.util.HashSet;
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
 * 当 Feign 相关配置发生变化时，自动重建 Feign 客户端实例。
 *
 * <p>工作原理：
 * <ol>
 *   <li>监听环境配置变更事件</li>
 *   <li>判断变更的配置是否与 Feign 相关</li>
 *   <li>如果是，则通过 {@link DynamicFeignClientFactory} 重建客户端</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see DynamicFeignClientFactory
 * @see FeignProperties
 */
@Slf4j
@Order(100)
public class FeignConfigRefresher implements ApplicationListener<EnvironmentChangeEvent> {

    private static final String FEIGN_CONFIG_PREFIX = "ydsz.feign";

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
     * 处理环境配置变更事件，当 Feign 相关配置发生变化时刷新客户端。
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

        log.info("[Feign] 检测到 Feign 配置变更，开始刷新: {}", relevantKeys);
        refreshFeignClients(relevantKeys, new HashSet<>(properties.getRefresh().getExclude()));
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
     * 刷新 Feign 客户端。
     *
     * @param changedKeys 变更的配置键
     * @param excludeSet 排除的客户端名称集合
     */
    private void refreshFeignClients(Set<String> changedKeys, Set<String> excludeSet) {
        // 清除缓存并重建 Feign Builder
        clientFactory.clearCache(excludeSet);

        log.info("[Feign] Feign 客户端刷新完成，受影响的配置: {}", changedKeys);
    }
}
