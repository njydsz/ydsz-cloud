package com.njydsz.pmis.common.netty.metric;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Netty Channel 指标收集器。
 *
 * <p>注册以下 Micrometer 指标：
 * <ul>
 *   <li>{@code pmis.netty.channels.active}（Gauge）— 活跃 Channel 数</li>
 *   <li>{@code pmis.netty.bytes.read.total}（Counter）— 累计读取字节数</li>
 *   <li>{@code pmis.netty.bytes.written.total}（Counter）— 累计写入字节数</li>
 * </ul>
 *
 * <p>当 MeterRegistry 不在 classpath 时降级为空操作（no-op）。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
public class NettyChannelMetrics {

    private static final String METRIC_CHANNELS_ACTIVE = "pmis.netty.channels.active";
    private static final String METRIC_BYTES_READ = "pmis.netty.bytes.read.total";
    private static final String METRIC_BYTES_WRITTEN = "pmis.netty.bytes.written.total";

    private final MeterRegistry meterRegistry;
    private final AtomicLong activeChannels = new AtomicLong(0);
    private final AtomicLong totalBytesRead = new AtomicLong(0);
    private final AtomicLong totalBytesWritten = new AtomicLong(0);

    /**
     * 构造 NettyChannelMetrics。
     *
     * @param meterRegistry MeterRegistry（可为 null，降级为 no-op）
     */
    public NettyChannelMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            Gauge.builder(METRIC_CHANNELS_ACTIVE, activeChannels, AtomicLong::doubleValue)
                    .description("活跃 Netty Channel 数")
                    .register(meterRegistry);
            Gauge.builder(METRIC_BYTES_READ, totalBytesRead, AtomicLong::doubleValue)
                    .description("累计读取字节数")
                    .register(meterRegistry);
            Gauge.builder(METRIC_BYTES_WRITTEN, totalBytesWritten, AtomicLong::doubleValue)
                    .description("累计写入字节数")
                    .register(meterRegistry);
            log.info("[Netty-Metrics] 指标已注册");
        }
    }

    /**
     * 递增活跃 Channel 数。
     */
    public void incrementActiveChannels() {
        activeChannels.incrementAndGet();
    }

    /**
     * 递减活跃 Channel 数。
     */
    public void decrementActiveChannels() {
        activeChannels.decrementAndGet();
    }

    /**
     * 累加读取字节数。
     *
     * @param bytes 字节数
     */
    public void addBytesRead(long bytes) {
        totalBytesRead.addAndGet(bytes);
    }

    /**
     * 累加写入字节数。
     *
     * @param bytes 字节数
     */
    public void addBytesWritten(long bytes) {
        totalBytesWritten.addAndGet(bytes);
    }

    /**
     * 获取当前活跃 Channel 数。
     *
     * @return 活跃 Channel 数
     */
    public long getActiveChannels() {
        return activeChannels.get();
    }
}
