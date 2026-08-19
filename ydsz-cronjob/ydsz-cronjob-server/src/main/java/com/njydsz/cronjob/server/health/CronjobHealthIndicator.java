package com.njydsz.cronjob.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.cronjob.infra.entity.job.Job;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 定时任务调度引擎健康检查指标。
 *
 * <p>检查项：Redis 连通性、Leader 选举状态、DB 连通性（任务数/运行中日志数）、调度器配置摘要。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
public class CronjobHealthIndicator extends AbstractModuleHealthIndicator {

  /** Redis 连接工厂（可选依赖，未配置时跳过 Redis 健康检查） */
  private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;

  /** Leader 选举器（可选依赖，未配置时报告 leaderless 模式） */
  private final ObjectProvider<LeaderElector> leaderElectorProvider;

  /** 任务 Mapper（可选依赖，未配置时跳过任务数探针） */
  private final ObjectProvider<JobMapper> jobMapperProvider;

  /** Micrometer 指标采集器（可选依赖） */
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

  /** 调度引擎配置属性 */
  private final CronjobProperties cronjobProperties;

  /**
   * 构造健康检查组件。
   *
   * <p>所有依赖通过 {@link ObjectProvider} 注入，支持可选装配场景（如单体部署不启用 Leader 选举）。
   *
   * @param redisConnectionFactoryProvider Redis 连接工厂提供者
   * @param leaderElectorProvider Leader 选举器提供者
   * @param jobMapperProvider 任务 Mapper 提供者
   * @param cronjobMetricsProvider 指标采集器提供者
   * @param cronjobProperties 调度引擎配置属性
   */
  public CronjobHealthIndicator(
      ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
      ObjectProvider<LeaderElector> leaderElectorProvider,
      ObjectProvider<JobMapper> jobMapperProvider,
      ObjectProvider<CronjobMetrics> cronjobMetricsProvider,
      CronjobProperties cronjobProperties) {
    this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    this.leaderElectorProvider = leaderElectorProvider;
    this.jobMapperProvider = jobMapperProvider;
    this.cronjobMetricsProvider = cronjobMetricsProvider;
    this.cronjobProperties = cronjobProperties;
  }

  /**
   * 执行健康检查，依次检测 Redis 连通性、Leader 选举状态、DB 探针（任务数）、 调度器配置摘要、Metrics 可用性。
   *
   * @param builder Spring Boot Health 构建器
   */
  @Override
  protected void doHealthCheck(Health.Builder builder) {
    // 1. Redis 连通性
    RedisConnectionFactory redisFactory = redisConnectionFactoryProvider.getIfAvailable();
    if (redisFactory != null) {
      checkRedis(builder, () -> redisFactory.getConnection().ping());
    } else {
      checkRedisNotConfigured(builder);
    }

    // 2. Leader 选举状态
    LeaderElector leaderElector = leaderElectorProvider.getIfAvailable();
    if (leaderElector != null && cronjobProperties.getLeader().isEnabled()) {
      try {
        String leaderRole = cronjobProperties.getLeader().getRole();
        boolean isLeader = leaderElector.isLeader(leaderRole);
        String currentLeader = leaderElector.getCurrentLeader(leaderRole);
        Map<String, Object> leaderInfo = new LinkedHashMap<>();
        leaderInfo.put("enabled", true);
        leaderInfo.put("isLeader", isLeader);
        leaderInfo.put("currentLeader", currentLeader != null ? currentLeader : "none");
        leaderInfo.put("role", leaderRole);
        builder.withDetail("leader", leaderInfo);
      } catch (Exception e) {
        builder.withDetail("leader", "ERROR - " + extractMessage(e));
      }
    } else {
      Map<String, Object> leaderInfo = new LinkedHashMap<>();
      leaderInfo.put("enabled", cronjobProperties.getLeader().isEnabled());
      leaderInfo.put("mode", "leaderless");
      builder.withDetail("leader", leaderInfo);
    }

    // 3. DB 探针 — 任务数
    JobMapper jobMapper = jobMapperProvider.getIfAvailable();
    if (jobMapper != null) {
      checkTableProbeWithValue(
          builder,
          "normalJobCount",
          () ->
              jobMapper.selectCount(
                  new LambdaQueryWrapper<Job>()
                      .eq(Job::getStatus, "NORMAL")
                      .eq(Job::getDeleted, 0)));
    }

    // 4. 调度器配置摘要
    Map<String, Object> schedulerInfo = new LinkedHashMap<>();
    schedulerInfo.put("scanIntervalMs", cronjobProperties.getScanner().getIntervalMs());
    schedulerInfo.put("maxBatchSize", cronjobProperties.getScanner().getBatchSize());
    schedulerInfo.put("lockTtlSeconds", cronjobProperties.getScanner().getLockTtlSeconds());
    schedulerInfo.put("failoverEnabled", cronjobProperties.getAnomalyRecovery().isFailoverEnabled());
    schedulerInfo.put("timeoutMonitorEnabled", cronjobProperties.getLeader().isEnabled());
    schedulerInfo.put(
        "selfHealingEnabled",
        cronjobProperties.getAnomalyRecovery().isSelfHealingEnabled());
    builder.withDetail("scheduler", schedulerInfo);

    // 5. Metrics 可用性
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    builder.withDetail("metricsEnabled", metrics != null);
  }
}
