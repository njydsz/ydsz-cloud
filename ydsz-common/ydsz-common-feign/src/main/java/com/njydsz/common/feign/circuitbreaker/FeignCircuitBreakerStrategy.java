package com.njydsz.common.feign.circuitbreaker;

/**
 * Feign 熔断器策略接口。
 *
 * <p>封装 Resilience4j 熔断器与 Feign 调用的集成点，提供请求许可判断和结果反馈。
 * 实现类由 {@link Resilience4jFeignConfiguration} 注册（需启用
 * {@code ydsz.feign.circuit-breaker.enabled=true}）。
 *
 * <p>当未注册实现时，{@code FeignResponseInterceptor} 跳过熔断逻辑（降级为无保护模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see Resilience4jFeignConfiguration
 */
public interface FeignCircuitBreakerStrategy {

    /**
     * 判断指定服务的熔断器是否允许当前请求通过。
     *
     * @param serviceName Feign 服务名称（来自 @FeignClient name）
     * @return true=允许通过；false=熔断器开启，应快速失败
     */
    boolean allowRequest(String serviceName);

    /**
     * 记录一次成功的调用。
     *
     * @param serviceName 服务名称
     * @param durationMs  调用耗时（毫秒）
     */
    void recordSuccess(String serviceName, long durationMs);

    /**
     * 记录一次失败的调用。
     *
     * @param serviceName 服务名称
     * @param durationMs  调用耗时（毫秒）
     * @param throwable   异常对象
     */
    void recordFailure(String serviceName, long durationMs, Throwable throwable);
}
