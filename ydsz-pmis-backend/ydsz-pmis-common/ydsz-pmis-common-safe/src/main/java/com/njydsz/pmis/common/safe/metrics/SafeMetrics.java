package com.njydsz.pmis.common.safe.metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.safe.alert.SecurityEvent;
import com.njydsz.pmis.common.safe.alert.SecurityEventType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 安全模块 Micrometer 指标采集
 *
 * <p>采集安全相关指标，通过 Micrometer 暴露到 Prometheus，供 Grafana 监控安全态势。
 *
 * <p><b>指标列表：</b>
 * <ul>
 *   <li>{@code safe_xss_attacks_total} - XSS 攻击次数</li>
 *   <li>{@code safe_sql_injection_total} - SQL 注入次数</li>
 *   <li>{@code safe_csrf_failures_total} - CSRF 验证失败次数</li>
 *   <li>{@code safe_rate_limit_triggered_total} - 限流触发次数</li>
 *   <li>{@code safe_illegal_access_total} - 非法访问次数</li>
 *   <li>{@code safe_ip_blocked_total} - IP 封禁次数</li>
 *   <li>{@code safe_filter_duration_seconds} - 安全过滤器处理耗时</li>
 * </ul>
 *
 * @since 1.3.0
 */
public class SafeMetrics {

    private static final Logger log = LoggerFactory.getLogger(SafeMetrics.class);

    private final MeterRegistry meterRegistry;

    private final AtomicLong xssAttacks = new AtomicLong(0);
    private final AtomicLong sqlInjections = new AtomicLong(0);
    private final AtomicLong csrfFailures = new AtomicLong(0);
    private final AtomicLong rateLimitTriggered = new AtomicLong(0);
    private final AtomicLong illegalAccess = new AtomicLong(0);
    private final AtomicLong ipBlocked = new AtomicLong(0);

    /**
     * @param meterRegistry Micrometer MeterRegistry（可为 null，降级为内存计数）
     */
    public SafeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            log.info("安全模块 Micrometer 指标采集已初始化");
        } else {
            log.info("安全模块指标采集降级为内存计数（MeterRegistry 不可用）");
        }
    }

    /**
     * 记录安全事件
     *
     * @param event 安全事件
     */
    public void recordSecurityEvent(SecurityEvent event) {
        if (event == null) {
            return;
        }

        SecurityEventType type = event.getEventType();
        String sourceIp = event.getSourceIp() != null ? event.getSourceIp() : "unknown";

        switch (type) {
            case XSS_ATTACK -> incrementCounter("safe_xss_attacks_total", sourceIp, xssAttacks);
            case SQL_INJECTION -> incrementCounter("safe_sql_injection_total", sourceIp, sqlInjections);
            case CSRF_ATTACK -> incrementCounter("safe_csrf_failures_total", sourceIp, csrfFailures);
            case RATE_LIMIT_TRIGGERED -> incrementCounter("safe_rate_limit_triggered_total", sourceIp, rateLimitTriggered);
            case ILLEGAL_ACCESS -> incrementCounter("safe_illegal_access_total", sourceIp, illegalAccess);
            case IP_AUTO_BLOCKED -> incrementCounter("safe_ip_blocked_total", sourceIp, ipBlocked);
            default -> incrementCounter("safe_security_events_total", sourceIp, new AtomicLong(0));
        }
    }

    /**
     * 记录过滤器处理耗时
     *
     * @param filterName 过滤器名称
     * @param durationNanos 处理耗时（纳秒）
     */
    public void recordFilterDuration(String filterName, long durationNanos) {
        if (meterRegistry != null) {
            Timer.builder("safe_filter_duration_seconds")
                    .tag("filter", filterName)
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 获取累计 XSS 攻击次数
     *
     * @return 累计次数
     */
    public long getXssAttacksCount() {
        return xssAttacks.get();
    }

    /**
     * 获取累计 SQL 注入次数
     *
     * @return 累计次数
     */
    public long getSqlInjectionCount() {
        return sqlInjections.get();
    }

    /**
     * 获取累计 CSRF 失败次数
     *
     * @return 累计次数
     */
    public long getCsrfFailuresCount() {
        return csrfFailures.get();
    }

    /**
     * 获取累计限流触发次数
     *
     * @return 累计次数
     */
    public long getRateLimitTriggeredCount() {
        return rateLimitTriggered.get();
    }

    private void incrementCounter(String name, String sourceIp, AtomicLong fallback) {
        fallback.incrementAndGet();
        if (meterRegistry != null) {
            Counter.builder(name)
                    .tag("source_ip", sourceIp)
                    .register(meterRegistry)
                    .increment();
        }
    }
}
