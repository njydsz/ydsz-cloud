package com.njydsz.cronjob.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.cronjob.domain.repository.JobNodeRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.repository.WebhookRetryRepository;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 定时任务调度引擎健康检查指标。
 *
 * <p>检查项：Redis 连通性、Leader 选举状态、DB 连通性（任务数/运行中日志数）、调度器配置摘要、
 * 节点健康概况、Webhook 重试队列深度。
 *
 * <p>P2-修正：使用 JobRepository 替换 JobMapper，符合 DDD 分层规范。
 *
 * <p><b>P1-10 增强：</b>新增节点健康概况（在线/慢节点/高负载节点数）和 Webhook 重试队列深度检查，
 * 当重试队列积压或节点异常时报告降级状态（UP 但带 warning 详情）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
public class CronjobHealthIndicator extends AbstractModuleHealthIndicator {

  /** 慢节点响应时长阈值（毫秒）：响应时长超过 5000ms 判定为慢节点 */
  private static final long SLOW_NODE_RESPONSE_THRESHOLD_MS = 5000L;

  /** 高负载节点阈值：运行任务数超过 50 判定为高负载 */
  private static final int HIGH_LOAD_NODE_THRESHOLD = 50;

  /** Redis 连接工厂（可选依赖，未配置时跳过 Redis 健康检查） */
  private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;

  /** Leader 选举器（可选依赖，未配置时报告 leaderless 模式） */
  private final ObjectProvider<LeaderElector> leaderElectorProvider;

  /** 任务 Repository（可选依赖，未配置时跳过任务数探针） */
  private final ObjectProvider<JobRepository> jobRepositoryProvider;

  /** 节点 Repository（可选依赖，P1-10 新增） */
  private final ObjectProvider<JobNodeRepository> jobNodeRepositoryProvider;

  /** Webhook 重试 Repository（可选依赖，P1-10 新增） */
  private final ObjectProvider<WebhookRetryRepository> webhookRetryRepositoryProvider;

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
   * @param jobRepositoryProvider 任务 Repository 提供者
   * @param jobNodeRepositoryProvider 节点 Repository 提供者
   * @param webhookRetryRepositoryProvider Webhook 重试 Repository 提供者
   * @param cronjobMetricsProvider 指标采集器提供者
   * @param cronjobProperties 调度引擎配置属性
   */
  public CronjobHealthIndicator(
      ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
      ObjectProvider<LeaderElector> leaderElectorProvider,
      ObjectProvider<JobRepository> jobRepositoryProvider,
      ObjectProvider<JobNodeRepository> jobNodeRepositoryProvider,
      ObjectProvider<WebhookRetryRepository> webhookRetryRepositoryProvider,
      ObjectProvider<CronjobMetrics> cronjobMetricsProvider,
      CronjobProperties cronjobProperties) {
    this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    this.leaderElectorProvider = leaderElectorProvider;
    this.jobRepositoryProvider = jobRepositoryProvider;
    this.jobNodeRepositoryProvider = jobNodeRepositoryProvider;
    this.webhookRetryRepositoryProvider = webhookRetryRepositoryProvider;
    this.cronjobMetricsProvider = cronjobMetricsProvider;
    this.cronjobProperties = cronjobProperties;
  }

  /**
   * 执行健康检查，依次检测 Redis 连通性、Leader 选举状态、DB 探针（任务数）、 调度器配置摘要、
   * Metrics 可用性、节点健康概况、Webhook 重试队列深度。
   *
   * @param builder Spring Boot Health 构建器
   */
  @Override
  protected void doHealthCheck(Health.Builder builder) {
    boolean hasWarning = false;

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

    // 3. DB 探针 — 任务数（使用 Repository 替换 Mapper）
    JobRepository jobRepository = jobRepositoryProvider.getIfAvailable();
    if (jobRepository != null) {
      checkTableProbeWithValue(
          builder,
          "normalJobCount",
          () -> jobRepository.countByStatus("NORMAL"));
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

    // ===== P1-10: 节点健康概况 =====
    JobNodeRepository jobNodeRepository = jobNodeRepositoryProvider.getIfAvailable();
    if (jobNodeRepository != null) {
      try {
        Map<String, Object> nodeHealth = new LinkedHashMap<>();
        var onlineNodes = jobNodeRepository.findOnlineNodes();
        nodeHealth.put("onlineCount", onlineNodes.size());

        // 统计慢节点（响应时长 > 5000ms）
        long slowNodeCount = onlineNodes.stream()
            .filter(n -> n.getResponseTimeMs() != null && n.getResponseTimeMs() > SLOW_NODE_RESPONSE_THRESHOLD_MS)
            .count();
        nodeHealth.put("slowNodeCount", slowNodeCount);

        // 统计高负载节点（runningCount > 50）
        long highLoadNodeCount = onlineNodes.stream()
            .filter(n -> n.getRunningCount() != null && n.getRunningCount() > HIGH_LOAD_NODE_THRESHOLD)
            .count();
        nodeHealth.put("highLoadNodeCount", highLoadNodeCount);

        if (slowNodeCount > 0 || highLoadNodeCount > 0) {
          hasWarning = true;
        }
        builder.withDetail("nodeHealth", nodeHealth);
      } catch (Exception e) {
        builder.withDetail("nodeHealth", "ERROR - " + extractMessage(e));
      }
    }

    // ===== P1-10: Webhook 重试队列深度 =====
    WebhookRetryRepository retryRepo = webhookRetryRepositoryProvider.getIfAvailable();
    if (retryRepo != null) {
      try {
        Map<String, Object> retryInfo = new LinkedHashMap<>();
        long pendingCount = retryRepo.countPending();
        long deadCount = retryRepo.countDead();
        retryInfo.put("pendingRetries", pendingCount);
        retryInfo.put("deadRetries", deadCount);

        // 重试队列积压超过 100 条时标记警告
        if (pendingCount > 100) {
          hasWarning = true;
          retryInfo.put("warning", "重试队列积压，建议检查 Webhook 接收方");
        }
        builder.withDetail("webhookRetry", retryInfo);
      } catch (Exception e) {
        builder.withDetail("webhookRetry", "ERROR - " + extractMessage(e));
      }
    }

    // 如果有警告，添加 summary 标记
    if (hasWarning) {
      builder.withDetail("status", "DEGRADED - 存在节点缓慢或队列积压，请查看详情");
    }
  }
}
