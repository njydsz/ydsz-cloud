package com.njydsz.common.netty.metric;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * {@link NettyChannelMetrics} 单元测试。
 *
 * <p>验证指标采集行为：Counter 递增、Gauge 反映实时值、null MeterRegistry 降级。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("NettyChannelMetrics 指标采集测试")
class NettyChannelMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private NettyChannelMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new NettyChannelMetrics(meterRegistry);
    }

    @Test
    @DisplayName("incrementActiveChannels() 递增活跃 Channel 计数")
    void testIncrementActiveChannels() {
        metrics.incrementActiveChannels();
        metrics.incrementActiveChannels();
        metrics.incrementActiveChannels();

        assertEquals(3L, metrics.getActiveChannels());
        assertEquals(3.0, meterRegistry.find("ydsz.netty.channels.active").gauge().value());
    }

    @Test
    @DisplayName("decrementActiveChannels() 递减活跃 Channel 计数")
    void testDecrementActiveChannels() {
        metrics.incrementActiveChannels();
        metrics.incrementActiveChannels();
        metrics.decrementActiveChannels();

        assertEquals(1L, metrics.getActiveChannels());
    }

    @Test
    @DisplayName("活跃 Channel 数不会变为负数")
    void testActiveChannelsNotNegative() {
        metrics.decrementActiveChannels();
        assertEquals(0L, metrics.getActiveChannels());
    }

    @Test
    @DisplayName("addBytesRead() 累加读取字节数并更新 Gauge")
    void testAddBytesRead() {
        metrics.addBytesRead(1024L);
        metrics.addBytesRead(2048L);

        assertEquals(3072L, metrics.getTotalBytesRead());
        assertEquals(3072.0, meterRegistry.find("ydsz.netty.bytes.read.total").gauge().value());
    }

    @Test
    @DisplayName("addBytesWritten() 累加写入字节数并更新 Gauge")
    void testAddBytesWritten() {
        metrics.addBytesWritten(512L);
        metrics.addBytesWritten(512L);

        assertEquals(1024L, metrics.getTotalBytesWritten());
        assertEquals(1024.0, meterRegistry.find("ydsz.netty.bytes.written.total").gauge().value());
    }

    @Test
    @DisplayName("incrementMessagesReceived() 递增消息接收 Counter")
    void testIncrementMessagesReceived() {
        metrics.incrementMessagesReceived();
        metrics.incrementMessagesReceived();

        assertEquals(2.0, meterRegistry.counter("ydsz.netty.messages.received").count());
    }

    @Test
    @DisplayName("incrementMessagesSent() 递增消息发送 Counter")
    void testIncrementMessagesSent() {
        metrics.incrementMessagesSent();
        metrics.incrementMessagesSent();
        metrics.incrementMessagesSent();

        assertEquals(3.0, meterRegistry.counter("ydsz.netty.messages.sent").count());
    }

    @Test
    @DisplayName("incrementConnections() 递增累计连接数 Counter")
    void testIncrementConnections() {
        metrics.incrementConnections();

        assertEquals(1.0, meterRegistry.counter("ydsz.netty.connections.total").count());
    }

    @Test
    @DisplayName("incrementDisconnections() 递增累计断开数 Counter")
    void testIncrementDisconnections() {
        metrics.incrementDisconnections();
        metrics.incrementDisconnections();

        assertEquals(2.0, meterRegistry.counter("ydsz.netty.disconnections.total").count());
    }

    @Test
    @DisplayName("incrementReconnectAttempts() 递增重连尝试 Counter")
    void testIncrementReconnectAttempts() {
        metrics.incrementReconnectAttempts();
        metrics.incrementReconnectAttempts();
        metrics.incrementReconnectAttempts();

        assertEquals(3.0, meterRegistry.counter("ydsz.netty.reconnect.attempts").count());
    }

    @Test
    @DisplayName("incrementReconnectSuccesses() 递增重连成功 Counter")
    void testIncrementReconnectSuccesses() {
        metrics.incrementReconnectSuccesses();

        assertEquals(1.0, meterRegistry.counter("ydsz.netty.reconnect.successes").count());
    }

    @Test
    @DisplayName("MeterRegistry 为 null 时所有方法安全降级（无 NPE）")
    void testNullMeterRegistryDegradesGracefully() {
        NettyChannelMetrics nullMetrics = new NettyChannelMetrics(null);
        assertDoesNotThrow(() -> nullMetrics.incrementActiveChannels());
        assertDoesNotThrow(() -> nullMetrics.decrementActiveChannels());
        assertDoesNotThrow(() -> nullMetrics.addBytesRead(100L));
        assertDoesNotThrow(() -> nullMetrics.addBytesWritten(100L));
        assertDoesNotThrow(() -> nullMetrics.incrementMessagesReceived());
        assertDoesNotThrow(() -> nullMetrics.incrementMessagesSent());
        assertDoesNotThrow(() -> nullMetrics.incrementConnections());
        assertDoesNotThrow(() -> nullMetrics.incrementDisconnections());
        assertDoesNotThrow(() -> nullMetrics.incrementReconnectAttempts());
        assertDoesNotThrow(() -> nullMetrics.incrementReconnectSuccesses());

        // 内部 AtomicLong 仍正常累加，仅 Counter/Gauge 不注册
        assertEquals(100L, nullMetrics.getTotalBytesRead());
        assertEquals(100L, nullMetrics.getTotalBytesWritten());
    }
}
