package com.njydsz.pmis.common.lock.health;

import jakarta.annotation.Resource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.njydsz.pmis.common.lock.metrics.LockMetrics;
import com.njydsz.pmis.common.lock.scheduler.LockWatchDog;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式锁健康检查
 *
 * <p>检测 Redis 连接状态和锁专用资源，暴露 /actuator/health/lock 端点。
 *
 * <p><b>检测逻辑：</b>
 * <ul>
 *   <li>验证 RedisConnectionFactory 连接状态</li>
 *   <li>执行 PING 命令验证连接可达性</li>
 *   <li>返回连接耗时作为性能指标</li>
 *   <li>检测看门狗活跃续期任务数（可选）</li>
 *   <li>检测锁降级状态和连续失败次数（可选）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class LockHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;
    private final ObjectProvider<LockWatchDog> lockWatchDogProvider;
    private final ObjectProvider<LockMetrics> lockMetricsProvider;

    public LockHealthIndicator(RedisConnectionFactory redisConnectionFactory,
                               ObjectProvider<LockWatchDog> lockWatchDogProvider,
                               ObjectProvider<LockMetrics> lockMetricsProvider) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.lockWatchDogProvider = lockWatchDogProvider;
        this.lockMetricsProvider = lockMetricsProvider;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        boolean redisHealthy = checkRedisHealth(builder);
        checkWatchDogHealth(builder);
        checkMetricsHealth(builder);

        if (!redisHealthy) {
            builder = Health.down();
        }
        return builder.build();
    }

    /**
     * 检查 Redis 连接健康状态
     *
     * @param builder Health 构建器
     * @return true-Redis 可用，false-不可用
     */
    private boolean checkRedisHealth(Health.Builder builder) {
        try {
            long startTime = System.currentTimeMillis();
            RedisConnection connection = null;
            try {
                connection = redisConnectionFactory.getConnection();
                String pong = connection.ping();
                long responseTime = System.currentTimeMillis() - startTime;

                builder.withDetail("lockType", "redis");
                builder.withDetail("responseTimeMs", responseTime);

                if (!"PONG".equals(pong)) {
                    builder.withDetail("redisStatus", "unexpected response: " + pong);
                    return false;
                }
                builder.withDetail("redisStatus", "UP");
                return true;
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception e) {
                        log.debug("关闭 Redis 连接异常", e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("分布式锁健康检查失败", e);
            builder.withDetail("lockType", "redis");
            builder.withDetail("redisStatus", "DOWN");
            builder.withDetail("redisError", e.getMessage());
            return false;
        }
    }

    /**
     * 检查看门狗资源健康状态
     *
     * @param builder Health 构建器
     */
    private void checkWatchDogHealth(Health.Builder builder) {
        LockWatchDog watchDog = lockWatchDogProvider.getIfAvailable();
        if (watchDog == null) {
            builder.withDetail("watchDog", "not configured");
            return;
        }
        int maxRenewTimes = watchDog.getMaxRenewCount();
        builder.withDetail("watchDogMaxRenewTimes", maxRenewTimes);
    }

    /**
     * 检查锁指标健康状态
     *
     * @param builder Health 构建器
     */
    private void checkMetricsHealth(Health.Builder builder) {
        LockMetrics metrics = lockMetricsProvider.getIfAvailable();
        if (metrics == null) {
            builder.withDetail("lockMetrics", "not configured");
            return;
        }
        builder.withDetail("activeLocks", metrics.getActiveLocks());
        builder.withDetail("acquireSuccessCount", metrics.getAcquireSuccessCount());
        builder.withDetail("acquireFailCount", metrics.getAcquireFailCount());
        builder.withDetail("lockTimeoutCount", metrics.getLockTimeoutCount());
        builder.withDetail("watchdogRenewCount", metrics.getWatchdogRenewCount());
        builder.withDetail("competitionCount", metrics.getCompetitionCount());
    }
}
