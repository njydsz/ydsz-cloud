package com.njydsz.common.domain.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;

import com.njydsz.common.domain.dag.SpELConditionEvaluator;
import com.njydsz.common.domain.event.DomainEventPublisher;
import com.njydsz.common.domain.event.EventStore;
import com.njydsz.common.domain.health.DomainHealthIndicator;
import com.njydsz.common.domain.tree.TreeLazyConfig;
/**
 * Domain 模块自动配置
 *
 * <p>激活领域模型层的配置属性绑定，包括：
 * <ul>
 *   <li>树形结构懒加载配置（TreeLazyConfig）</li>
 *   <li>领域事件发布器（DomainEventPublisher）</li>
 *   <li>模块健康指标（DomainHealthIndicator，需 spring-boot-health 在 classpath）</li>
 * </ul>
 *
 * <p><b>EventStore SPI：</b>{@link com.njydsz.common.domain.event.EventStore}
 * 作为 SPI 接口保留，业务模块可按需实现并注册为 Bean。本模块不提供默认实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.domain", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({TreeLazyConfig.class, DomainProperties.class})
public class DomainAutoConfiguration {

    /**
     * 注册领域事件发布器
     *
     * <p>如果容器中存在 {@link TaskExecutor}，则注入以支持异步发布能力；
     * 否则退化为仅同步发布模式。
     *
     * @param eventPublisher      Spring 应用事件发布器
     * @param taskExecutorProvider 异步任务执行器提供者（可选）
     * @return 领域事件发布器实例
     */
    @Bean
    @ConditionalOnBean(ApplicationEventPublisher.class)
    @ConditionalOnMissingBean(DomainEventPublisher.class)
    public DomainEventPublisher domainEventPublisher(ApplicationEventPublisher eventPublisher,
                                                      ObjectProvider<TaskExecutor> taskExecutorProvider,
                                                      DomainProperties domainProperties) {
        TaskExecutor taskExecutor = taskExecutorProvider.getIfAvailable();
        return new DomainEventPublisher(eventPublisher, taskExecutor,
                                         domainProperties.getEvent().isAsyncEnabled());
    }

    /**
     * 注册 SpEL 条件评估器（DAG 条件分支节点使用）。
     *
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
     * 报告领域事件发布器状态和树懒加载配置。
     *
     * @param eventPublisherProvider 领域事件发布器提供者
     * @param treeLazyConfig         树懒加载配置
     * @return Domain 健康指标实例
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(DomainHealthIndicator.class)
    public DomainHealthIndicator domainHealthIndicator(ObjectProvider<DomainEventPublisher> eventPublisherProvider,
                                                         TreeLazyConfig treeLazyConfig,
                                                         ObjectProvider<SpELConditionEvaluator> spELConditionEvaluatorProvider,
                                                         ObjectProvider<EventStore> eventStoreProvider) {
        DomainEventPublisher eventPublisher = eventPublisherProvider.getIfAvailable();
        return new DomainHealthIndicator(eventPublisher, treeLazyConfig,
                                         spELConditionEvaluatorProvider, eventStoreProvider);
    }
}
