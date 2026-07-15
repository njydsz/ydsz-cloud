package com.njydsz.pmis.common.feign.interceptor;

import org.jspecify.annotations.Nullable;

import com.njydsz.pmis.common.feign.circuitbreaker.FeignCircuitBreakerStrategy;

import feign.InvocationContext;
import feign.Response;
import feign.ResponseInterceptor;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign 响应拦截器
 *
 * <p>统一处理 Feign 客户端的响应，提供以下能力：
 * <ul>
 *   <li>熔断器集成：调用前检查 allowRequest，调用后记录 success/failure</li>
 *   <li>响应日志记录（状态码、耗时、方法信息）</li>
 *   <li>响应指标采集（用于 Micrometer 监控）</li>
 *   <li>慢调用检测与告警</li>
 *   <li>异常响应统一处理</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class FeignResponseInterceptor implements ResponseInterceptor {

    private final FeignResponseMetrics metrics;
    private final boolean logEnabled;
    private final long slowCallThresholdMillis;
    private final FeignCircuitBreakerStrategy circuitBreaker;

    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled) {
        this(metrics, logEnabled, 0, null);
    }

    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled,
                                    long slowCallThresholdMillis) {
        this(metrics, logEnabled, slowCallThresholdMillis, null);
    }

    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled,
                                    long slowCallThresholdMillis,
                                    @Nullable FeignCircuitBreakerStrategy circuitBreaker) {
        this.metrics = metrics;
        this.logEnabled = logEnabled;
        this.slowCallThresholdMillis = slowCallThresholdMillis;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Object intercept(InvocationContext context, Chain chain) throws Exception {
        long startTime = System.currentTimeMillis();
        String serviceName = extractServiceName(context);
        String httpMethod = extractMethod(context);

        if (circuitBreaker != null && !circuitBreaker.allowRequest(serviceName)) {
            log.warn("[Feign] 熔断器拒绝请求 | service={} | method={}", serviceName, httpMethod);
            throw new RuntimeException("Circuit breaker is open for service: " + serviceName);
        }

        try {
            Object result = context.proceed();
            Response response = context.response();
            long duration = System.currentTimeMillis() - startTime;
            recordSuccess(serviceName, httpMethod, response, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Response response = context.response();
            recordFailure(serviceName, httpMethod, response, duration, e);
            throw e;
        }
    }

    /**
     * 记录成功响应
     */
    private void recordSuccess(String serviceName, String httpMethod, Response response, long duration) {
        if (logEnabled && response != null) {
            log.info("[Feign] 响应成功 | service={} | method={} | status={} | duration={}ms",
                    serviceName, httpMethod, response.status(), duration);
        }

        if (slowCallThresholdMillis > 0 && duration >= slowCallThresholdMillis) {
            log.warn("[Feign] 慢调用告警 | service={} | method={} | status={} | duration={}ms | threshold={}ms",
                    serviceName, httpMethod, response != null ? response.status() : "N/A",
                    duration, slowCallThresholdMillis);
            if (metrics != null) {
                metrics.recordSlowCall(serviceName, httpMethod, duration, slowCallThresholdMillis);
            }
        }

        if (circuitBreaker != null) {
            circuitBreaker.recordSuccess(serviceName, duration);
        }

        if (metrics != null && response != null) {
            metrics.recordSuccess(
                    serviceName,
                    httpMethod,
                    response.status(),
                    duration
            );
        }
    }

    /**
     * 记录失败响应
     */
    private void recordFailure(String serviceName, String httpMethod, Response response, long duration, Exception e) {
        log.warn("[Feign] 响应失败 | service={} | method={} | status={} | duration={}ms | error={}",
                serviceName, httpMethod,
                response != null ? response.status() : "N/A",
                duration,
                e.getMessage());

        if (slowCallThresholdMillis > 0 && duration >= slowCallThresholdMillis) {
            log.warn("[Feign] 慢调用告警 | service={} | method={} | duration={}ms | threshold={}ms | error={}",
                    serviceName, httpMethod, duration, slowCallThresholdMillis, e.getClass().getSimpleName());
            if (metrics != null) {
                metrics.recordSlowCall(serviceName, httpMethod, duration, slowCallThresholdMillis);
            }
        }

        if (circuitBreaker != null) {
            circuitBreaker.recordFailure(serviceName, duration, e);
        }

        if (metrics != null) {
            metrics.recordFailure(
                    serviceName,
                    httpMethod,
                    response != null ? response.status() : 0,
                    duration,
                    e.getClass().getSimpleName()
            );
        }
    }

    /**
     * 从 InvocationContext 提取服务名称。
     *
     * <p>优先通过 FeignClient 注解的 contextId/name 提取，
     * 兜底通过 method 所在接口类名提取。
     *
     * @param context Feign 调用上下文
     * @return 服务名称
     */
    private String extractServiceName(InvocationContext context) {
        try {
            String configKey = context.toString();
            int hashIdx = configKey.indexOf('#');
            if (hashIdx > 0) {
                return configKey.substring(0, hashIdx);
            }
            return configKey;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 提取 HTTP 方法
     *
     * @param context Feign 调用上下文
     * @return HTTP 方法名称
     */
    private String extractMethod(InvocationContext context) {
        try {
            String configKey = context.toString();
            int hashIdx = configKey.indexOf('#');
            if (hashIdx > 0) {
                int parenIdx = configKey.indexOf('(', hashIdx);
                if (parenIdx > hashIdx) {
                    return configKey.substring(hashIdx + 1, parenIdx);
                }
            }
            return "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * Feign 响应指标接口
     *
     * <p>用于集成 Micrometer 或其他监控系统
     */
    public interface FeignResponseMetrics {
        /**
         * 记录成功响应
         *
         * @param service  服务名称
         * @param method   HTTP 方法
         * @param status   HTTP 状态码
         * @param duration 耗时（毫秒）
         */
        void recordSuccess(String service, String method, int status, long duration);

        /**
         * 记录失败响应
         *
         * @param service   服务名称
         * @param method    HTTP 方法
         * @param status    HTTP 状态码
         * @param duration  耗时（毫秒）
         * @param errorType 错误类型
         */
        void recordFailure(String service, String method, int status, long duration, String errorType);

        /**
         * 记录慢调用（P2 可观测性增强）
         *
         * @param service    服务名称
         * @param method     HTTP 方法
         * @param duration   耗时（毫秒）
         * @param threshold  慢调用阈值（毫秒）
         */
        void recordSlowCall(String service, String method, long duration, long threshold);
    }
}
