package com.njydsz.pmis.common.netty.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.SmartLifecycle;

import java.util.List;

/**
 * Netty Server Spring 生命周期管理器。
 *
 * <p>实现 {@link SmartLifecycle}，随 Spring 容器启动自动启动所有注册的 {@link AbstractNettyServer}，
 * 容器关闭时优雅停止（释放 EventLoopGroup）。
 *
 * <p>自动扫描 Spring 容器中所有 {@link AbstractNettyServer} 子类 Bean，
 * 无需手动调用 {@code start()} / {@code stop()}。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class NettyServerLifecycle implements SmartLifecycle, SmartInitializingSingleton {

    private final List<AbstractNettyServer> servers;
    private volatile boolean running = false;

    @Override
    public void afterSingletonsInstantiated() {
        // Spring 容器初始化完成后，不自动启动；等 SmartLifecycle.start() 触发
        log.info("[Netty-Lifecycle] 检测到 {} 个 Netty Server 待启动", servers.size());
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
            } catch (Exception e) {
                log.error("[Netty-Lifecycle] {} 启动失败", server.getClass().getSimpleName(), e);
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
