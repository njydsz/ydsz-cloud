package com.njydsz.cronjob.server.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.health.AbstractModuleHealthIndicator;
import com.njydsz.cronjob.domain.entity.job.JobDO;
import com.njydsz.cronjob.domain.entity.log.JobLogDO;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * 定时任务调度引擎健康检查指标。
 *
 * <p>检查项：Redis 连通性、Leader 选举状态、DB 连通性（任务数/运行中日志数）、调度器配置摘要。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(HealthIndicator.class)
public class CronjobHealthIndicator extends AbstractModuleHealthIndicator {

    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
    private final ObjectProvider<LeaderElector> leaderElectorProvider;
    private final ObjectProvider<JobMapper> jobMapperProvider;
    private final ObjectProvider<JobLogMapper> jobLogMapperProvider;
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
    private final CronjobProperties cronjobProperties;

    public CronjobHealthIndicator(
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
            ObjectProvider<LeaderElector> leaderElectorProvider,
            ObjectProvider<JobMapper> jobMapperProvider,
            ObjectProvider<JobLogMapper> jobLogMapperProvider,
            ObjectProvider<CronjobMetrics> cronjobMetricsProvider,
            CronjobProperties cronjobProperties) {
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
        this.leaderElectorProvider = leaderElectorProvider;
        this.jobMapperProvider = jobMapperProvider;
        this.jobLogMapperProvider = jobLogMapperProvider;
        this.cronjobMetricsProvider = cronjobMetricsProvider;
        this.cronjobProperties = cronjobProperties;
    }

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
            checkTableProbeWithValue(builder, "normalJobCount", () ->
                    jobMapper.selectCount(new LambdaQueryWrapper<JobDO>()
                            .eq(JobDO::getStatus, "NORMAL")
                            .eq(JobDO::getDeleted, 0)));
        }

        // 4. DB 探针 — 运行中日志数
        JobLogMapper jobLogMapper = jobLogMapperProvider.getIfAvailable();
        if (jobLogMapper != null) {
            checkTableProbeWithValue(builder, "runningJobCount", () ->
                    jobLogMapper.selectCount(new LambdaQueryWrapper<JobLogDO>()
                            .eq(JobLogDO::getStatus, "RUNNING")
                            .eq(JobLogDO::getDeleted, 0)));
        }

        // 5. 调度器配置摘要
        Map<String, Object> schedulerInfo = new LinkedHashMap<>();
        schedulerInfo.put("scanIntervalMs", cronjobProperties.getScanner().getScanIntervalMs());
        schedulerInfo.put("maxBatchSize", cronjobProperties.getScanner().getMaxBatchSize());
        schedulerInfo.put("lockTtlSeconds", cronjobProperties.getScanner().getLockTtlSeconds());
        schedulerInfo.put("failoverEnabled", cronjobProperties.getFailover().isEnabled());
        schedulerInfo.put("timeoutMonitorEnabled", cronjobProperties.getLeader().isEnabled());
        schedulerInfo.put("selfHealingEnabled", cronjobProperties.getSelfHealing() != null
                && cronjobProperties.getSelfHealing().isEnabled());
        builder.withDetail("scheduler", schedulerInfo);

        // 6. Metrics 可用性
        CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
        builder.withDetail("metricsEnabled", metrics != null);
    }
}
