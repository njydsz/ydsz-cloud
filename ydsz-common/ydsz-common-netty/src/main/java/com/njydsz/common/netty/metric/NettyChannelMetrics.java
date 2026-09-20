package com.njydsz.common.netty.metric;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.util.internal.PlatformDependent;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.netty.api.ConnectionMetrics;

/**
 * Netty Channel 指标收集器。
 *
 * <p>注册以下 Micrometer 指标：
 *
 * <ul>
 *   <li>{@code ydsz.netty.channels.active}（Gauge）— 活跃 Channel 数
 *   <li>{@code ydsz.netty.bytes.read.total}（Counter）— 累计读取字节数
 *   <li>{@code ydsz.netty.bytes.written.total}（Counter）— 累计写入字节数
 *   <li>{@code ydsz.netty.connections.total}（Counter）— 累计连接数
 *   <li>{@code ydsz.netty.disconnections.total}（Counter）— 累计断开数
 *   <li>{@code ydsz.netty.messages.received}（Counter）— 消息接收数
 *   <li>{@code ydsz.netty.messages.sent}（Counter）— 消息发送数
 *   <li>{@code ydsz.netty.reconnect.attempts}（Counter）— 重连尝试次数
 *   <li>{@code ydsz.netty.reconnect.successes}（Counter）— 重连成功次数
 * </ul>
 *
 * <p>当 MeterRegistry 不在 classpath 时降级为空操作（no-op）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class NettyChannelMetrics implements ConnectionMetrics {

  private static final String METRIC_CHANNELS_ACTIVE = "ydsz.netty.channels.active";
  private static final String METRIC_BYTES_READ = "ydsz.netty.bytes.read.total";
  private static final String METRIC_BYTES_WRITTEN = "ydsz.netty.bytes.written.total";
  private static final String METRIC_CONNECTIONS = "ydsz.netty.connections.total";
  private static final String METRIC_DISCONNECTIONS = "ydsz.netty.disconnections.total";
  private static final String METRIC_MESSAGES_RECEIVED = "ydsz.netty.messages.received";
  private static final String METRIC_MESSAGES_SENT = "ydsz.netty.messages.sent";
  private static final String METRIC_RECONNECT_ATTEMPTS = "ydsz.netty.reconnect.attempts";
  private static final String METRIC_RECONNECT_SUCCESSES = "ydsz.netty.reconnect.successes";
  private static final String METRIC_DIRECT_MEMORY_USED = "ydsz.netty.direct.memory.used";
  private static final String METRIC_DIRECT_MEMORY_MAX = "ydsz.netty.direct.memory.max";
  private static final String METRIC_POOLED_ARENAS_ACTIVE = "ydsz.netty.arenas.active";

  private final AtomicLong activeChannels = new AtomicLong(0);
  private final AtomicLong totalBytesRead = new AtomicLong(0);
  private final AtomicLong totalBytesWritten = new AtomicLong(0);

  private Counter connectionsCounter;
  private Counter disconnectionsCounter;
  private Counter messagesReceivedCounter;
  private Counter messagesSentCounter;
  private Counter reconnectAttemptsCounter;
  private Counter reconnectSuccessesCounter;

  /**
   * 构造 NettyChannelMetrics。
   *
   * @param meterRegistry MeterRegistry（可为 null，降级为 no-op）
   */
  public NettyChannelMetrics(MeterRegistry meterRegistry) {
    if (meterRegistry != null) {
      // 在本实例注册完成前，先保存 this 引用供 Gauge lambda 使用
      NettyChannelMetrics self = this;
      Gauge.builder(METRIC_CHANNELS_ACTIVE, activeChannels, AtomicLong::doubleValue)
          .description("活跃 Netty Channel 数")
          .register(meterRegistry);
      Gauge.builder(METRIC_BYTES_READ, totalBytesRead, AtomicLong::doubleValue)
          .description("累计读取字节数")
          .register(meterRegistry);
      Gauge.builder(METRIC_BYTES_WRITTEN, totalBytesWritten, AtomicLong::doubleValue)
          .description("累计写入字节数")
          .register(meterRegistry);
      connectionsCounter =
          Counter.builder(METRIC_CONNECTIONS).description("累计连接数").register(meterRegistry);
      disconnectionsCounter =
          Counter.builder(METRIC_DISCONNECTIONS).description("累计断开数").register(meterRegistry);
      messagesReceivedCounter =
          Counter.builder(METRIC_MESSAGES_RECEIVED).description("消息接收数").register(meterRegistry);
      messagesSentCounter =
          Counter.builder(METRIC_MESSAGES_SENT).description("消息发送数").register(meterRegistry);
      reconnectAttemptsCounter =
          Counter.builder(METRIC_RECONNECT_ATTEMPTS).description("重连尝试次数").register(meterRegistry);
      reconnectSuccessesCounter =
          Counter.builder(METRIC_RECONNECT_SUCCESSES).description("重连成功次数").register(meterRegistry);
      Gauge.builder(METRIC_DIRECT_MEMORY_USED, self, m -> (double) m.getDirectMemoryUsed())
          .description("Netty 直接内存当前使用量（字节）")
          .register(meterRegistry);
      Gauge.builder(METRIC_DIRECT_MEMORY_MAX, self,
              m -> (double) estimateMaxDirectMemory())
          .description("JVM 最大直接内存限制（字节）")
          .register(meterRegistry);
      Gauge.builder(METRIC_POOLED_ARENAS_ACTIVE, self,
              m -> (double) countActiveArenas())
          .description("Netty PooledByteBufAllocator 活跃 Arena 数")
          .register(meterRegistry);
      log.info("[Netty-Metrics] 指标已注册（含直接内存监控）");
    }
  }

  /** 递增活跃 Channel 数。 */
  @Override
  public void incrementActiveChannels() {
    activeChannels.incrementAndGet();
  }

  /** 递减活跃 Channel 数（不会变为负数）。 */
  @Override
  public void decrementActiveChannels() {
    activeChannels.updateAndGet(curr -> Math.max(0, curr - 1));
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

  /** 递增消息接收计数。 */
  @Override
  public void incrementMessagesReceived() {
    if (messagesReceivedCounter != null) {
      messagesReceivedCounter.increment();
    }
  }

  /** 递增消息发送计数。 */
  @Override
  public void incrementMessagesSent() {
    if (messagesSentCounter != null) {
      messagesSentCounter.increment();
    }
  }

  /** 递增连接计数。 */
  @Override
  public void incrementConnections() {
    if (connectionsCounter != null) {
      connectionsCounter.increment();
    }
  }

  /** 递增断开计数。 */
  @Override
  public void incrementDisconnections() {
    if (disconnectionsCounter != null) {
      disconnectionsCounter.increment();
    }
  }

  /** 递增重连尝试计数。 */
  public void incrementReconnectAttempts() {
    if (reconnectAttemptsCounter != null) {
      reconnectAttemptsCounter.increment();
    }
  }

  /** 递增重连成功计数。 */
  public void incrementReconnectSuccesses() {
    if (reconnectSuccessesCounter != null) {
      reconnectSuccessesCounter.increment();
    }
  }

  /**
   * 获取当前活跃 Channel 数。
   *
   * @return 活跃 Channel 数
   */
  @Override
  public long getActiveChannels() {
    return activeChannels.get();
  }

  /**
   * 获取累计读取字节数。
   *
   * @return 读取字节数
   */
  @Override
  public long getTotalBytesRead() {
    return totalBytesRead.get();
  }

  /**
   * 获取累计写入字节数。
   *
   * @return 写入字节数
   */
  @Override
  public long getTotalBytesWritten() {
    return totalBytesWritten.get();
  }

  /**
   * 估算 JVM 最大直接内存（Direct Memory）限制。
   *
   * <p>优先使用 {@code sun.misc.VM.maxDirectMemory()}， 回退到 {@code -XX:MaxDirectMemorySize} 默认值（通常为 -Xmx）。
   *
   * @return 最大直接内存字节数
   */
  private static long estimateMaxDirectMemory() {
    try {
      return PlatformDependent.maxDirectMemory();
    } catch (Throwable t) {
      // 兜底：若 PlatformDependent 不可用，回退到 Runtime.maxMemory()
      return Runtime.getRuntime().maxMemory();
    }
  }

  /**
   * 估算 Netty PooledByteBufAllocator 的活跃 Arena 数。
   *
   * <p>用于监控 Arena 配置是否合理。Arena 数通常等于 {@code numDirectArena} 参数， 过多 Arena 会增加内存碎片，过少会降低并发性能。
   *
   * @return 活跃 Arena 估算数（当前为配置值，待接入 allocator 引用后精确计算）
   */
  private int countActiveArenas() {
    // TODO: 接入 PooledByteBufAllocator 引用后，通过 metric() 接口获取精确值
    try {
      return PlatformDependent.directBufferPreferred() ? Runtime.getRuntime().availableProcessors() * 2 : 0;
    } catch (Throwable t) {
      return 0;
    }
  }

  /**
   * 获取直接内存当前使用量（字节）。
   *
   * @return 直接内存使用量
   */
  public long getDirectMemoryUsed() {
    try {
      return PlatformDependent.usedDirectMemory();
    } catch (Throwable t) {
      return -1;
    }
  }

  /**
   * 获取直接内存使用率（0.0 - 1.0）。
   *
   * <p>当使用率超过 0.8 时应触发告警，接近 1.0 时 {@link OutOfMemoryError: Direct buffer memory} 即将发生。
   *
   * @return 直接内存使用率，无法获取时返回 -1.0
   */
  public double getDirectMemoryUsageRatio() {
    try {
      long used = PlatformDependent.usedDirectMemory();
      long max = estimateMaxDirectMemory();
      return max <= 0 ? -1.0 : (double) used / max;
    } catch (Throwable t) {
      return -1.0;
    }
  }
}
