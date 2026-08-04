package com.remisoft.common.netty.handler;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.remisoft.common.netty.metric.NettyChannelMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * {@link ConnectionEventHandler} 单元测试。
 *
 * <p>验证连接事件监控 Handler 在 Channel 活跃/非活跃时正确更新指标。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("ConnectionEventHandler 连接事件测试")
class ConnectionEventHandlerTest {

    @Test
    @DisplayName("Channel 激活时递增活跃连接数和累计连接数")
    void testChannelActiveIncrementsCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NettyChannelMetrics metrics = new NettyChannelMetrics(registry);
        ConnectionEventHandler handler = new ConnectionEventHandler(metrics);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // EmbeddedChannel 构造时已触发 channelActive
        assertEquals(1L, metrics.getActiveChannels());
        assertEquals(1.0, registry.counter("remi.netty.connections.total").count());

        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("Channel 失效时递减活跃连接数并递增累计断开数")
    void testChannelInactiveDecrementsActiveAndIncrementsDisconnections() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NettyChannelMetrics metrics = new NettyChannelMetrics(registry);
        ConnectionEventHandler handler = new ConnectionEventHandler(metrics);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        // 关闭 Channel 触发 channelInactive
        channel.close().syncUninterruptibly();

        assertEquals(0L, metrics.getActiveChannels());
        assertEquals(1.0, registry.counter("remi.netty.disconnections.total").count());
    }

    @Test
    @DisplayName("多个 Channel 同时活跃时活跃数正确累加")
    void testMultipleChannelsActive() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NettyChannelMetrics metrics = new NettyChannelMetrics(registry);
        ConnectionEventHandler handler = new ConnectionEventHandler(metrics);

        EmbeddedChannel ch1 = new EmbeddedChannel(handler);
        EmbeddedChannel ch2 = new EmbeddedChannel(handler);
        EmbeddedChannel ch3 = new EmbeddedChannel(handler);

        assertEquals(3L, metrics.getActiveChannels());
        assertEquals(3.0, registry.counter("remi.netty.connections.total").count());

        ch1.finishAndReleaseAll();
        ch2.finishAndReleaseAll();
        ch3.finishAndReleaseAll();

        assertEquals(0L, metrics.getActiveChannels());
        assertEquals(3.0, registry.counter("remi.netty.disconnections.total").count());
    }

    @Test
    @DisplayName("null metrics 时 Handler 降级为 no-op")
    void testNullMetricsDegradesGracefully() {
        ConnectionEventHandler handler = new ConnectionEventHandler(null);
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertDoesNotThrow(() -> channel.close().syncUninterruptibly());
    }

    @Test
    @DisplayName("@Sharable 注解允许 Handler 跨多个 Channel 共享")
    void testSharableAnnotation() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NettyChannelMetrics metrics = new NettyChannelMetrics(registry);

        // 同一 handler 实例用于多个 Channel
        ConnectionEventHandler handler = new ConnectionEventHandler(metrics);

        EmbeddedChannel ch1 = new EmbeddedChannel(handler);
        EmbeddedChannel ch2 = new EmbeddedChannel(handler);

        assertEquals(2L, metrics.getActiveChannels());

        ch1.finishAndReleaseAll();
        ch2.finishAndReleaseAll();
    }
}
