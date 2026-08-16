package com.njydsz.common.netty.client;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * 断线重连处理器（指数退避策略）。
 *
 * <p>当 Channel 断开时自动触发重连，重连间隔按指数退避增长：
 * {@code delay = min(initialDelay * 2^retryCount, maxDelay)}。
 *
 * <p>当达到 {@code maxRetries}（非 -1）时停止重连。
 * 重连成功后重置重试计数器。
 *
 * <p>使用方式：
 * <pre>{@code
 * @ChannelHandler.Sharable
 * public class MyReconnectHandler extends ReconnectHandler {
 *     private final Supplier<ChannelFuture> connectAction;
 *
 *     public MyReconnectHandler(long initialDelayMs, long maxDelayMs, int maxRetries,
 *                               Supplier<ChannelFuture> connectAction) {
 *         super(initialDelayMs, maxDelayMs, maxRetries);
 *         this.connectAction = connectAction;
 *     }
 *
 *     @Override
 *     protected void doReconnect() {
 *         connectAction.get().addListener(f -> {
 *             if (!f.isSuccess()) {
 *                 scheduleReconnect(); // 重连失败，继续重试
 *             }
 *         });
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ChannelHandler.Sharable
public abstract class ReconnectHandler extends ChannelInboundHandlerAdapter {

    /** 指数退避最大移位值（2^30 = 1,073,741,824 ms ≈ 12.4 天，防止位移溢出） */
    private static final int MAX_BACKOFF_SHIFT = 30;

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final int maxRetries;
    private final AtomicInteger retryCount = new AtomicInteger(0);

    /**
     * 构造断线重连处理器。
     *
     * @param initialDelayMs 初始重连延迟（毫秒）
     * @param maxDelayMs     最大重连延迟（毫秒）
     * @param maxRetries     最大重试次数（-1 = 无限重试）
     */
    protected ReconnectHandler(long initialDelayMs, long maxDelayMs, int maxRetries) {
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.maxRetries = maxRetries;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (maxRetries < 0 || retryCount.get() < maxRetries) {
            scheduleReconnect();
        } else {
            log.warn("[Netty-Reconnect] 已达最大重试次数 {}, 停止重连", maxRetries);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        // 连接成功，重置重试计数
        if (retryCount.get() > 0) {
            log.info("[Netty-Reconnect] 重连成功, previousRetries={}", retryCount.get());
        }
        retryCount.set(0);
    }

    /**
     * 调度下一次重连（指数退避）。
     */
    public void scheduleReconnect() {
        int current = retryCount.incrementAndGet();
        long delay = Math.min(initialDelayMs * (1L << Math.min(current - 1, MAX_BACKOFF_SHIFT)), maxDelayMs);

        log.info("[Netty-Reconnect] 计划重连: retry={}, delay={}ms", current, delay);

        ctx.executor().schedule(() -> {
            try {
                doReconnect();
            } catch (Exception e) {
                log.error("[Netty-Reconnect] 重连异常: {}", e.getMessage(), e);
                scheduleReconnect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 执行实际重连操作（由子类实现具体的连接逻辑）。
     */
    protected abstract void doReconnect();

    /**
     * 获取当前重试次数。
     *
     * @return 重试次数
     */
    public int getRetryCount() {
        return retryCount.get();
    }

    private volatile ChannelHandlerContext ctx;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        this.ctx = ctx;
    }
}
