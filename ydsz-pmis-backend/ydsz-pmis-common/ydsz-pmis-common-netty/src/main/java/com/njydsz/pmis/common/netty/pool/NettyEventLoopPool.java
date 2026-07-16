package com.njydsz.pmis.common.netty.pool;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty EventLoopGroup 池化管理器。
 *
 * <p>实例化使用（非 static），支持共享和隔离两种模式：
 * <ul>
 *   <li><b>共享模式</b>（默认）：所有 Server/Client 复用同一个 boss/worker EventLoopGroup，
 *       通过引用计数管理生命周期，减少线程上下文切换开销</li>
 *   <li><b>隔离模式</b>：每个 Server/Client 创建独立的 EventLoopGroup，
 *       避免高吞吐服务饿死低频服务</li>
 * </ul>
 *
 * <p>使用自定义线程命名（{@code pmis-netty-boss-N} / {@code pmis-netty-worker-N}），
 * 便于在线程 dump 中定位问题。
 *
 * <p>优雅关闭时等待 {@code shutdownGracefully()} 完成，避免在途消息丢失。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
public class NettyEventLoopPool {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private final AtomicInteger bossRefCount = new AtomicInteger(0);
    private final AtomicInteger workerRefCount = new AtomicInteger(0);

    /** 优雅关闭静默期（秒） */
    private final long shutdownQuietPeriodSeconds;
    /** 优雅关闭超时（秒） */
    private final long shutdownTimeoutSeconds;

    /**
     * 构造默认 EventLoop 池（静默期 2s，超时 15s）。
     */
    public NettyEventLoopPool() {
        this(2L, 15L);
    }

    /**
     * 构造 EventLoop 池。
     *
     * @param shutdownQuietPeriodSeconds 优雅关闭静默期（秒）
     * @param shutdownTimeoutSeconds     优雅关闭超时（秒）
     */
    public NettyEventLoopPool(long shutdownQuietPeriodSeconds, long shutdownTimeoutSeconds) {
        this.shutdownQuietPeriodSeconds = shutdownQuietPeriodSeconds;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }

    /**
     * 获取 boss EventLoopGroup（引用计数 +1）。
     *
     * @param threads 线程数（0 = 1）
     * @return boss EventLoopGroup
     */
    public synchronized EventLoopGroup acquireBossGroup(int threads) {
        if (bossGroup == null) {
            int n = threads <= 0 ? 1 : threads;
            bossGroup = new NioEventLoopGroup(n, new DefaultThreadFactory("pmis-netty-boss"));
            log.info("[Netty-Pool] 创建 boss EventLoopGroup, threads={}", n);
        }
        bossRefCount.incrementAndGet();
        return bossGroup;
    }

    /**
     * 获取 worker EventLoopGroup（引用计数 +1）。
     *
     * @param threads 线程数（0 = CPU 核数 * 2）
     * @return worker EventLoopGroup
     */
    public synchronized EventLoopGroup acquireWorkerGroup(int threads) {
        if (workerGroup == null) {
            int n = threads <= 0 ? Runtime.getRuntime().availableProcessors() * 2 : threads;
            workerGroup = new NioEventLoopGroup(n, new DefaultThreadFactory("pmis-netty-worker"));
            log.info("[Netty-Pool] 创建 worker EventLoopGroup, threads={}", n);
        }
        workerRefCount.incrementAndGet();
        return workerGroup;
    }

    /**
     * 创建独立的 boss EventLoopGroup（隔离模式，不共享，不引用计数）。
     *
     * @param threads 线程数（0 = 1）
     * @return 独立的 boss EventLoopGroup
     */
    public EventLoopGroup createIsolatedBossGroup(int threads) {
        int n = threads <= 0 ? 1 : threads;
        EventLoopGroup group = new NioEventLoopGroup(n, new DefaultThreadFactory("pmis-netty-boss-iso"));
        log.info("[Netty-Pool] 创建隔离 boss EventLoopGroup, threads={}", n);
        return group;
    }

    /**
     * 创建独立的 worker EventLoopGroup（隔离模式，不共享，不引用计数）。
     *
     * @param threads 线程数（0 = CPU 核数 * 2）
     * @return 独立的 worker EventLoopGroup
     */
    public EventLoopGroup createIsolatedWorkerGroup(int threads) {
        int n = threads <= 0 ? Runtime.getRuntime().availableProcessors() * 2 : threads;
        EventLoopGroup group = new NioEventLoopGroup(n, new DefaultThreadFactory("pmis-netty-worker-iso"));
        log.info("[Netty-Pool] 创建隔离 worker EventLoopGroup, threads={}", n);
        return group;
    }

    /**
     * 释放 boss EventLoopGroup（引用计数 -1，降为 0 时优雅关闭并等待完成）。
     */
    public synchronized void releaseBossGroup() {
        if (bossRefCount.decrementAndGet() <= 0 && bossGroup != null) {
            try {
                bossGroup.shutdownGracefully(shutdownQuietPeriodSeconds, shutdownTimeoutSeconds, TimeUnit.SECONDS)
                        .await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Netty-Pool] boss EventLoopGroup 关闭被中断");
            }
            bossGroup = null;
            log.info("[Netty-Pool] boss EventLoopGroup 已关闭");
        }
    }

    /**
     * 释放 worker EventLoopGroup（引用计数 -1，降为 0 时优雅关闭并等待完成）。
     */
    public synchronized void releaseWorkerGroup() {
        if (workerRefCount.decrementAndGet() <= 0 && workerGroup != null) {
            try {
                workerGroup.shutdownGracefully(shutdownQuietPeriodSeconds, shutdownTimeoutSeconds, TimeUnit.SECONDS)
                        .await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Netty-Pool] worker EventLoopGroup 关闭被中断");
            }
            workerGroup = null;
            log.info("[Netty-Pool] worker EventLoopGroup 已关闭");
        }
    }

    /**
     * 关闭独立的 EventLoopGroup（隔离模式使用）。
     *
     * @param group 要关闭的 EventLoopGroup
     */
    public void shutdownGroup(EventLoopGroup group) {
        if (group != null) {
            try {
                group.shutdownGracefully(shutdownQuietPeriodSeconds, shutdownTimeoutSeconds, TimeUnit.SECONDS)
                        .await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Netty-Pool] 独立 EventLoopGroup 关闭被中断");
            }
        }
    }

    /**
     * 获取 boss 引用计数。
     *
     * @return 引用计数
     */
    public int getBossRefCount() {
        return bossRefCount.get();
    }

    /**
     * 获取 worker 引用计数。
     *
     * @return 引用计数
     */
    public int getWorkerRefCount() {
        return workerRefCount.get();
    }

    /**
     * 判断 boss EventLoopGroup 是否已创建。
     *
     * @return true 表示已创建
     */
    public boolean isBossGroupActive() {
        return bossGroup != null && !bossGroup.isShutdown();
    }

    /**
     * 判断 worker EventLoopGroup 是否已创建。
     *
     * @return true 表示已创建
     */
    public boolean isWorkerGroupActive() {
        return workerGroup != null && !workerGroup.isShutdown();
    }
}
