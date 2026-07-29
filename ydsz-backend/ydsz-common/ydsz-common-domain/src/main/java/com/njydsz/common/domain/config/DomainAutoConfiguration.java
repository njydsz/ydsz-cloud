package com.njydsz.common.domain.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.domain.dag.SpELConditionEvaluator;
import com.njydsz.common.domain.health.DomainHealthIndicator;

/**
 * Domain 模块自动配置
 *
 * <p>激活领域模型层的配置属性绑定，包括：
 * <ul>
 *   <li>SpEL 条件评估器（DAG 条件分支节点使用）</li>
 *   <li>模块健康指标（DomainHealthIndicator，需 spring-boot-health 在 classpath）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.domain", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DomainProperties.class)
public class DomainAutoConfiguration {

    /**
     * 注册 SpEL 条件评估器（DAG 条件分支节点使用）。
     *
     * @param domainProperties 配置属性
     * @return SpELConditionEvaluator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SpELConditionEvaluator spELConditionEvaluator(DomainProperties domainProperties) {
        return new SpELConditionEvaluator(
                domainProperties.getSpel().isCacheEnabled(),
                domainProperties.getSpel().getCacheMaxSize());
    }

    /**
     * 注册 Domain 模块健康指标
     *
     * <p>当 spring-boot-health 在 classpath 时自动注册。
     *
     * @param spELConditionEvaluatorProvider SpEL 评估器提供者
     * @return Domain 健康指标实例
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(DomainHealthIndicator.class)
    public DomainHealthIndicator domainHealthIndicator(
            org.springframework.beans.factory.ObjectProvider<SpELConditionEvaluator> spELConditionEvaluatorProvider) {
        return new DomainHealthIndicator(spELConditionEvaluatorProvider);
    }
}
