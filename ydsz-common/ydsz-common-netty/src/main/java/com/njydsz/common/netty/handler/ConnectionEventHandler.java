package com.njydsz.common.netty.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.netty.metric.NettyChannelMetrics;

/**
 * 连接事件监控 Handler — 自动追踪 Channel 的创建与销毁。
 *
 * <p>在 {@code channelActive} 时递增活跃连接数和累计连接数，
 * 在 {@code channelInactive} 时递减活跃连接数并递增累计断开数。
 *
 * <p>该 Handler 是 {@code @Sharable} 的，可被多个 Channel 共享。
 * 当 {@link NettyChannelMetrics} 为 {@code null} 时降级为空操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ChannelHandler.Sharable
public class ConnectionEventHandler extends ChannelInboundHandlerAdapter {

    private final NettyChannelMetrics metrics;

    /**
     * 构造连接事件监控 Handler。
     *
     * @param metrics Netty 指标收集器（可为 null，降级为 no-op）
     */
    public ConnectionEventHandler(NettyChannelMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (metrics != null) {
            metrics.incrementActiveChannels();
            metrics.incrementConnections();
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (metrics != null) {
            metrics.decrementActiveChannels();
            metrics.incrementDisconnections();
        }
        super.channelInactive(ctx);
    }
}
