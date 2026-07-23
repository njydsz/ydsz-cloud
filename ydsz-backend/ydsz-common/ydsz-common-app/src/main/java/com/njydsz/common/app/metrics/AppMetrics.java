package com.njydsz.common.app.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * App 模块 Micrometer 指标采集
 *
 * <p>采集 App 端签名验证和认证相关的指标，通过 Micrometer 暴露到 Prometheus，
 * 供 Grafana 监控 App 端安全态势和请求处理质量。
 *
 * <p><b>指标列表：</b>
 * <ul>
 *   <li>{@code app.signature.verify.total} - 签名验证总次数（tag: result）</li>
 *   <li>{@code app.signature.verify.duration} - 签名验证耗时分布（tag: result）</li>
 *   <li>{@code app.auth.total} - 认证总次数（tag: result）</li>
 * </ul>
 *
 * <p><b>注意：</b>请求处理耗时由 Spring MVC 内置的 {@code http.server.requests} 指标覆盖，
 * 本类不再重复采集，避免 URI 标签基数爆炸问题。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AppMetrics {

    private static final Logger log = LoggerFactory.getLogger(AppMetrics.class);

    /** Counter 缓存，避免每次调用重复创建 Builder 对象 */
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    /** Timer 缓存，避免每次调用重复创建 Builder 对象 */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

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
        counterCache.computeIfAbsent(
                metricKey("app.signature.verify.total", "result", result),
                k -> Counter.builder("app.signature.verify.total")
                        .tag("result", result)
                        .register(meterRegistry))
                .increment();

        timerCache.computeIfAbsent(
                metricKey("app.signature.verify.duration", "result", result),
                k -> Timer.builder("app.signature.verify.duration")
                        .tag("result", result)
                        .register(meterRegistry))
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录认证结果
     *
     * @param result 认证结果标签（success/fail/skip）
     */
    public void recordAuth(String result) {
        if (meterRegistry == null) {
            return;
        }
        counterCache.computeIfAbsent(
                metricKey("app.auth.total", "result", result),
                k -> Counter.builder("app.auth.total")
                        .tag("result", result)
                        .register(meterRegistry))
                .increment();
    }

    /**
     * 构建 Meter 缓存键
     *
     * @param name Meter 名称
     * @param tags 标签键值对（交替排列：key1, val1, key2, val2, ...）
     * @return 复合缓存键字符串
     */
    private static String metricKey(String name, String... tags) {
        StringBuilder sb = new StringBuilder(name);
        for (int i = 0; i < tags.length; i += 2) {
            sb.append(':').append(tags[i]).append('=').append(tags[i + 1]);
        }
        return sb.toString();
    }
}
