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

import com.njydsz.cronjob.domain.repository.JobNodeRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.repository.WebhookRetryRepository;
import com.njydsz.cronjob.server.core.config.CronjobThreadPoolRegistry;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.health.CronjobHealthIndicator;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 定时任务调度引擎自动配置类。
 *
 * <p>注册调度引擎核心组件，启用 @Scheduled 定时任务支持。 通过 {@code ydsz.cronjob.enabled=true}（默认启用）控制是否加载。
 *
 * <p>P2-修正：使用 JobRepository 替换 JobMapper 传递，符合 DDD 分层规范。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "ydsz.cronjob",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CronjobAutoConfiguration {

  /** P1-1: 健康检查 Bean 注册（统一模式，不使用 @Component） */
  @Bean
  @ConditionalOnClass(HealthIndicator.class)
  @ConditionalOnMissingBean(CronjobHealthIndicator.class)
  public CronjobHealthIndicator cronjobHealthIndicator(
      ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
      ObjectProvider<LeaderElector> leaderElectorProvider,
      ObjectProvider<JobRepository> jobRepositoryProvider,
      ObjectProvider<JobNodeRepository> jobNodeRepositoryProvider,
      ObjectProvider<WebhookRetryRepository> webhookRetryRepositoryProvider,
      ObjectProvider<CronjobMetrics> cronjobMetricsProvider,
      CronjobProperties cronjobProperties) {
    return new CronjobHealthIndicator(
        redisConnectionFactoryProvider,
        leaderElectorProvider,
        jobRepositoryProvider,
        jobNodeRepositoryProvider,
        webhookRetryRepositoryProvider,
        cronjobMetricsProvider,
        cronjobProperties);
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

  // P0-1 整改（26.09.12）：删除本模块自建的 ThreadPoolMetricsEndpoint Bean 注册。
  //
  // 原实现以 @Endpoint(id = "threadpools") 暴露线程池指标，与 ydsz-common-thread 的
  // com.njydsz.common.thread.actuator.ThreadPoolMetricsEndpoint（同一 id）重复。
  // Spring Boot 4.1.0 的 EndpointDiscoverer#createEndpointBeans() 对重复 endpoint id 直接抛
  // IllegalStateException("Found two endpoints with the id 'threadpools'")，一旦
  // management.endpoints.web.exposure.include 含 threadpools 即导致应用启动失败。
  //
  // 整改方式：统一由 ydsz-common-thread 提供 /actuator/threadpools 端点。
  // CronjobThreadPoolRegistry#register 已把本模块线程池同步注册进 common 的 ThreadPoolRegistry，
  // 故运维观测能力不丢失（符合《云顶编码规范》§16.4 统一线程池管理、§33.2 必用 common 能力清单）。
}
