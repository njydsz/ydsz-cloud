package com.njydsz.common.domain.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.domain.event.DomainEventPublisher;
import com.njydsz.common.domain.tree.TreeLazyConfig;

/**
 * Domain 模块健康指标
 *
 * <p>报告 domain 模块核心组件的运行状态，包括：
 * <ul>
 *   <li>领域事件发布器状态（同步/异步模式）</li>
 *   <li>树懒加载配置</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DomainHealthIndicator implements HealthIndicator {

    private final DomainEventPublisher domainEventPublisher;
    private final TreeLazyConfig treeLazyConfig;

    /**
     * 构造 Domain 模块健康指标
     *
     * @param domainEventPublisher 领域事件发布器（可为 null）
     * @param treeLazyConfig       树懒加载配置（可为 null）
     */
    public DomainHealthIndicator(DomainEventPublisher domainEventPublisher, TreeLazyConfig treeLazyConfig) {
        this.domainEventPublisher = domainEventPublisher;
        this.treeLazyConfig = treeLazyConfig;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        if (domainEventPublisher != null) {
            details.put("eventPublisher.available", true);
            details.put("eventPublisher.asyncSupported", domainEventPublisher.isAsyncSupported());
        } else {
            details.put("eventPublisher.available", false);
        }

        if (treeLazyConfig != null) {
            details.put("tree.lazy.enabled", treeLazyConfig.isEnabled());
            details.put("tree.lazy.maxLazyDepth", treeLazyConfig.getMaxLazyDepth());
            details.put("tree.lazy.batchSize", treeLazyConfig.getBatchSize());
        }

        return Health.up().withDetails(details).build();
    }
}
