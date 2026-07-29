package com.njydsz.common.domain.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.domain.dag.SpELConditionEvaluator;
import com.njydsz.common.domain.event.DomainEventPublisher;
import com.njydsz.common.domain.event.EventStore;
import com.njydsz.common.domain.tree.TreeLazyConfig;

/**
 * Domain 模块健康指标
 *
 * <p>报告 domain 模块核心组件的运行状态，包括：
 * <ul>
 *   <li>领域事件发布器状态（同步/异步模式）</li>
 *   <li>事件存储（EventStore）状态</li>
 *   <li>SpEL 条件评估器缓存状态</li>
 *   <li>树懒加载配置</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DomainHealthIndicator implements HealthIndicator {

    private final DomainEventPublisher domainEventPublisher;
    private final TreeLazyConfig treeLazyConfig;
    private final ObjectProvider<SpELConditionEvaluator> spELConditionEvaluatorProvider;
    private final ObjectProvider<EventStore> eventStoreProvider;

    /**
     * 构造 Domain 模块健康指标
     *
     * @param domainEventPublisher          领域事件发布器（可为 null）
     * @param treeLazyConfig               树懒加载配置（可为 null）
     * @param spELConditionEvaluatorProvider SpEL 评估器提供者（可为 null）
     * @param eventStoreProvider           事件存储提供者（可为 null）
     * @since 1.1.0
     */
    public DomainHealthIndicator(DomainEventPublisher domainEventPublisher,
                                 TreeLazyConfig treeLazyConfig,
                                 ObjectProvider<SpELConditionEvaluator> spELConditionEvaluatorProvider,
                                 ObjectProvider<EventStore> eventStoreProvider) {
        this.domainEventPublisher = domainEventPublisher;
        this.treeLazyConfig = treeLazyConfig;
        this.spELConditionEvaluatorProvider = spELConditionEvaluatorProvider;
        this.eventStoreProvider = eventStoreProvider;
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

        if (eventStoreProvider != null) {
            EventStore eventStore = eventStoreProvider.getIfAvailable();
            details.put("eventStore.available", eventStore != null);
        }

        if (spELConditionEvaluatorProvider != null) {
            SpELConditionEvaluator evaluator = spELConditionEvaluatorProvider.getIfAvailable();
            if (evaluator != null) {
                details.put("spELConditionEvaluator.available", true);
                details.put("spELConditionEvaluator.cacheSize", evaluator.getCacheSize());
            } else {
                details.put("spELConditionEvaluator.available", false);
            }
        }

        if (treeLazyConfig != null) {
            details.put("tree.lazy.enabled", treeLazyConfig.isEnabled());
            details.put("tree.lazy.maxLazyDepth", treeLazyConfig.getMaxLazyDepth());
            details.put("tree.lazy.batchSize", treeLazyConfig.getBatchSize());
        }

        return Health.up().withDetails(details).build();
    }
}
