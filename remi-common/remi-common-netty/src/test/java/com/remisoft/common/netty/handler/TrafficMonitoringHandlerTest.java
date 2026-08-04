package com.remisoft.common.netty.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.remisoft.common.netty.metric.NettyChannelMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * {@link TrafficMonitoringHandler} 单元测试。
 *
 * <p>验证流量监控 Handler 正确统计入站读字节数和出站写字节数，
 * 并将数据委托给 {@link NettyChannelMetrics}。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("TrafficMonitoringHandler 流量监控测试")
class TrafficMonitoringHandlerTest {

    @Test
    @DisplayName("入站 ByteBuf 累加读取字节数和消息接收计数")
    void testChannelReadAccumulatesBytesRead() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NettyChannelMetrics metrics = new NettyChannelMetrics(registry);
        TrafficMonitoringHandler handler = new TrafficMonitoringHandler(metrics);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[256]));
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[512]));

        assertEquals(768L, metrics.getTotalBytesRead());
        assertEquals(2.0, registry.counter("remi.netty.messages.received").count());

        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("出站 ByteBuf 累加写入字节数和消息发送计数")
    void testWriteAccumulatesBytesWritten() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NettyChannelMetrics metrics = new NettyChannelMetrics(registry);
        TrafficMonitoringHandler handler = new TrafficMonitoringHandler(metrics);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        channel.writeOutbound(Unpooled.wrappedBuffer(new byte[1024]));
        channel.writeOutbound(Unpooled.wrappedBuffer(new byte[2048]));

        assertEquals(3072L, metrics.getTotalBytesWritten());
        assertEquals(2.0, registry.counter("remi.netty.messages.sent").count());

        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("非 ByteBuf 消息不累加字节数")
    void testNonByteBufMessageIgnored() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NettyChannelMetrics metrics = new NettyChannelMetrics(registry);
        TrafficMonitoringHandler handler = new TrafficMonitoringHandler(metrics);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // 写入字符串消息，不应累加字节数
        channel.writeInbound("hello");

        assertEquals(0L, metrics.getTotalBytesRead());
        assertEquals(0.0, registry.counter("remi.netty.messages.received").count());

        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("null metrics 时 Handler 降级为 no-op（不抛 NPE）")
    void testNullMetricsDegradesGracefully() {
        TrafficMonitoringHandler handler = new TrafficMonitoringHandler(null);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertDoesNotThrow(() -> channel.writeInbound(Unpooled.wrappedBuffer(new byte[128])));
        assertDoesNotThrow(() -> channel.writeOutbound(Unpooled.wrappedBuffer(new byte[128])));

        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("@Sharable 注解允许 Handler 跨 Channel 共享")
    void testSharableAnnotation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NettyChannelMetrics metrics = new NettyChannelMetrics(registry);
        TrafficMonitoringHandler handler = new TrafficMonitoringHandler(metrics);

        // 同一 handler 实例用于多个 EmbeddedChannel
        EmbeddedChannel ch1 = new EmbeddedChannel(handler);
        EmbeddedChannel ch2 = new EmbeddedChannel(handler);

        ch1.writeInbound(Unpooled.wrappedBuffer(new byte[100]));
        ch2.writeInbound(Unpooled.wrappedBuffer(new byte[200]));

        // 共享 metrics 实例汇总所有 Channel 的字节数
        assertEquals(300L, metrics.getTotalBytesRead());

        ch1.finishAndReleaseAll();
        ch2.finishAndReleaseAll();
    }
}
