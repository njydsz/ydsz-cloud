package com.njydsz.cronjob.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.njydsz.cronjob.domain.dag.SpELConditionEvaluator;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.health.CronjobHealthIndicator;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 定时任务调度引擎自动配置类。
 *
 * <p>注册调度引擎核心组件，启用 @Scheduled 定时任务支持。
 * 通过 {@code ydsz.cronjob.enabled=true}（默认启用）控制是否加载。
 *
 * <h3>对标</h3>
 * <p>对标 XXL-Job 的 XxlJobAdminConfig 和 PowerJob 的 PowerJobAutoConfiguration，
 * 提供标准 Spring Boot 自动配置能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.cronjob", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CronjobAutoConfiguration {

    /**
     * P1-1: 健康检查 Bean 注册（统一模式，不使用 @Component）
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(CronjobHealthIndicator.class)
    public CronjobHealthIndicator cronjobHealthIndicator(
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
            ObjectProvider<LeaderElector> leaderElectorProvider,
            ObjectProvider<JobMapper> jobMapperProvider,
            ObjectProvider<CronjobMetrics> cronjobMetricsProvider,
            CronjobProperties cronjobProperties) {
        return new CronjobHealthIndicator(redisConnectionFactoryProvider, leaderElectorProvider,
                jobMapperProvider, cronjobMetricsProvider, cronjobProperties);
    }

    /**
     * 注册 SpEL 条件评估器（DAG 条件分支节点使用）。
     *
     * <p><b>v1.4.0</b>：自 ydsz-common-domain 迁移至本模块（原由 DomainAutoConfiguration 注册）。
     *
     * <p><b>P1-2:</b> 缓存配置从 {@link CronjobProperties#getSpel()} 读取，
     * 可通过 {@code ydsz.cronjob.spel.enabled/max-size} 动态调整。
     *
     * @param properties 定时任务配置属性
     * @return SpELConditionEvaluator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SpELConditionEvaluator spELConditionEvaluator(CronjobProperties properties) {
        return new SpELConditionEvaluator(properties.getSpel().isEnabled(), properties.getSpel().getMaxSize());
    }
}
