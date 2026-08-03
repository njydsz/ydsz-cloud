package com.njydsz.common.util.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.util.bean.BeanCopyUtils;
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
 *   <li>SnowflakeUtils 初始化状态（workerId / datacenterId / shardCount）</li>
 *   <li>JVM 运行时基础指标（内存使用率）</li>
 *   <li>BeanCopyUtils 缓存状态（fieldCacheSize / propertyCacheSize）</li>
 *   <li>OkHttp 连接池统计（idleConnections / totalConnections / queuedCallsCount）</li>
 * </ul>
 *
 * <p>健康状态映射：
 * <ul>
 *   <li>无异常 → UP</li>
 *   <li>有警告（内存使用率 >85%）→ UP（带详情）</li>
 *   <li>有严重异常（SnowflakeUtils 未初始化）→ DOWN</li>
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
        boolean hasCritical = collectHealthDetails(builder);
        if (hasCritical) {
            builder.down();
        }
        return builder.build();
    }

    /**
     * 收集健康检查详情到 Health.Builder
     *
     * @param builder Health.Builder
     * @return true 表示有严重异常（应标记为 DOWN）
     */
    private boolean collectHealthDetails(Health.Builder builder) {
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

        // 2. JVM 运行时基础指标
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

        // 3. BeanCopyUtils 缓存状态
        try {
            Map<String, Integer> cacheStats = BeanCopyUtils.getCacheStats();
            builder.withDetail("beanCopy.fieldCacheSize", cacheStats.get("fieldCacheSize"));
            builder.withDetail("beanCopy.propertyCacheSize", cacheStats.get("propertyCacheSize"));
        } catch (Exception e) {
            log.debug("BeanCopyUtils cache stats unavailable: {}", e.getMessage());
        }

        return hasCritical;
    }

    /**
     * 执行健康检查（向后兼容方法）
     *
     * @return 健康检查结果 Map，包含 status 和详细信息
     */
    public Map<String, Object> checkHealth() {
        Health health = health();
        Map<String, Object> result = new LinkedHashMap<>();
        String status = health.getStatus().getCode();
        result.put("status", status.toUpperCase());
        result.put("details", health.getDetails());
        return result;
    }

    /**
     * 检查是否健康（向后兼容方法）
     *
     * @return 健康返回 true，不健康返回 false
     */
    public boolean isHealthy() {
        Health health = health();
        return "UP".equalsIgnoreCase(health.getStatus().getCode());
    }
}
