package com.remisoft.common.netty.server;

import java.util.List;

import org.springframework.context.SmartLifecycle;

import lombok.extern.slf4j.Slf4j;

/**
 * Netty Server Spring 生命周期管理器。
 *
 * <p>实现 {@link SmartLifecycle}，随 Spring 容器启动自动启动所有注册的 {@link AbstractNettyServer}，
 * 容器关闭时优雅停止（释放 EventLoopGroup）。
 *
 * <p>自动扫描 Spring 容器中所有 {@link AbstractNettyServer} 子类 Bean，
 * 无需手动调用 {@code start()} / {@code stop()}。
 *
 * <p>支持 fail-fast 模式：当 {@code failFast=true} 时，任一 Server 启动失败
 * 将抛出异常终止 Spring 容器启动；{@code failFast=false} 时仅记录错误日志。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class NettyServerLifecycle implements SmartLifecycle {

    private final List<AbstractNettyServer> servers;
    private final boolean failFast;
    private volatile boolean running = false;

    /**
     * 构造 Netty Server 生命周期管理器。
     *
     * @param servers  Netty Server 列表
     * @param failFast 是否启用 fail-fast 模式
     */
    public NettyServerLifecycle(List<AbstractNettyServer> servers, boolean failFast) {
        this.servers = servers;
        this.failFast = failFast;
    }


    @Override
    public void start() {
        if (running) {
            return;
        }
        for (AbstractNettyServer server : servers) {
            try {
                server.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[Netty-Lifecycle] {} 启动被中断", server.getClass().getSimpleName(), e);
                if (failFast) {
                    throw new RuntimeException("Netty Server 启动被中断: "
                            + server.getClass().getSimpleName(), e);
                }
            } catch (Exception e) {
                log.error("[Netty-Lifecycle] {} 启动失败", server.getClass().getSimpleName(), e);
                if (failFast) {
                    throw new RuntimeException("Netty Server 启动失败: "
                            + server.getClass().getSimpleName(), e);
                }
            }
        }
        running = true;
        log.info("[Netty-Lifecycle] 所有 Netty Server 已启动");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        for (AbstractNettyServer server : servers) {
            try {
                server.stop();
            } catch (Exception e) {
                log.error("[Netty-Lifecycle] {} 停止异常", server.getClass().getSimpleName(), e);
            }
        }
        running = false;
        log.info("[Netty-Lifecycle] 所有 Netty Server 已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 在 Spring Web Server 之前启动
        return Integer.MIN_VALUE + 100;
    }
}
