package com.njydsz.pmis.common.auth.metrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * 认证授权模块 Micrometer 指标采集器。
 *
 * <p>采集以下指标：
 * <ul>
 *   <li>{@code auth.permission.check.time} - 权限校验耗时 Timer</li>
 *   <li>{@code auth.permission.deny.count} - 权限拒绝次数 Counter（按 type 标签区分）</li>
 *   <li>{@code auth.permission.allow.count} - 权限通过次数 Counter</li>
 *   <li>{@code auth.cache.hit} - 缓存命中次数 Counter</li>
 *   <li>{@code auth.cache.miss} - 缓存未命中次数 Counter</li>
 *   <li>{@code auth.redis.available} - Redis 可用状态 Gauge</li>
 * </ul>
 *
 * <p>同时负责权限拒绝事件的安全审计日志记录。
 *
 * @since 1.1.0

 */
@Component
@ConditionalOnClass(MeterRegistry.class)
public class AuthMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(AuthMetricsCollector.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final MeterRegistry meterRegistry;

    private Counter permissionDenyCounter;
    private Counter permissionAllowCounter;
    private Counter cacheHitCounter;
    private Counter cacheMissCounter;
    private Timer permissionCheckTimer;

    /**
     * Redis 可用状态，通过 Gauge 暴露到监控系统。
     */
    private final java.util.concurrent.atomic.AtomicInteger redisAvailable = new java.util.concurrent.atomic.AtomicInteger(1);

    public AuthMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    private void initMetrics() {
        this.permissionDenyCounter = Counter.builder("auth.permission.deny")
                .description("权限拒绝次数")
                .register(meterRegistry);

        this.permissionAllowCounter = Counter.builder("auth.permission.allow")
                .description("权限通过次数")
                .register(meterRegistry);

        this.cacheHitCounter = Counter.builder("auth.cache.hit")
                .description("权限缓存命中次数")
                .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("auth.cache.miss")
                .description("权限缓存未命中次数")
                .register(meterRegistry);

        this.permissionCheckTimer = Timer.builder("auth.permission.check.time")
                .description("权限校验耗时")
                .register(meterRegistry);

        // Redis 可用状态 Gauge（绑定实例字段，可通过 updateRedisAvailable 动态更新）
        meterRegistry.gauge("auth.redis.available", redisAvailable, AtomicInteger::get);
    }

    /**
     * 记录权限校验通过。
     *
     * @param permissionType 权限类型
     */
    public void recordPermissionAllow(String permissionType) {
        permissionAllowCounter.increment();
    }

    /**
     * 记录权限校验拒绝，并写入安全审计日志。
     *
     * @param userId 用户 ID
     * @param permissionType 权限类型
     * @param requiredPermissions 缺少的权限
     * @param resource 资源路径
     */
    public void recordPermissionDeny(String userId, String permissionType,
                                      String requiredPermissions, String resource) {
        permissionDenyCounter.increment();

        // 写入安全审计日志
        securityLog.warn("[PERMISSION_DENIED] userId={}, type={}, required={}, resource={}",
                userId, permissionType, requiredPermissions, resource);
    }

    /**
     * 记录缓存命中。
     */
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    /**
     * 记录缓存未命中。
     */
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    /**
     * 记录权限校验耗时。
     *
     * @param nanos 耗时（纳秒）
     */
    public void recordCheckTime(long nanos) {
        permissionCheckTimer.record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 更新 Redis 可用状态。
     *
     * @param available Redis 是否可用
     */
    public void updateRedisAvailable(boolean available) {
        redisAvailable.set(available ? 1 : 0);
    }

    /**
     * 创建带标签的 Tag 集合。
     *
     * @param key   标签键
     * @param value 标签值
     * @return Tag 集合
     */
    public static Tags tags(String key, String value) {
        return Tags.of(Tag.of(key, value));
    }
}
