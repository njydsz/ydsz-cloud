package com.njydsz.common.app.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.auth.metrics.AuthMetrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * App 模块 Micrometer 指标采集
 *
 * <p>实现 {@link AuthMetrics} 接口，统一 App 端认证指标的采集契约。
 *
 * <p>采集 App 端签名验证和认证相关的指标，通过 Micrometer 暴露到 Prometheus，
 * 供 Grafana 监控 App 端安全态势和请求处理质量。
 *
 * <p><b>指标列表：</b>
 * <ul>
 *   <li>{@code app.signature.verify.total} - 签名验证总次数（tag: result）</li>
 *   <li>{@code app.signature.verify.duration} - 签名验证耗时分布（tag: result）</li>
 *   <li>{@code app.auth.total} - 认证总次数（tag: result, userType, reason）</li>
 *   <li>{@code app.auth.duration} - 认证耗时分布（tag: result, userType）</li>
 * </ul>
 *
 * <p><b>注意：</b>请求处理耗时由 Spring MVC 内置的 {@code http.server.requests} 指标覆盖，
 * 本类不再重复采集，避免 URI 标签基数爆炸问题。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AuthMetrics
 */
public class AppMetrics implements AuthMetrics {

    private static final Logger log = LoggerFactory.getLogger(AppMetrics.class);

    /** 默认降级值，避免 null/空串污染指标基数 */
    private static final String UNKNOWN = "unknown";

    /** App 端用户类型标识，作为 recordAuthSuccess/Failure 的默认 userType */
    private static final String USER_TYPE_APP = "app";

    /** Counter 缓存，避免每次调用重复创建 Builder 对象 */
    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    /** Timer 缓存，避免每次调用重复创建 Builder 对象 */
    private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;

    /**
     * 构造方法
     *
     * @param meterRegistry Micrometer MeterRegistry（可为 null，降级为无指标采集）
     */
    public AppMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            log.info("App 模块 Micrometer 指标采集已初始化");
        } else {
            log.info("App 模块指标采集降级（MeterRegistry 不可用）");
        }
    }

    // ==================== AuthMetrics 接口实现 ====================

    @Override
    public void recordAuthSuccess(String userType, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        String type = normalizeOrDefault(userType, USER_TYPE_APP);
        getCounter("app.auth.total", "result", "success", "userType", type).increment();
        getTimer("app.auth.duration", "result", "success", "userType", type)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordAuthFailure(String userType, String reason, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        String type = normalizeOrDefault(userType, USER_TYPE_APP);
        String r = normalize(reason);
        getCounter("app.auth.total", "result", "failure", "userType", type, "reason", r).increment();
        getTimer("app.auth.duration", "result", "failure", "userType", type)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordAuthSkip(String reason) {
        if (meterRegistry == null) {
            return;
        }
        getCounter("app.auth.total", "result", "skip", "reason", normalize(reason)).increment();
    }

    // ==================== App 特有指标：API 签名验证 ====================

    /**
     * 记录签名验证结果和耗时
     *
     * @param result        验证结果标签（success/missing_headers/invalid_timestamp/timestamp_expired/nonce_replay/no_secret/signature_mismatch）
     * @param durationNanos 验证耗时（纳秒）
     */
    public void recordSignatureVerify(String result, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        String r = normalize(result);
        getCounter("app.signature.verify.total", "result", r).increment();
        getTimer("app.signature.verify.duration", "result", r)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    // ==================== 工具方法 ====================

    /**
     * 规范化标签值，避免 null/空串污染指标基数。
     *
     * @param value 原始值
     * @return 规范化后的值
     */
    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? UNKNOWN : value;
    }

    /**
     * 规范化标签值，null/空串时返回默认值。
     *
     * @param value        原始值
     * @param defaultValue 默认值
     * @return 规范化后的值
     */
    private static String normalizeOrDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * 获取（或创建）带动态标签的 Counter，使用缓存避免重复创建 Builder。
     *
     * @param name 指标名
     * @param tags 标签键值对（交替排列：key1, val1, key2, val2, ...）
     * @return Counter 实例
     */
    private Counter getCounter(String name, String... tags) {
        String cacheKey = buildCacheKey(name, tags);
        return counterCache.computeIfAbsent(cacheKey, k -> Counter.builder(name).tags(tags).register(meterRegistry));
    }

    /**
     * 获取（或创建）带动态标签的 Timer，使用缓存避免重复创建 Builder。
     *
     * @param name 指标名
     * @param tags 标签键值对（交替排列：key1, val1, key2, val2, ...）
     * @return Timer 实例
     */
    private Timer getTimer(String name, String... tags) {
        String cacheKey = buildCacheKey(name, tags);
        return timerCache.computeIfAbsent(cacheKey, k -> Timer.builder(name).tags(tags).register(meterRegistry));
    }

    private static String buildCacheKey(String name, String... tags) {
        StringBuilder sb = new StringBuilder(name);
        for (int i = 0; i < tags.length; i++) {
            sb.append(':').append(tags[i]);
        }
        return sb.toString();
    }
}
