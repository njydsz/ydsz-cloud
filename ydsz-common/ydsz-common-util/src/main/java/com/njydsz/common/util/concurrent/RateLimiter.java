package com.njydsz.common.util.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 轻量级限流器（令牌桶算法）。
 *
 * <p>提供单机内存级限流能力，适用于：
 * <ul>
 *   <li>API 调用频率控制（如第三方接口调用限流）</li>
 *   <li>资源访问限流（如数据库连接池获取限流）</li>
 *   <li>任务执行速率控制（如批量任务并发控制）</li>
 * </ul>
 *
 * <p><b>算法：</b>令牌桶（Token Bucket）
 * <ul>
 *   <li>以固定速率向桶中添加令牌</li>
 *   <li>每次获取令牌时消耗一枚令牌</li>
 *   <li>桶满时新令牌丢弃（允许突发流量 = 桶容量）</li>
 *   <li>桶空时调用方等待或立即返回失败（取决于调用方式）</li>
 * </ul>
 *
 * <p><b>与 Guava RateLimiter 对比：</b>
 * <ul>
 *   <li>不支持预平滑（smooth bursting），仅支持固定速率</li>
 *   <li>不支持动态修改速率（创建后速率固定）</li>
 *   <li>更轻量：无依赖、无预热逻辑、无 SleepingStopwatch</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 创建限流器：每秒 100 个令牌，桶容量 200（允许 2 倍突发）
 * RateLimiter limiter = RateLimiter.create(100, 200);
 *
 * // 阻塞式获取（等待直到获取成功）
 * limiter.acquire();
 *
 * // 带超时的非阻塞获取
 * boolean acquired = limiter.tryAcquire(100, TimeUnit.MILLISECONDS);
 * if (!acquired) {
 *     // 限流降级逻辑
 * }
 * }</pre>
 *
 * <p><b>线程安全：</b>本类线程安全，多线程共享同一实例。
 *
 * <p><b>注意：</b>本限流器为单机版，分布式场景请使用 Redis + Lua 或 Sentinel。
 *
 * @author ydsz-team
 * @since 4.0.0
 */
public final class RateLimiter {

    /** 每秒令牌数（速率） */
    private final double permitsPerSecond;

    /** 桶最大容量（允许的突发量） */
    private final double maxPermits;

    /** 每枚令牌之间的间隔（纳秒） */
    private final long intervalNanos;

    /** 当前可用令牌数 */
    private double storedPermits;

    /** 上次补充令牌的时间（纳秒） */
    private volatile long lastRefillNanos;

