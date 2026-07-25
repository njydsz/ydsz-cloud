package com.njydsz.userinfo.server.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import lombok.extern.slf4j.Slf4j;

/**
 * Userinfo module Micrometer metrics.
 *
 * <p>Exposes login success/failure counters, auth duration timer.
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
        this.loginSuccessCounter = Counter.builder("userinfo.login.success")
                .description("Login success count")
                .register(meterRegistry);
        this.loginFailCounter = Counter.builder("userinfo.login.fail")
                .description("Login failure count")
                .register(meterRegistry);
        this.authDurationTimer = Timer.builder("userinfo.auth.duration")
                .description("Authentication duration")
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
