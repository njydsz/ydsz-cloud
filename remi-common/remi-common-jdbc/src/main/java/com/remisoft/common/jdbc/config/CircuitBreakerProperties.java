package com.remisoft.common.jdbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 数据库操作熔断器配置属性
 *
 * <p>配置示例：
 * <pre>
 * remi:
 *   jdbc:
 *     circuit-breaker:
 *       enabled: true
 *       failure-threshold: 10
 *       open-duration-millis: 30000
 *       half-open-probe-size: 3
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "remi.jdbc.circuit-breaker")
public class CircuitBreakerProperties {

    /**
     * 是否启用数据库熔断器（默认 false）
     */
    private boolean enabled = false;

    /**
     * 连续失败次数阈值，超过后触发熔断（默认 10）
     */
    private int failureThreshold = 10;

    /**
     * 熔断持续时间（毫秒），超过后进入半开状态（默认 30000）
     */
    private long openDurationMillis = 30000L;

    /**
     * 半开状态探测请求数（默认 3）
     */
    private int halfOpenProbeSize = 3;
}
