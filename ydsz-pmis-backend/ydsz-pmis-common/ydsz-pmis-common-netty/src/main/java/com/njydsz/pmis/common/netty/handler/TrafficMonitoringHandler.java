package com.njydsz.pmis.common.netty.handler;

import com.njydsz.pmis.common.netty.metric.NettyChannelMetrics;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import lombok.extern.slf4j.Slf4j;

/**
 * 流量监控 Handler — 自动统计每条 Channel 的读写字节数。
 *
 * <p>作为 {@link ChannelDuplexHandler} 同时拦截入站读和出站写事件，
 * 将字节数累加到 {@link NettyChannelMetrics}。
 *
 * <p>该 Handler 是 {@code @Sharable} 的，可被多个 Channel 共享，
 * 因为所有统计都委托给同一个 {@link NettyChannelMetrics} 实例。
 *
 * <p>当 {@link NettyChannelMetrics} 为 {@code null} 时降级为空操作。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@ChannelHandler.Sharable
public class TrafficMonitoringHandler extends ChannelDuplexHandler {

    private final NettyChannelMetrics metrics;

    /**
     * 构造流量监控 Handler。
     *
     * @param metrics Netty 指标收集器（可为 null，降级为 no-op）
     */
    public TrafficMonitoringHandler(NettyChannelMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (metrics != null && msg instanceof ByteBuf buf) {
            metrics.addBytesRead(buf.readableBytes());
            metrics.incrementMessagesReceived();
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (metrics != null && msg instanceof ByteBuf buf) {
            metrics.addBytesWritten(buf.readableBytes());
            metrics.incrementMessagesSent();
        }
        super.write(ctx, msg, promise);
    }
}
