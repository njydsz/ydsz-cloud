package com.njydsz.userinfo.server.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.base.metrics.AbstractModuleMetrics;
import com.njydsz.common.redis.service.ops.RedisStringOps;

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
 *   <li>{@code ydsz_userinfo_online_sessions} — 在线会话数（Gauge，读自 Redis 计数器）</li>
 * </ul>
 *
 * <p><b>在线会话计数策略（P1-1）：</b>
 * <p>使用 Redis 原子计数器 {@code userinfo:session:total} 维护全局活跃会话总数，支持多实例
 * 部署场景下的准确统计。登录成功时 INCR，登出/驱逐时 DECR。Gauge 读取该计数器值，
 * 消除单节点 {@code AtomicLong} 无法跨实例聚合的问题。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class UserInfoMetrics extends AbstractModuleMetrics {

    /** Redis 在线会话总数计数器 Key */
    private static final String SESSION_TOTAL_KEY = "userinfo:session:total";

    private final RedisStringOps redisStringOps;

    public UserInfoMetrics(MeterRegistry meterRegistry, RedisStringOps redisStringOps) {
        super(meterRegistry, "ydsz_userinfo_");
        this.redisStringOps = redisStringOps;
        gauge("online_sessions", this::getOnlineSessionsFromRedis);
    }

    /**
     * 从 Redis 计数器读取当前在线会话总数。
     *
     * <p>读取 {@code userinfo:session:total} 计数器值，读取失败时返回 0（不影响监控链路）。
     *
     * @return 当前在线会话总数，Redis 不可用时返回 0
     */
    private double getOnlineSessionsFromRedis() {
        try {
            String value = redisStringOps.get(SESSION_TOTAL_KEY, String.class);
            if (value == null || value.isBlank()) {
                return 0.0;
            }
            return Double.parseDouble(value);
        } catch (Exception e) {
            log.warn("Failed to read online sessions from Redis, error={}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 记录一次登录成功。
     *
     * <p>累加 {@code ydsz_userinfo_logins_total{result=success}} 计数器，并将在线会话
     * Redis 计数器 {@code userinfo:session:total} INCR +1。应在登录链路「鉴权通过且会话已建立」
     * 之后调用；与 {@link #recordLogout()} 配对使用。
     */
    public void recordLoginSuccess() {
        incrementCounter("logins_total", "result", "success");
        try {
            redisStringOps.incr(SESSION_TOTAL_KEY, 1L);
        } catch (Exception e) {
            log.warn("Failed to increment online session counter, error={}", e.getMessage());
        }
    }

    /**
     * 记录一次登录失败（鉴权不通过 / 账户锁定 / 风控拦截等）。
     *
     * <p>仅累加 {@code ydsz_userinfo_logins_total{result=fail}} 计数器；<b>不</b>改变在线
     * 会话 Gauge —— 失败意味着未建立会话，故不应与 {@link #recordLogout()} 配对。
     */
    public void recordLoginFail() {
        incrementCounter("logins_total", "result", "fail");
    }

    /**
     * 记录一次登出，将在线会话 Redis 计数器 DECR -1。
     *
     * <p>应在登出成功路径调用，并与一次成功登录配对。
     */
    public void recordLogout() {
        try {
            redisStringOps.decr(SESSION_TOTAL_KEY, 1L);
        } catch (Exception e) {
            log.warn("Failed to decrement online session counter, error={}", e.getMessage());
        }
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
