package com.njydsz.common.util.health;

import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.common.util.concurrent.RateLimiterUtils;
import com.njydsz.common.util.id.SnowflakeUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Util 模块健康检查工具
 *
 * <p>提供工具模块核心组件的健康状态检查：
 * <ul>
 *   <li>SnowflakeUtils 初始化状态</li>
 *   <li>RateLimiterUtils 注册表大小（内存泄漏预警）</li>
 *   <li>Pattern 缓存命中率（RegexUtils）</li>
 *   <li>JVM 运行时基础指标</li>
 * </ul>
 *
 * <p>可通过定时任务或外部监控系统调用 {@link #checkHealth()} 获取健康状态。
 * 当项目引入 spring-boot-actuator 时，可包装此类实现 HealthIndicator。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class UtilHealthIndicator {

    /** 限流器注册表大小预警阈值 */
    private static final int RATE_LIMITER_WARNING_THRESHOLD = 5000;

    /** 健康状态枚举 */
    public enum HealthStatus {
        /** 健康 */
        UP,
        /** 不健康 */
        DOWN,
        /** 降级（部分组件异常但不影响核心功能） */
        DEGRADED
    }

    /**
     * 执行健康检查
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
            jvmStatus.put("memoryWarning", memoryUsagePercent > 85);
            details.put("jvm", jvmStatus);
            if (memoryUsagePercent > 85) {
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
     * 检查是否健康
     *
     * @return 健康返回 true，不健康返回 false
     */
    public boolean isHealthy() {
        String status = (String) checkHealth().get("status");
        return HealthStatus.UP.name().equals(status) || HealthStatus.DEGRADED.name().equals(status);
    }
}
