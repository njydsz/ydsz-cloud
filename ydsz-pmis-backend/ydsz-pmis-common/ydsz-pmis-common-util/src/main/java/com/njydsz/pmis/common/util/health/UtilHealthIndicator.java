package com.njydsz.pmis.common.util.health;

import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.pmis.common.util.id.SnowflakeUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Util 模块健康检查工具
 *
 * <p>提供工具模块核心组件的健康状态检查：
 * <ul>
 *   <li>SnowflakeUtils 初始化状态</li>
 *   <li>Worker ID 和 Datacenter ID 信息</li>
 * </ul>
 *
 * <p>可通过定时任务或外部监控系统调用 {@link #checkHealth()} 获取健康状态。
 * 当项目引入 spring-boot-actuator 时，可包装此类实现 HealthIndicator。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
public class UtilHealthIndicator {

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        /** 健康 */
        UP,
        /** 不健康 */
        DOWN
    }

    /**
     * 执行健康检查
     *
     * @return 健康检查结果 Map，包含 status 和详细信息
     */
    public Map<String, Object> checkHealth() {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> details = new LinkedHashMap<>();

        try {
            SnowflakeUtils instance = SnowflakeUtils.getInstance();
            details.put("snowflakeInitialized", true);
            details.put("workerId", instance.getWorkerId());
            details.put("datacenterId", instance.getDatacenterId());
            result.put("status", HealthStatus.UP.name());
        } catch (Exception e) {
            log.warn("Util health check failed: {}", e.getMessage());
            details.put("snowflakeInitialized", false);
            details.put("error", e.getMessage());
            result.put("status", HealthStatus.DOWN.name());
        }

        result.put("details", details);
        return result;
    }

    /**
     * 检查是否健康
     *
     * @return 健康返回 true，不健康返回 false
     */
    public boolean isHealthy() {
        return HealthStatus.UP.name().equals(checkHealth().get("status"));
    }
}
