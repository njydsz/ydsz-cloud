package com.njydsz.pmis.common.feign.interceptor;

import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * Feign 响应拦截器
 *
 * <p>统一处理 Feign 客户端的响应，提供以下能力：
 * <ul>
 *   <li>响应日志记录（状态码、耗时、方法信息）</li>
 *   <li>响应指标采集（用于 Micrometer 监控）</li>
 *   <li>异常响应统一处理</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class FeignResponseInterceptor implements feign.ResponseInterceptor {

    private final FeignResponseMetrics metrics;
    private final boolean logEnabled;
    private final long slowCallThresholdMillis;

    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled) {
        this(metrics, logEnabled, 0);
    }

    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled, long slowCallThresholdMillis) {
        this.metrics = metrics;
        this.logEnabled = logEnabled;
        this.slowCallThresholdMillis = slowCallThresholdMillis;
    }

    @Override
    public Object intercept(feign.InvocationContext context, Chain chain) throws Exception {
        long startTime = System.currentTimeMillis();
        String serviceName = extractServiceName(context);
        String httpMethod = extractMethod(context);

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

        // P2: 慢调用检测 — 超过阈值时输出 WARN 日志
        if (slowCallThresholdMillis > 0 && duration >= slowCallThresholdMillis) {
            log.warn("[Feign] 慢调用告警 | service={} | method={} | status={} | duration={}ms | threshold={}ms",
                    serviceName, httpMethod, response != null ? response.status() : "N/A",
                    duration, slowCallThresholdMillis);
            if (metrics != null) {
                metrics.recordSlowCall(serviceName, httpMethod, duration, slowCallThresholdMillis);
            }
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

        // P2: 失败场景也检测慢调用
        if (slowCallThresholdMillis > 0 && duration >= slowCallThresholdMillis) {
            log.warn("[Feign] 慢调用告警 | service={} | method={} | duration={}ms | threshold={}ms | error={}",
                    serviceName, httpMethod, duration, slowCallThresholdMillis, e.getClass().getSimpleName());
            if (metrics != null) {
                metrics.recordSlowCall(serviceName, httpMethod, duration, slowCallThresholdMillis);
            }
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
     * 从 configKey 提取服务名称
     * configKey 格式为 "ServiceName#methodName(params)"
     */
    private String extractServiceName(feign.InvocationContext context) {
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
     */
    private String extractMethod(feign.InvocationContext context) {
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
