package com.njydsz.userinfo.server.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.metrics.AbstractModuleMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Userinfo module Micrometer metrics.
 *
 * <p>P0-2 架构优化：继承 {@link AbstractModuleMetrics}，统一指标前缀 {@code ydsz_userinfo_}，
 * 消除手动 Counter/Timer/Gauge 创建样板代码。
 *
 * <p>Exposes login counters (with result tag), auth duration timer, and online session gauge.
 * Integrated into AuthServiceImpl login chain.
 *
 * <p>Metric naming follows Micrometer convention (dots converted to underscores by Prometheus):
 * <ul>
 *   <li>{@code ydsz_userinfo_logins_total{result=success|fail}} — 登录成功/失败计数</li>
 *   <li>{@code ydsz_userinfo_auth_duration_ms} — 认证耗时分布（P50/P90/P99）</li>
 *   <li>{@code ydsz_userinfo_online_sessions} — 在线会话数（Gauge）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class UserInfoMetrics extends AbstractModuleMetrics {

    private final AtomicLong onlineSessions = new AtomicLong(0);

    public UserInfoMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "ydsz_userinfo_");
        gaugeRef("online_sessions", onlineSessions, AtomicLong::doubleValue);
    }

    public void recordLoginSuccess() {
        incrementCounter("logins_total", "result", "success");
        onlineSessions.incrementAndGet();
    }

    public void recordLoginFail() {
        incrementCounter("logins_total", "result", "fail");
    }

    public void recordLogout() {
        onlineSessions.decrementAndGet();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(timer("auth_duration_ms"));
    }
}
