package com.njydsz.common.app.metrics;

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
 *   <li>{@code app.request.duration} - 请求处理耗时分布（tag: uri）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AppMetrics {

    private static final Logger log = LoggerFactory.getLogger(AppMetrics.class);

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
     * @param result       验证结果标签（success/missing_headers/invalid_timestamp/timestamp_expired/nonce_replay/no_secret/signature_mismatch）
     * @param durationNanos 验证耗时（纳秒）
     */
    public void recordSignatureVerify(String result, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("app.signature.verify.total")
                .tag("result", result)
                .register(meterRegistry)
                .increment();

        Timer.builder("app.signature.verify.duration")
                .tag("result", result)
                .register(meterRegistry)
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
        Counter.builder("app.auth.total")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录请求处理耗时
     *
     * @param uri          请求 URI
     * @param durationNanos 处理耗时（纳秒）
     */
    public void recordRequestDuration(String uri, long durationNanos) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder("app.request.duration")
                .tag("uri", uri)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
