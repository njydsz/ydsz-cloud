package com.njydsz.common.util.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import lombok.extern.slf4j.Slf4j;

/**
 * Util 模块健康检查指示器
 *
 * <p>实现 Spring {@link HealthIndicator} 接口，通过 /actuator/health 端点暴露健康状态。
 * 在 {@link com.njydsz.common.util.config.UtilAutoConfiguration} 中以 {@code @Bean} 注册。
 *
 * <p>检查内容：
 * <ul>
 *   <li>JVM 运行时基础指标（内存使用率）</li>
 * </ul>
 *
 * <p>Snowflake 相关健康检查由 {@link com.njydsz.common.util.id.SnowflakeHealthIndicator}
 * 独立负责，本类不再重复暴露 workerId/datacenterId/lastTimestamp，避免冗余。
 *
 * <p>健康状态映射：
 * <ul>
 *   <li>无异常 → UP</li>
 *   <li>有警告（内存使用率 >85%）→ UP（带详情）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class UtilHealthIndicator implements HealthIndicator {

    /** JVM 内存使用率警告阈值（百分比） */
    private static final double MEMORY_WARNING_PERCENT = 85.0;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        collectHealthDetails(builder);
        return builder.build();
    }

    /**
     * 收集健康检查详情到 Health.Builder
     *
     * @param builder Health.Builder
     */
    private void collectHealthDetails(Health.Builder builder) {
        // JVM 运行时基础指标
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
    }

}
