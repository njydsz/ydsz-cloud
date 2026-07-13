package com.njydsz.pmis.common.netty.pool;

import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty EventLoopGroup 池化管理器。
 *
 * <p>全局复用 boss/worker EventLoopGroup，避免每个 TCP 服务创建独立线程组，
 * 减少线程上下文切换开销，降低资源消耗。
 *
 * <p>使用懒加载 + 引用计数：首次获取时创建，所有使用者释放后关闭。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
public class NettyEventLoopPool {

    private static volatile EventLoopGroup bossGroup;
    private static volatile EventLoopGroup workerGroup;
    private static final AtomicInteger bossRefCount = new AtomicInteger(0);
    private static final AtomicInteger workerRefCount = new AtomicInteger(0);

    private NettyEventLoopPool() {
    }

    /**
     * 获取 boss EventLoopGroup（引用计数 +1）。
     *
     * @param threads 线程数（0 = 1）
     * @return boss EventLoopGroup
     */
    public static synchronized EventLoopGroup acquireBossGroup(int threads) {
        if (bossGroup == null) {
            int n = threads <= 0 ? 1 : threads;
            bossGroup = new NioEventLoopGroup(n);
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
    public static synchronized EventLoopGroup acquireWorkerGroup(int threads) {
        if (workerGroup == null) {
            int n = threads <= 0 ? Runtime.getRuntime().availableProcessors() * 2 : threads;
            workerGroup = new NioEventLoopGroup(n);
            log.info("[Netty-Pool] 创建 worker EventLoopGroup, threads={}", n);
        }
        workerRefCount.incrementAndGet();
        return workerGroup;
    }

    /**
     * 释放 boss EventLoopGroup（引用计数 -1，降为 0 时优雅关闭）。
     */
    public static synchronized void releaseBossGroup() {
        if (bossRefCount.decrementAndGet() <= 0 && bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
            log.info("[Netty-Pool] boss EventLoopGroup 已关闭");
        }
    }

    /**
     * 释放 worker EventLoopGroup（引用计数 -1，降为 0 时优雅关闭）。
     */
    public static synchronized void releaseWorkerGroup() {
        if (workerRefCount.decrementAndGet() <= 0 && workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
            log.info("[Netty-Pool] worker EventLoopGroup 已关闭");
        }
    }
}
