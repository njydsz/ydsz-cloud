package com.njydsz.cronjob.server.config;

import com.njydsz.cronjob.domain.dag.SpELConditionEvaluator;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.core.config.CronjobThreadPoolRegistry;
import com.njydsz.cronjob.server.core.config.ThreadPoolMetricsEndpoint;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.health.CronjobHealthIndicator;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务调度引擎自动配置类。
 *
 * <p>注册调度引擎核心组件，启用 @Scheduled 定时任务支持。 通过 {@code ydsz.cronjob.enabled=true}（默认启用）控制是否加载。
 *
 * <h3>对标</h3>
 *
 * <p>对标 XXL-Job 的 XxlJobAdminConfig 和 PowerJob 的 PowerJobAutoConfiguration， 提供标准 Spring Boot
 * 自动配置能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.cronjob", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CronjobAutoConfiguration {

  /** P1-1: 健康检查 Bean 注册（统一模式，不使用 @Component） */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(CronjobHealthIndicator.class)
  public CronjobHealthIndicator cronjobHealthIndicator(
      ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
      ObjectProvider<LeaderElector> leaderElectorProvider,
      ObjectProvider<JobMapper> jobMapperProvider,
      ObjectProvider<CronjobMetrics> cronjobMetricsProvider,
      CronjobProperties cronjobProperties) {
    return new CronjobHealthIndicator(
        redisConnectionFactoryProvider,
        leaderElectorProvider,
        jobMapperProvider,
        cronjobMetricsProvider,
        cronjobProperties);
  }

  /**
   * 注册 SpEL 条件评估器（DAG 条件分支节点使用）。
   *
   * <p><b>v1.4.0</b>：自 ydsz-common-domain 迁移至本模块（原由 DomainAutoConfiguration 注册）。
   *
   * <p><b>P1-2:</b> 缓存配置从 {@link CronjobProperties#getSpel()} 读取， 可通过 {@code
   * ydsz.cronjob.spel.enabled/max-size} 动态调整。
   *
   * @param properties 定时任务配置属性
   * @return SpELConditionEvaluator 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public SpELConditionEvaluator spELConditionEvaluator(CronjobProperties properties) {
    return new SpELConditionEvaluator(
        properties.getSpel().isEnabled(), properties.getSpel().getMaxSize());
  }

  /**
   * P1-A2: 注册线程池注册表（集中管理所有线程池的生命周期）。
   *
   * <p>使用自动配置模式注册（而非 @Component），避免 standalone 模式下出现不必要的 Bean 注册。
   *
   * @param properties 调度引擎配置属性
   * @return CronjobThreadPoolRegistry 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public CronjobThreadPoolRegistry cronjobThreadPoolRegistry(CronjobProperties properties) {
    return new CronjobThreadPoolRegistry(properties);
  }

  /**
   * P1-A2: 注册线程池指标 Actuator 端点。
   *
   * <p>暴露 {@code /actuator/threadpools} 端点，提供线程池运行时指标查询。
   * 仅当 Spring Boot Actuator 在 classpath 中且 endpoint 启用时注册。
   *
   * @param registry 线程池注册表
   * @return ThreadPoolMetricsEndpoint 实例
   */
  @Bean
  @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
  @ConditionalOnAvailableEndpoint(endpoint = ThreadPoolMetricsEndpoint.class)
  @ConditionalOnMissingBean
  public ThreadPoolMetricsEndpoint threadPoolMetricsEndpoint(CronjobThreadPoolRegistry registry) {
    return new ThreadPoolMetricsEndpoint(registry);
  }
}
