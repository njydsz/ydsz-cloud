package com.njydsz.userinfo.server.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import lombok.extern.slf4j.Slf4j;

/**
 * Userinfo module Micrometer metrics.
 *
 * <p>Exposes login counters (with result tag) and auth duration timer.
 * Integrated into AuthServiceImpl login chain.
 *
 * <p>Metric naming follows Micrometer convention:
 * <ul>
 *   <li>{@code userinfo.logins.total} with tag {@code result=success|fail}</li>
 *   <li>{@code userinfo.auth.duration} (P50/P90/P99)</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class UserInfoMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter loginSuccessCounter;
    private final Counter loginFailCounter;
    private final Timer authDurationTimer;

    public UserInfoMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.loginSuccessCounter = Counter.builder("userinfo.logins.total")
                .tag("result", "success")
                .description("Total login attempts")
                .register(meterRegistry);
        this.loginFailCounter = Counter.builder("userinfo.logins.total")
                .tag("result", "fail")
                .description("Total login attempts")
                .register(meterRegistry);
        this.authDurationTimer = Timer.builder("userinfo.auth.duration")
                .description("Authentication duration")
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(meterRegistry);
    }

    public void recordLoginSuccess() {
        loginSuccessCounter.increment();
    }

    public void recordLoginFail() {
        loginFailCounter.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(authDurationTimer);
    }
}
