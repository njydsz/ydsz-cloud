package com.njydsz.common.feign.health;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
 * <p>报告 Feign 熔断器配置状态及各服务运行时熔断探测结果，暴露 /actuator/health/feign 端点。
 *
 * <p><b>检测逻辑：</b>
 * <ul>
 *   <li>检查熔断器是否已初始化</li>
 *   <li>报告当前熔断器策略与全局配置</li>
 *   <li>运行时探测：遍历所有已注册服务的熔断器状态（{@link FeignCircuitBreakerStrategy#getServiceNames()}）</li>
 *   <li>任一服务处于 OPEN/FORCED_OPEN 状态时，健康状态降为 DOWN</li>
 *   <li>半开状态（HALF_OPEN）视为"降级但可用"，标记为 UNKNOWN</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
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
     * <p>运行时探测各已注册服务的熔断器状态：
     * <ul>
     *   <li>所有服务 CLOSED → UP</li>
     *   <li>有服务 OPEN/FORCED_OPEN → DOWN</li>
     *   <li>有服务 HALF_OPEN（无 OPEN） → UNKNOWN</li>
     *   <li>熔断器未初始化（circuitBreakerStrategy=null）→ UP（仅报告基础信息）</li>
     * </ul>
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

                // 运行时探测：获取所有已注册服务并检查熔断状态
                Set<String> serviceNames = circuitBreakerStrategy.getServiceNames();
                if (serviceNames != null && !serviceNames.isEmpty()) {
                    boolean hasOpen = false;
                    boolean hasHalfOpen = false;
                    Map<String, Object> circuitDetails = new LinkedHashMap<>();

                    for (String serviceName : serviceNames) {
                        CircuitBreakerState state = circuitBreakerStrategy.getState(serviceName);
                        circuitDetails.put(serviceName, state.name());

                        if (state == CircuitBreakerState.OPEN || state == CircuitBreakerState.FORCED_OPEN) {
                            hasOpen = true;
                        } else if (state == CircuitBreakerState.HALF_OPEN) {
                            hasHalfOpen = true;
                        }
                    }

                    builder.withDetail("circuitBreakerStates", circuitDetails);

                    if (hasOpen) {
                        builder.down();
                        log.warn("[FeignHealth] 检测到服务熔断器开启，健康状态降级为 DOWN，服务数={}", serviceNames.size());
                    } else if (hasHalfOpen) {
                        // 半开状态：有服务正在恢复中，标记为 UNKNOWN
                        builder.status("UNKNOWN");
                        log.info("[FeignHealth] 检测到服务半开状态（恢复中），健康状态标记为 UNKNOWN，服务数={}", serviceNames.size());
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
     * 获取指定服务的熔断指标详情（供监控扩展使用）。
     *
     * @param serviceName 服务名称
     * @return 熔断指标映射，若策略不可用返回空 Map
     */
    public Map<String, Object> getServiceMetricsDetail(String serviceName) {
        Map<String, Object> detail = new HashMap<>();
        if (circuitBreakerStrategy == null) {
            return detail;
        }

        CircuitBreakerMetrics metrics = circuitBreakerStrategy.getMetrics(serviceName);
        if (metrics != null) {
            detail.put("totalCalls", metrics.getTotalCalls());
            detail.put("successfulCalls", metrics.getSuccessfulCalls());
            detail.put("failedCalls", metrics.getFailedCalls());
            detail.put("slowCalls", metrics.getSlowCalls());
            detail.put("failureRate", String.format("%.2f%%", metrics.getFailureRate()));
            detail.put("slowCallRate", String.format("%.2f%%", metrics.getSlowCallRate()));
            detail.put("averageDurationMs", metrics.getAverageDuration());
            detail.put("maxDurationMs", metrics.getMaxDuration());
        }
        detail.put("state", circuitBreakerStrategy.getState(serviceName).name());
        return detail;
    }
}
