package com.njydsz.common.netty.event;

import java.util.List;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * Channel 事件分发器 — 将 Channel 连接/断开事件分发给所有注册的 {@link ChannelEventListener}。
 *
 * <p>作为 Netty Pipeline 中的 Handler 自动拦截 {@code channelActive} / {@code channelInactive} 事件，
 * 转发给所有监听器。支持单个监听器异常不影响其他监听器执行。
 *
 * <p>该 Handler 是 {@code @Sharable} 的，可被多个 Channel 共享。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ChannelHandler.Sharable
public class ChannelEventDispatcher extends ChannelInboundHandlerAdapter {

    private final List<ChannelEventListener> listeners;

    /**
     * 构造 Channel 事件分发器。
     *
     * @param listeners 监听器列表（可为空列表）
     */
    public ChannelEventDispatcher(List<ChannelEventListener> listeners) {
        this.listeners = listeners != null ? listeners : List.of();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        for (ChannelEventListener listener : listeners) {
            try {
                listener.onChannelActive(channel);
            } catch (Exception e) {
                log.warn("[Netty-Event] 监听器 {} onChannelActive 异常: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        for (ChannelEventListener listener : listeners) {
            try {
                listener.onChannelInactive(channel);
            } catch (Exception e) {
                log.warn("[Netty-Event] 监听器 {} onChannelInactive 异常: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        super.channelInactive(ctx);
    }
}
