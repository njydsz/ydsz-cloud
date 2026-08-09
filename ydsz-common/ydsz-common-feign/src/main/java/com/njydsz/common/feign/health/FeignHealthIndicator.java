package com.njydsz.common.feign.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.feign.circuitbreaker.FeignCircuitBreakerStrategy;
import com.njydsz.common.feign.config.FeignProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Feign 模块健康检查指示器
 *
 * <p>报告 Feign 熔断器状态，暴露 /actuator/health/feign 端点。
 *
 * <p><b>检测逻辑：</b>
 * <ul>
 *   <li>检查熔断器是否已初始化</li>
 *   <li>报告当前熔断器策略与状态</li>
 *   <li>返回 Feign 客户端配置概要</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeignHealthIndicator implements HealthIndicator {

    private final FeignProperties feignProperties;

    private final FeignCircuitBreakerStrategy circuitBreakerStrategy;

    /**
     * 构造 Feign 健康检查指示器。
     *
     * @param feignProperties              Feign 配置属性
     * @param circuitBreakerStrategyProvider 熔断器策略提供者（可选）
     */
    public FeignHealthIndicator(FeignProperties feignProperties,
                                ObjectProvider<FeignCircuitBreakerStrategy> circuitBreakerStrategyProvider) {
        this.feignProperties = feignProperties;
        this.circuitBreakerStrategy = circuitBreakerStrategyProvider.getIfAvailable();
    }

    /**
     * 执行健康检查，返回 Feign 模块状态。
     *
     * <p>检查内容包括：熔断器启用状态、重试策略、超时配置、熔断器策略等。
     *
     * @return 健康检查结果
     */
    @Override
    public Health health() {
        try {
            Health.Builder builder = Health.up()
                    .withDetail("module", "feign")
                    .withDetail("circuitBreakerEnabled", feignProperties.getCircuitBreaker().isEnabled())
                    .withDetail("retryEnabled", feignProperties.getRetry().isEnabled())
                    .withDetail("connectTimeoutMs", feignProperties.getTimeout().getConnect())
                    .withDetail("readTimeoutMs", feignProperties.getTimeout().getRead());

            if (circuitBreakerStrategy != null) {
                builder.withDetail("circuitBreakerStrategy", circuitBreakerStrategy.getName());
            } else {
                builder.withDetail("circuitBreakerStrategy", "not initialized");
            }

            return builder.build();
        } catch (Exception e) {
            log.error("【Feign模块】健康检查失败 | error={}", e.getMessage());
            return Health.down()
                    .withDetail("module", "feign")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
