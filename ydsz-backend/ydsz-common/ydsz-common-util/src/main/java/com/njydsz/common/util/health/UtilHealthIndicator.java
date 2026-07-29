package com.njydsz.common.util.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.util.id.SnowflakeUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Util 模块健康检查指示器
 *
 * <p>实现 Spring {@link HealthIndicator} 接口，通过 /actuator/health 端点暴露健康状态。
 * 在 {@link com.njydsz.common.util.config.UtilAutoConfiguration} 中以 {@code @Bean} 注册。
 *
 * <p>检查内容：
 * <ul>
 *   <li>SnowflakeUtils 初始化状态（workerId / datacenterId）</li>
 *   <li>RateLimiterUtils 注册表大小（内存泄漏预警）</li>
 *   <li>JVM 运行时基础指标（内存使用率）</li>
 * </ul>
 *
 * <p>健康状态映射：
 * <ul>
 *   <li>无异常 → UP</li>
 *   <li>有警告（内存使用率 >85% 或注册表接近上限）→ UP（带详情）</li>
 *   <li>有严重异常（SnowflakeUtils 未初始化）→ DOWN</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class UtilHealthIndicator implements HealthIndicator {

    /** 限流器注册表大小预警阈值 */
    private static final int RATE_LIMITER_WARNING_THRESHOLD = 5000;

    /** JVM 内存使用率警告阈值（百分比） */
    private static final double MEMORY_WARNING_PERCENT = 85.0;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        boolean hasCritical = false;

        // 1. SnowflakeUtils 检查
        try {
            SnowflakeUtils instance = SnowflakeUtils.getInstance();
            builder.withDetail("snowflake.initialized", true);
            builder.withDetail("snowflake.workerId", instance.getWorkerId());
            builder.withDetail("snowflake.datacenterId", instance.getDatacenterId());
            builder.withDetail("snowflake.lastTimestamp", instance.getLastTimestamp());
        } catch (Exception e) {
            log.warn("SnowflakeUtils health check failed: {}", e.getMessage());
            builder.withDetail("snowflake.initialized", false);
            builder.withDetail("snowflake.error", e.getMessage());
            hasCritical = true;
        }

        // 2. RateLimiterUtils 注册表检查
        try {
            // 不再使用已废弃的 RateLimiterUtils，跳过健康检查（无实际业务依赖）
            // TODO: 集成 RedisRateLimiter 指标后添加 check()
            builder.withDetail("rateLimiter", "已废弃，集成 RedisRateLimiter 后添加 check()");
            builder.withDetail("rateLimiter.note", "RateLimiterUtils 已废弃，集成 RedisRateLimiter 后添加 check()");
        } catch (Exception e) {
            log.warn("RateLimiterUtils health check failed: {}", e.getMessage());
            builder.withDetail("rateLimiter.error", e.getMessage());
        }

        // 3. JVM 运行时基础指标
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsagePercent = maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;
            builder.withDetail("jvm.availableProcessors", runtime.availableProcessors());
            builder.withDetail("jvm.maxMemoryMB", maxMemory / (1024 * 1024));
            builder.withDetail("jvm.usedMemoryMB", usedMemory / (1024 * 1024));
            builder.withDetail("jvm.memoryUsagePercent", String.format("%.1f%%", memoryUsagePercent));
            builder.withDetail("jvm.memoryWarning", memoryUsagePercent > MEMORY_WARNING_PERCENT);
        } catch (Exception e) {
            log.warn("JVM health check failed: {}", e.getMessage());
            builder.withDetail("jvm.error", e.getMessage());
        }

        // 4. 确定整体状态
        if (hasCritical) {
            builder.down();
        }

        return builder.build();
    }

    /**
     * 健康状态枚举（向后兼容，用于非 Spring 环境）
     */
    public enum HealthStatus {
        UP,
        DOWN,
        DEGRADED
    }

    /**
     * 执行健康检查（向后兼容方法）
     *
     * @return 健康检查结果 Map，包含 status 和详细信息
     */
    public Map<String, Object> checkHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> details = new LinkedHashMap<>();
        boolean hasCritical = false;
        boolean hasWarning = false;

        // 1. SnowflakeUtils 检查
        try {
            SnowflakeUtils instance = SnowflakeUtils.getInstance();
            Map<String, Object> snowflakeStatus = new LinkedHashMap<>();
            snowflakeStatus.put("initialized", true);
            snowflakeStatus.put("workerId", instance.getWorkerId());
            snowflakeStatus.put("datacenterId", instance.getDatacenterId());
            details.put("snowflake", snowflakeStatus);
        } catch (Exception e) {
            log.warn("SnowflakeUtils health check failed: {}", e.getMessage());
            Map<String, Object> snowflakeStatus = new LinkedHashMap<>();
            snowflakeStatus.put("initialized", false);
            snowflakeStatus.put("error", e.getMessage());
            details.put("snowflake", snowflakeStatus);
            hasCritical = true;
        }

        // 2. RateLimiterUtils 注册表检查
        try {
            int semaphoreSize = RateLimiterUtils.getSemaphoreRegistrySize();
            int tokenBucketSize = RateLimiterUtils.getTokenBucketRegistrySize();
            int totalSize = semaphoreSize + tokenBucketSize;
            Map<String, Object> rateLimiterStatus = new LinkedHashMap<>();
            rateLimiterStatus.put("semaphoreRegistrySize", semaphoreSize);
            rateLimiterStatus.put("tokenBucketRegistrySize", tokenBucketSize);
            rateLimiterStatus.put("totalSize", totalSize);
            rateLimiterStatus.put("warningThreshold", RATE_LIMITER_WARNING_THRESHOLD);
            rateLimiterStatus.put("nearLimit", totalSize >= RATE_LIMITER_WARNING_THRESHOLD);
            details.put("rateLimiter", rateLimiterStatus);
            if (totalSize >= RATE_LIMITER_WARNING_THRESHOLD) {
                hasWarning = true;
            }
        } catch (Exception e) {
            log.warn("RateLimiterUtils health check failed: {}", e.getMessage());
            details.put("rateLimiterError", e.getMessage());
            hasWarning = true;
        }

        // 3. JVM 运行时基础指标
        try {
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> jvmStatus = new LinkedHashMap<>();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsagePercent = maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;
            jvmStatus.put("availableProcessors", runtime.availableProcessors());
            jvmStatus.put("maxMemoryMB", maxMemory / (1024 * 1024));
            jvmStatus.put("usedMemoryMB", usedMemory / (1024 * 1024));
            jvmStatus.put("memoryUsagePercent", String.format("%.1f%%", memoryUsagePercent));
            jvmStatus.put("memoryWarning", memoryUsagePercent > MEMORY_WARNING_PERCENT);
            details.put("jvm", jvmStatus);
            if (memoryUsagePercent > MEMORY_WARNING_PERCENT) {
                hasWarning = true;
            }
        } catch (Exception e) {
            log.warn("JVM health check failed: {}", e.getMessage());
            details.put("jvmError", e.getMessage());
        }

        // 4. 确定整体状态
        String status;
        if (hasCritical) {
            status = HealthStatus.DOWN.name();
        } else if (hasWarning) {
            status = HealthStatus.DEGRADED.name();
        } else {
            status = HealthStatus.UP.name();
        }

        result.put("status", status);
        result.put("details", details);
        return result;
    }

    /**
     * 检查是否健康（向后兼容方法）
     *
     * @return 健康返回 true，不健康返回 false
     */
    public boolean isHealthy() {
        String status = (String) checkHealth().get("status");
        return HealthStatus.UP.name().equals(status) || HealthStatus.DEGRADED.name().equals(status);
    }
}
