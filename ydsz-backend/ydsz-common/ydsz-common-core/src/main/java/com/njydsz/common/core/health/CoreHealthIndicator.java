package com.njydsz.common.core.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.core.config.CoreProperties;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.core.trace.TraceIdSupplier;

/**
 * Core 模块健康指标
 *
 * <p>向 Spring Boot Actuator 暴露核心模块的运行状态信息，包括：
 * <ul>
 *   <li>TraceId 生成策略（UUID / Snowflake）</li>
 *   <li>分页配置（maxPageSize / defaultPageSize）</li>
 * </ul>
 *
 * <p>访问 {@code /actuator/health} 时，响应的 details 中会包含 {@code core} 节点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CoreHealthIndicator implements HealthIndicator {

    private final CoreProperties properties;

    /**
     * 创建 CoreHealthIndicator 实例
     *
     * @param properties 核心配置属性
     */
    public CoreHealthIndicator(CoreProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();

        // TraceId 策略
        TraceIdSupplier supplier = TraceIdGenerator.getSupplier();
        String traceIdStrategy = supplier != null ? supplier.getClass().getSimpleName() : "unknown";
        details.put("traceIdStrategy", traceIdStrategy);
        details.put("traceEnabled", properties.getTrace().isEnabled());

        // 分页配置
        details.put("maxPageSize", properties.getMaxPageSize());
        details.put("defaultPageSize", properties.getDefaultPageSize());

        return Health.up().withDetails(details).build();
    }
}
