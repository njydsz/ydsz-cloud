package com.njydsz.common.socket.metric;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * WebSocket Micrometer 指标收集器（P1-4）。
 *
 * <p>实现 {@link NetworkMetrics} 顶层网络指标契约（P2-9），注册以下指标：
 *
 * <ul>
 *   <li>{@code ydsz.websocket.channels.active}（Gauge）— 活跃连接数
 *   <li>{@code ydsz.websocket.connections.total}（Counter）— 累计连接数
 *   <li>{@code ydsz.websocket.disconnections.total}（Counter）— 累计断开数
 *   <li>{@code ydsz.websocket.messages.received}（Counter）— 消息接收数
 *   <li>{@code ydsz.websocket.messages.sent}（Counter）— 消息发送数
 *   <li>{@code ydsz.websocket.push.total}（Counter）— 推送次数，tag: type, result
 *   <li>{@code ydsz.websocket.push.duration}（Timer）— 推送耗时（含 P99 百分位）
 * </ul>
 *
 * <p>推送结果计数器在构造时预构建（PushResultKey → Counter 映射），避免每次
 * push 都做 Counter.builder() 构建与 register() 查找，减少高并发下对象分配。
 *
 * <p>当 MeterRegistry 不在 classpath 时降级为空操作（no-op）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class WebSocketMetrics implements NetworkMetrics {

  private static final String METRIC_CHANNELS_ACTIVE = "ydsz.websocket.channels.active";
  private static final String METRIC_CONNECTIONS = "ydsz.websocket.connections.total";
  private static final String METRIC_DISCONNECTIONS = "ydsz.websocket.disconnections.total";
  private static final String METRIC_MESSAGES_RECEIVED = "ydsz.websocket.messages.received";
  private static final String METRIC_MESSAGES_SENT = "ydsz.websocket.messages.sent";
  private static final String METRIC_PUSH_TOTAL = "ydsz.websocket.push.total";
  private static final String METRIC_PUSH_DURATION = "ydsz.websocket.push.duration";

  /** 推送结果计数器组合键（pushType × result）。 */
  private record PushResultKey(String type, boolean success) {}

  private final MeterRegistry meterRegistry;
  private final AtomicLong activeChannels = new AtomicLong(0);
  private final AtomicLong totalBytesRead = new AtomicLong(0);
  private final AtomicLong totalBytesWritten = new AtomicLong(0);

  private Counter connectionsCounter;
  private Counter disconnectionsCounter;
  private Counter messagesReceivedCounter;
  private Counter messagesSentCounter;

  /** 预构建的推送结果计数器映射。 */
  private final Map<PushResultKey, Counter> pushCounterCache = new HashMap<>(8);

  /**
   * 构造 WebSocketMetrics。
   *
   * @param meterRegistry MeterRegistry（可为 null，降级为 no-op）
   */
  public WebSocketMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    if (meterRegistry != null) {
      Gauge.builder(METRIC_CHANNELS_ACTIVE, activeChannels, AtomicLong::doubleValue)
          .description("活跃 WebSocket 连接数")
          .register(meterRegistry);
      connectionsCounter =
          Counter.builder(METRIC_CONNECTIONS)
              .description("累计 WebSocket 连接数")
              .register(meterRegistry);
      disconnectionsCounter =
          Counter.builder(METRIC_DISCONNECTIONS)
              .description("累计 WebSocket 断开数")
              .register(meterRegistry);
      messagesReceivedCounter =
          Counter.builder(METRIC_MESSAGES_RECEIVED)
              .description("WebSocket 消息接收数")
              .register(meterRegistry);
      messagesSentCounter =
          Counter.builder(METRIC_MESSAGES_SENT)
              .description("WebSocket 消息发送数")
              .register(meterRegistry);
      initPushCounters();
    }
  }

  /**
   * 预构建所有 (pushType × result) 组合对应的 Counter。
   *
   * <p>固定组合仅为 3×2=6 种（USER/BROADCAST/TOPIC × success/failure），
   * 在构造期一次性分配到缓存 Map，recordPush() 路径上零对象分配。
   */
  private void initPushCounters() {
    String[] pushTypes = {"USER", "BROADCAST", "TOPIC"};
    for (String type : pushTypes) {
      registerPushCounter(type, true);
      registerPushCounter(type, false);
    }
  }

  /**
   * 注册并缓存单个推送结果计数器。
   *
   * @param type 推送类型
   * @param success 是否成功
   */
  private void registerPushCounter(String type, boolean success) {
    Counter counter =
        Counter.builder(METRIC_PUSH_TOTAL)
            .tags(Tags.of("type", type, "result", success ? "success" : "failure"))
            .description("WebSocket 推送次数")
            .register(meterRegistry);
    pushCounterCache.put(new PushResultKey(type, success), counter);
  }

  // ==================== NetworkMetrics 契约实现 ====================

  /** 递增活跃连接数。 */
  @Override
  public void incrementActiveChannels() {
    activeChannels.incrementAndGet();
  }

  /** 递减活跃连接数（不会变为负数）。 */
  @Override
  public void decrementActiveChannels() {
    activeChannels.updateAndGet(curr -> Math.max(0, curr - 1));
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

  /**
   * 获取当前活跃连接数。
   *
   * @return 活跃连接数
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

  // ==================== WebSocket 业务指标 ====================

  /**
   * 记录一次推送结果（O(1) 查找，零对象分配）。
   *
   * @param pushType 推送类型（USER / BROADCAST / TOPIC）
   * @param success 是否成功
   */
  public void recordPush(String pushType, boolean success) {
    if (meterRegistry == null) {
      return;
    }
    PushResultKey key = new PushResultKey(pushType, success);
    Counter counter = pushCounterCache.get(key);
    if (counter != null) {
      counter.increment();
    } else {
      // 未知 pushType（如自定义扩展）降级为动态注册，兼容未来扩展
      Counter.builder(METRIC_PUSH_TOTAL)
          .tags(Tags.of("type", pushType, "result", success ? "success" : "failure"))
          .register(meterRegistry)
          .increment();
    }
    // 同时更新消息发送计数
    incrementMessagesSent();
  }

  /**
   * 记录推送耗时（含 P99 百分位）。
   *
   * @param pushType 推送类型
   * @param duration 耗时
   */
  public void recordPushDuration(String pushType, Duration duration) {
    if (meterRegistry == null) {
      return;
    }
    Timer.builder(METRIC_PUSH_DURATION)
        .tags(Tags.of("type", pushType))
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)
        .record(duration);
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
   * 获取底层 MeterRegistry（供高级用户使用）。
   *
   * @return MeterRegistry 实例，可能为 null
   */
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }
}