    /**
     * 创建限流器。
     *
     * @param permitsPerSecond 每秒允许的请求数（必须 > 0）
     * @param maxBurstSeconds   桶容量（以秒为单位，即允许的突发秒数，必须 ≥ 1）
     */
    private RateLimiter(double permitsPerSecond, double maxBurstSeconds) {
        this.permitsPerSecond = permitsPerSecond;
        this.maxPermits = permitsPerSecond * maxBurstSeconds;
        this.intervalNanos = Math.round(1_000_000_000.0 / permitsPerSecond);
        this.storedPermits = this.maxPermits; // 初始满桶，允许突发
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * 创建限流器（指定速率和桶容量）。
     *
     * @param permitsPerSecond 每秒允许的请求数（必须 > 0）
     * @param burstCapacity    桶容量（允许的突发请求数，必须 ≥ 1）
     * @return 限流器实例
     * @throws IllegalArgumentException 参数非法
     */
    public static RateLimiter create(double permitsPerSecond, int burstCapacity) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be positive: " + permitsPerSecond);
        }
        if (burstCapacity < 1) {
            throw new IllegalArgumentException("burstCapacity must be >= 1: " + burstCapacity);
        }
        return new RateLimiter(permitsPerSecond, burstCapacity / permitsPerSecond);
    }

    /**
     * 创建限流器（桶容量 = 1 秒令牌量，无突发）。
     *
     * @param permitsPerSecond 每秒允许的请求数（必须 > 0）
     * @return 限流器实例
     */
    public static RateLimiter create(double permitsPerSecond) {
        return create(permitsPerSecond, 1);
    }

    /**
     * 阻塞式获取一枚令牌。
     *
     * <p>如果当前无可用令牌，会阻塞等待直到获取成功。
     *
     * @return 等待时间（毫秒）
     */
    public synchronized double acquire() {
        return acquire(1);
    }

    /**
     * 阻塞式获取指定数量的令牌。
     *
     * @param permits 需要获取的令牌数（必须 > 0）
     * @return 等待时间（毫秒）
     */
    public synchronized double acquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive: " + permits);
        }

        long nowNanos = System.nanoTime();
        refill(nowNanos);

        double waitTimeNanos = reserve(permits);
        if (waitTimeNanos <= 0) {
            return 0;
        }

        // 等待指定时间
        long waitMillis = (long) (waitTimeNanos / 1_000_000);
        int waitRemainder = (int) (waitTimeNanos % 1_000_000);
        if (waitMillis > 0 || waitRemainder > 0) {
            LockSupport.parkNanos(waitMillis * 1_000_000L + waitRemainder);
        }

        return waitTimeNanos / 1_000_000.0;
    }

    /**
     * 尝试在指定超时时间内获取一枚令牌。
     *
     * @param timeout 最大等待时间
     * @param unit    时间单位
     * @return 获取成功返回 true，超时返回 false
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return tryAcquire(1, timeout, unit);
    }

    /**
     * 尝试在指定超时时间内获取指定数量的令牌。
     *
     * @param permits 需要获取的令牌数（必须 > 0）
     * @param timeout 最大等待时间（必须 ≥ 0）
     * @param unit    时间单位
     * @return 获取成功返回 true，超时返回 false
     */
    public synchronized boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be positive: " + permits);
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative: " + timeout);
        }

        long timeoutNanos = unit.toNanos(timeout);
        long nowNanos = System.nanoTime();
        refill(nowNanos);

        double waitTimeNanos = reserve(permits);
        if (waitTimeNanos <= 0) {
            return true; // 立即获取成功
        }

        // 需要等待，检查是否能在超时内完成
        if (waitTimeNanos <= timeoutNanos) {
            long waitMillis = (long) (waitTimeNanos / 1_000_000);
            int waitRemainder = (int) (waitTimeNanos % 1_000_000);
            LockSupport.parkNanos(waitMillis * 1_000_000L + waitRemainder);
            return true;
        }

        return false; // 超时
    }

    /**
     * 尝试立即获取一枚令牌（非阻塞）。
     *
     * @return 获取成功返回 true，无可用令牌返回 false
     */
    public boolean tryAcquire() {
        return tryAcquire(1, 0, TimeUnit.NANOSECONDS);
    }

    /**
     * 补充令牌（基于时间差计算）。
     *
     * @param nowNanos 当前时间（纳秒）     */
    private void refill(long nowNanos) {
        long elapsedNanos = nowNanos - lastRefillNanos;
        if (elapsedNanos > 0) {
            double newPermits = elapsedNanos / (double) intervalNanos;
            storedPermits = Math.min(storedPermits + newPermits, maxPermits);
            lastRefillNanos = nowNanos;
        }
    }

    /**
     * 预留令牌，返回需要等待的纳秒数。
     *
     * @param permits 需要预留的令牌数
     * @return 需要等待的纳秒数（0 表示立即获取）
     */
    private double reserve(int permits) {
        if (storedPermits >= permits) {
            storedPermits -= permits;
            return 0;
        }
        double deficit = permits - storedPermits;
        storedPermits = 0;
        return deficit * intervalNanos;
    }

    /**
     * 获取当前可用令牌数（近似值）。
     *
     * @return 当前可用令牌数
     */
    public synchronized double getStoredPermits() {
        refill(System.nanoTime());
        return storedPermits;
    }

    /**
     * 获取速率（每秒令牌数）。
     *
     * @return 每秒允许的请求数
     */
    public double getRate() {
        return permitsPerSecond;
    }

    /**
     * 获取桶容量。
     *
     * @return 最大令牌数
     */
    public double getMaxPermits() {
        return maxPermits;
    }
}
