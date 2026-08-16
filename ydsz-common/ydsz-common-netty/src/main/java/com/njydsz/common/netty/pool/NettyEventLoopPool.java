package com.njydsz.common.netty.pool;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.netty.channel.EventLoopGroup;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.netty.config.NettyProperties;
import com.njydsz.common.netty.transport.NativeTransportDetector;
import com.njydsz.common.netty.transport.NativeTransportDetector.TransportType;
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
 * <p>使用自定义线程命名（{@code ydsz-netty-boss-N} / {@code ydsz-netty-worker-N}），
 * 便于在线程 dump 中定位问题。
 *
 * <p>优雅关闭时等待 {@code shutdownGracefully()} 完成，避免在途消息丢失。
 *
 * @author ydsz-team
 * @since 1.0.0
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
    /** 原生传输类型 */
    private final TransportType transportType;

    /**
     * 构造 EventLoop 池（指定传输类型）。
     *
     * @param shutdownQuietPeriodSeconds 优雅关闭静默期（秒）
     * @param shutdownTimeoutSeconds     优雅关闭超时（秒）
     * @param nativeTransportMode        原生传输模式（auto / enabled / disabled）
     */
    public NettyEventLoopPool(long shutdownQuietPeriodSeconds, long shutdownTimeoutSeconds,
                              NettyProperties.NativeTransportMode nativeTransportMode) {
        this(shutdownQuietPeriodSeconds, shutdownTimeoutSeconds,
                NativeTransportDetector.detect(nativeTransportMode.name().toLowerCase()));
    }

    /**
     * 构造 EventLoop 池（指定传输类型）。
     *
     * @param shutdownQuietPeriodSeconds 优雅关闭静默期（秒）
     * @param shutdownTimeoutSeconds     优雅关闭超时（秒）
     * @param transportType              传输类型
     */
    public NettyEventLoopPool(long shutdownQuietPeriodSeconds, long shutdownTimeoutSeconds,
                              TransportType transportType) {
        this.shutdownQuietPeriodSeconds = shutdownQuietPeriodSeconds;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        this.transportType = transportType;
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
            bossGroup = NativeTransportDetector.createEventLoopGroup(transportType, n, "ydsz-netty-boss");
            log.info("[Netty-Pool] 创建 boss EventLoopGroup, threads={}, transport={}", n, transportType);
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
            workerGroup = NativeTransportDetector.createEventLoopGroup(transportType, n, "ydsz-netty-worker");
            log.info("[Netty-Pool] 创建 worker EventLoopGroup, threads={}, transport={}", n, transportType);
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
        EventLoopGroup group = NativeTransportDetector.createEventLoopGroup(transportType, n, "ydsz-netty-boss-iso");
        log.info("[Netty-Pool] 创建隔离 boss EventLoopGroup, threads={}, transport={}", n, transportType);
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
        EventLoopGroup group = NativeTransportDetector.createEventLoopGroup(transportType, n, "ydsz-netty-worker-iso");
        log.info("[Netty-Pool] 创建隔离 worker EventLoopGroup, threads={}, transport={}", n, transportType);
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

    /**
     * 获取当前传输类型。
     *
     * @return 传输类型
     */
    public TransportType getTransportType() {
        return transportType;
    }
}
