package com.njydsz.common.domain.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.domain.dag.SpELConditionEvaluator;

/**
 * Domain 模块健康指标
 *
 * <p>报告 domain 模块核心组件的运行状态：
 * <ul>
 *   <li>SpEL 条件评估器缓存状态</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DomainHealthIndicator implements HealthIndicator {

    private final ObjectProvider<SpELConditionEvaluator> spELConditionEvaluatorProvider;

    /**
     * 构造 Domain 模块健康指标
     *
     * @param spELConditionEvaluatorProvider SpEL 评估器提供者
     * @since 1.3.0
     */
    public DomainHealthIndicator(ObjectProvider<SpELConditionEvaluator> spELConditionEvaluatorProvider) {
        this.spELConditionEvaluatorProvider = spELConditionEvaluatorProvider;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        if (spELConditionEvaluatorProvider != null) {
            SpELConditionEvaluator evaluator = spELConditionEvaluatorProvider.getIfAvailable();
            if (evaluator != null) {
                details.put("spELConditionEvaluator.available", true);
                details.put("spELConditionEvaluator.cacheSize", evaluator.getCacheSize());
            } else {
                details.put("spELConditionEvaluator.available", false);
            }
        }

        return Health.up().withDetails(details).build();
    }
}
