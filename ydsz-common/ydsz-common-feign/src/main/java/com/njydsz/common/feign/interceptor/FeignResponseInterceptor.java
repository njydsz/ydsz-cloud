package com.njydsz.common.feign.interceptor;

import org.jspecify.annotations.Nullable;

import com.njydsz.common.feign.circuitbreaker.FeignCircuitBreakerStrategy;
import com.njydsz.common.util.string.StringUtils;

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
 *   <li>Bulkhead 许可释放：在 finally 块中释放 {@link BulkheadRequestInterceptor} 获取的信号量许可</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class FeignResponseInterceptor implements ResponseInterceptor {

    private final FeignResponseMetrics metrics;
    private final boolean logEnabled;
    private final long slowCallThresholdMillis;
    private final FeignCircuitBreakerStrategy circuitBreaker;
    private final BulkheadRequestInterceptor bulkhead;

    /**
     * 使用指标和日志配置构造响应拦截器。
     *
     * @param metrics    Feign 响应指标采集器（可为 null）
     * @param logEnabled 是否启用响应日志记录
     */
    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled) {
        this(metrics, logEnabled, 0, null, null);
    }

    /**
     * 使用指标、日志和慢调用阈值构造响应拦截器。
     *
     * @param metrics                Feign 响应指标采集器（可为 null）
     * @param logEnabled             是否启用响应日志记录
     * @param slowCallThresholdMillis 慢调用阈值（毫秒），0 表示禁用
     */
    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled,
                                    long slowCallThresholdMillis) {
        this(metrics, logEnabled, slowCallThresholdMillis, null, null);
    }

    /**
     * 使用指标、日志、慢调用阈值和熔断器策略构造响应拦截器。
     *
     * @param metrics                Feign 响应指标采集器（可为 null）
     * @param logEnabled             是否启用响应日志记录
     * @param slowCallThresholdMillis 慢调用阈值（毫秒），0 表示禁用
     * @param circuitBreaker         熔断器策略（可为 null）
     */
    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled,
                                    long slowCallThresholdMillis,
                                    @Nullable FeignCircuitBreakerStrategy circuitBreaker) {
        this(metrics, logEnabled, slowCallThresholdMillis, circuitBreaker, null);
    }

    /**
     * 使用完整参数构造响应拦截器。
     *
     * @param metrics                Feign 响应指标采集器（可为 null）
     * @param logEnabled             是否启用响应日志记录
     * @param slowCallThresholdMillis 慢调用阈值（毫秒），0 表示禁用
     * @param circuitBreaker         熔断器策略（可为 null）
     * @param bulkhead               Bulkhead 请求隔离拦截器（可为 null，启用后用于在 finally 中释放许可）
     */
    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled,
                                    long slowCallThresholdMillis,
                                    @Nullable FeignCircuitBreakerStrategy circuitBreaker,
                                    @Nullable BulkheadRequestInterceptor bulkhead) {
        this.metrics = metrics;
        this.logEnabled = logEnabled;
        this.slowCallThresholdMillis = slowCallThresholdMillis;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
    }

    /**
     * 拦截 Feign 调用，执行熔断检查、响应记录和许可释放。
     *
     * <p>执行流程：
     * <ol>
     *   <li>调用前检查熔断器是否允许请求</li>
     *   <li>执行实际 Feign 调用</li>
     *   <li>调用后记录成功/失败指标和慢调用告警</li>
     *   <li>finally 块中释放 Bulkhead 信号量许可</li>
     * </ol>
     *
     * @param context Feign 调用上下文
     * @param chain   调用链
     * @return 调用结果
     * @throws Exception 调用过程中可能抛出的异常
     */
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
        } finally {
            // 释放 Bulkhead 许可：若 BulkheadRequestInterceptor.apply 已获取许可（写入 ThreadLocal），
            // 此处释放；若未获取（apply 抛异常）或未启用 Bulkhead，此方法为空操作
            if (bulkhead != null) {
                try {
                    bulkhead.releaseCurrentPermit();
                } catch (Exception releaseEx) {
                    log.warn("[Feign] Bulkhead 许可释放失败 | service={}", serviceName, releaseEx);
                }
            }
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
            // 记录响应体大小
            metrics.recordResponseBodySize(serviceName, httpMethod, response.status(), resolveBodySize(response));
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
                String servicePart = configKey.substring(0, hashIdx);
                if (StringUtils.isNotEmpty(servicePart)) {
                    return servicePart;
                }
            }
            int atIdx = configKey.indexOf('@');
            if (atIdx > 0) {
                String servicePart = configKey.substring(0, atIdx);
                if (StringUtils.isNotEmpty(servicePart)) {
                    return servicePart;
                }
            }
            if (StringUtils.isNotEmpty(configKey)) {
                return configKey;
            }
            return "unknown";
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
            if (hashIdx > 0 && hashIdx < configKey.length() - 1) {
                int parenIdx = configKey.indexOf('(', hashIdx);
                if (parenIdx > hashIdx) {
                    String methodPart = configKey.substring(hashIdx + 1, parenIdx);
                    if (StringUtils.isNotEmpty(methodPart)) {
                        return methodPart;
                    }
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

        /**
         * 记录响应体大小（P3 可观测性增强）。
         *
         * <p>用于监控响应体分布，识别异常大响应或空响应。
         * 默认实现为空操作，实现类可选择性覆盖。
         *
         * @param service     服务名称
         * @param method      HTTP 方法
         * @param status      HTTP 状态码
         * @param bodySizeBytes 响应体大小（字节），若未知传 -1
         */
        default void recordResponseBodySize(String service, String method, int status, long bodySizeBytes) {
            // 默认空操作，避免破坏现有实现
        }
    }
}
