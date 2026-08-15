package com.njydsz.userinfo.server.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.base.metrics.AbstractModuleMetrics;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;

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

    /**
     * 记录一次登录成功。
     *
     * <p>累加 {@code ydsz_userinfo_logins_total{result=success}} 计数器，并将在线会话
     * Gauge {@code ydsz_userinfo_online_sessions} +1。应在登录链路「鉴权通过且会话已建立」
     * 之后调用；与 {@link #recordLogout()} 配对使用。
     *
     * <p>线程安全：计数器与 {@code AtomicLong} 均为并发安全，并发调用不会丢计数。
     */
    public void recordLoginSuccess() {
        incrementCounter("logins_total", "result", "success");
        onlineSessions.incrementAndGet();
    }

    /**
     * 记录一次登录失败（鉴权不通过 / 账户锁定 / 风控拦截等）。
     *
     * <p>仅累加 {@code ydsz_userinfo_logins_total{result=fail}} 计数器；<b>不</b>改变在线
     * 会话 Gauge —— 失败意味着未建立会话，故不应与 {@link #recordLogout()} 配对。
     *
     * <p>线程安全：并发调用不丢计数。
     */
    public void recordLoginFail() {
        incrementCounter("logins_total", "result", "fail");
    }

    /**
     * 记录一次登出，将在在线会话 Gauge {@code ydsz_userinfo_online_sessions} -1。
     *
     * <p>应在登出成功路径调用，并与一次成功登录配对：若在未登录态（例如重复登出、越权探测）
     * 调用，{@code AtomicLong} 允许短暂负偏，会使在线会话数统计失真。计数器非单调下界保护，
     * 业务侧需保证「先成功登录、后登出」的配对语义。
     *
     * <p>线程安全：{@code AtomicLong#decrementAndGet()} 并发安全。
     */
    public void recordLogout() {
        onlineSessions.decrementAndGet();
    }

    /**
     * 开始一次认证耗时采样。
     *
     * <p>返回 {@link Timer.Sample} 句柄，需在认证逻辑结束处交给 {@link #stopTimer(Timer.Sample)}
     * 关闭，从而记录 {@code ydsz_userinfo_auth_duration_ms} 的 P50/P90/P99 分布。每次调用
     * 新建独立采样，线程安全、无共享状态；采样句柄不可跨线程复用后再 stop。
     *
     * @return 认证耗时采样句柄（非 null），须交由 {@link #stopTimer} 关闭
     */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /**
     * 结束一次认证耗时采样并记录到 {@code ydsz_userinfo_auth_duration_ms}。
     *
     * <p>由 {@link #startTimer()} 返回的 {@code sample} 必须来自同一次请求且<b>未</b>已被 stop，
     * 重复 stop 会触发 Micrometer 重复记录告警。采样本身线程安全，但同一个 {@code sample}
     * 不应被并发 stop。
     *
     * @param sample 来自 {@link #startTimer()} 的采样句柄，不可为 null（为 null 将抛 NPE）
     */
    public void stopTimer(Timer.Sample sample) {
        sample.stop(timer("auth_duration_ms"));
    }
}
