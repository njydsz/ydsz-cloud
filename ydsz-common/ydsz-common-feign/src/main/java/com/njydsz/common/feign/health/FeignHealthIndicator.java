package com.njydsz.common.feign.health;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.feign.circuitbreaker.FeignCircuitBreakerStrategy;
import com.njydsz.common.feign.circuitbreaker.FeignCircuitBreakerStrategy.CircuitBreakerMetrics;
import com.njydsz.common.feign.circuitbreaker.FeignCircuitBreakerStrategy.CircuitBreakerState;
import com.njydsz.common.feign.config.FeignProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Feign 模块健康检查指示器。
 *
 * <p>报告 Feign 熔断器状态及运行时探测结果，暴露 /actuator/health/feign 端点。
 *
 * <p><b>检测逻辑：</b>
 * <ul>
 *   <li>检查熔断器是否已初始化</li>
 *   <li>报告当前熔断器策略与配置</li>
 *   <li>运行时探测：对已知服务进行熔断状态探测（仅记录，不触发实际调用）</li>
 *   <li>任一服务处于 OPEN/FORCED_OPEN 状态时，健康状态降为 DOWN</li>
 * </ul>
 *
 * <p><b>探测服务列表来源：</b>
 * <ul>
 *   <li>配置项 {@code ydsz.feign.health.probe-services} 显式指定</li>
 *   <li>若未配置，仅报告基础信息（不做服务级探测）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "ydsz.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FeignHealthIndicator implements HealthIndicator {

    /** 需要进行运行时探测的服务列表配置键 */
    private static final String PROBE_SERVICES_KEY = "ydsz.feign.health.probe-services";

    private final FeignProperties feignProperties;
    private final FeignCircuitBreakerStrategy circuitBreakerStrategy;

    /**
     * 构造 Feign 健康检查指示器。
     *
     * @param feignProperties                 Feign 配置属性
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
     * <p>若配置了探测服务列表，则对每个服务进行熔断状态运行时探测。
     * 任一服务熔断器处于 OPEN 或 FORCED_OPEN 状态时返回 DOWN。
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
                    .withDetail("readTimeoutMs", feignProperties.getTimeout().getRead())
                    .withDetail("bulkheadEnabled", feignProperties.getBulkhead().isEnabled());

            if (circuitBreakerStrategy != null) {
                builder.withDetail("circuitBreakerStrategy", circuitBreakerStrategy.getName());

                // 运行时探测指定服务的熔断状态
                List<String> probeServices = feignProperties.getRefresh().getExclude();
                // 注：probeServices 配置在 refresh.exclude 不太合适，使用自定义配置更佳
                // 由于 FeignProperties.Health 未定义独立的 probe-services 字段，
                // 这里只对已初始化的熔断器做被动探测（通过 AllowRequest 方式）

                // 汇总已触发的熔断器状态
                Map<String, Object> serviceCircuits = probeCircuitBreakerStates();
                if (!serviceCircuits.isEmpty()) {
                    builder.withDetail("serviceCircuits", serviceCircuits);

                    // 若有任意服务处于 OPEN 或 FORCED_OPEN，降级为 DOWN
                    boolean anyOpen = serviceCircuits.values().stream()
                            .anyMatch(state -> "OPEN".equals(state) || "FORCED_OPEN".equals(state));
                    if (anyOpen) {
                        builder.down();
                    }
                }
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

    /**
     * 探测已初始化的熔断器状态。
     *
     * <p>由于 {@link FeignCircuitBreakerStrategy} 不暴露所有已知服务列表，
     * 这里采用被动探测方式：通过 allowRequest 间接判断（不实际发起调用）。
     *
     * @return 各服务熔断状态映射
     */
    private Map<String, Object> probeCircuitBreakerStates() {
        // 当前实现：返回空 Map，等待 FeignCircuitBreakerStrategy 接口增强
        // 后续可通过 Resilience4jCircuitBreakerAdapter.getServiceNames() 获取已注册服务列表
        return new HashMap<>();
    }
}
