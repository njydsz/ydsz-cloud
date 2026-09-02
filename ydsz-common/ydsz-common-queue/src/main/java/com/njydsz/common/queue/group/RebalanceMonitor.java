package com.njydsz.common.queue.group;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.queue.group.ConsumerGroupEvent.EventType;
import com.njydsz.common.thread.factory.InternalExecutorFactory;

/**
 * 消费组 Rebalance 监控器
 *
 * <p>周期性扫描 Redis Stream 消费组的消费者列表，检测消费者加入/离开事件， 并触发 {@link ConsumerGroupRebalanceListener} 回调。
 *
 * <p><b>工作原理：</b>
 *
 * <ol>
 *   <li>以固定间隔（默认 30 秒）调用 XPENDING 获取当前消费者列表
 *   <li>与上一次快照对比，检测新增/移除的消费者
 *   <li>触发对应类型的 {@link ConsumerGroupEvent} 事件
 * </ol>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * RebalanceMonitor monitor = new RebalanceMonitor(redisTemplate, "my-stream", "my-group");
 * monitor.addListener(event -> log.info("消费组变化: {}", event));
 * monitor.start();
 * // ...
 * monitor.close();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RebalanceMonitor implements DisposableBean {

  /** 默认扫描间隔（秒） */
  private static final long DEFAULT_SCAN_INTERVAL_SECONDS = 30;

  private final RedisTemplate<String, Object> redisTemplate;
  private final String channel;
  private final String groupName;
  private final long scanIntervalSeconds;
  private final List<ConsumerGroupRebalanceListener> listeners = new CopyOnWriteArrayList<>();

  private ScheduledExecutorService scheduler;
  private ScheduledFuture<?> scheduledTask;
  private volatile Set<String> lastConsumerSnapshot = new HashSet<>(16);

  /**
   * 创建消费组 Rebalance 监控器
   *
   * @param redisTemplate Redis 连接模板
   * @param channel Stream Key
   * @param groupName 消费组名称
   */
  public RebalanceMonitor(
      RedisTemplate<String, Object> redisTemplate, String channel, String groupName) {
    this(redisTemplate, channel, groupName, DEFAULT_SCAN_INTERVAL_SECONDS);
  }

  /**
   * 创建消费组 Rebalance 监控器（自定义扫描间隔）
   *
   * @param redisTemplate Redis 连接模板
   * @param channel Stream Key
   * @param groupName 消费组名称
   * @param scanIntervalSeconds 扫描间隔（秒）
   */
  public RebalanceMonitor(
      RedisTemplate<String, Object> redisTemplate,
      String channel,
      String groupName,
      long scanIntervalSeconds) {
    if (redisTemplate == null) {
      throw new IllegalArgumentException("RedisTemplate 不能为空");
    }
    if (channel == null || channel.isEmpty()) {
      throw new IllegalArgumentException("Stream channel 不能为空");
    }
    if (groupName == null || groupName.isEmpty()) {
      throw new IllegalArgumentException("消费组名称不能为空");
    }
    this.redisTemplate = redisTemplate;
    this.channel = channel;
    this.groupName = groupName;
    this.scanIntervalSeconds =
        scanIntervalSeconds > 0 ? scanIntervalSeconds : DEFAULT_SCAN_INTERVAL_SECONDS;
  }

  /**
   * 添加消费组变化监听器
   *
   * @param listener 监听器实例
   */
  public void addListener(ConsumerGroupRebalanceListener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  /**
   * 移除消费组变化监听器
   *
   * @param listener 监听器实例
   */
  public void removeListener(ConsumerGroupRebalanceListener listener) {
    listeners.remove(listener);
  }

  /**
   * 启动消费组监控
   *
   * <p>创建单线程调度器，周期性扫描消费组变化。
   */
  public void start() {
    if (scheduler != null && !scheduler.isShutdown()) {
      log.warn("[RebalanceMonitor] 监控器已在运行中, channel={}, group={}", channel, groupName);
      return;
    }
    // 单线程调度器：仅用于周期性扫描消费组状态，轻量级后台任务，统一使用 InternalExecutorFactory
    scheduler = InternalExecutorFactory.newSingleThreadScheduledPool(
        "queue-rebalance-monitor-" + groupName);
    scheduledTask =
        scheduler.scheduleWithFixedDelay(
            this::scanAndDetectChanges, 0, scanIntervalSeconds, TimeUnit.SECONDS);
    log.info(
        "[RebalanceMonitor] 监控器已启动，channel={}, group={}, interval={}s",
        channel,
        groupName,
        scanIntervalSeconds);
  }

  /** 关闭监控器，释放调度器资源 */
  @Override
  public void destroy() {
    close();
  }

  /** 关闭监控器 */
  public void close() {
    if (scheduledTask != null) {
      scheduledTask.cancel(false);
    }
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
    lastConsumerSnapshot.clear();
    log.info("[RebalanceMonitor] 监控器已关闭，channel={}, group={}", channel, groupName);
  }

  /** 扫描消费组并检测消费者变化 */
  private void scanAndDetectChanges() {
    try {
      Set<String> currentConsumers = fetchCurrentConsumers();
      detectChanges(currentConsumers);
      lastConsumerSnapshot = currentConsumers;
    } catch (Exception e) {
      log.warn(
          "[RebalanceMonitor] 扫描消费组异常，channel={}, group={}, error={}",
          channel,
          groupName,
          e.getMessage());
    }
  }

  /**
   * 获取当前消费者列表
   *
   * <p>使用 XPENDING 命令获取消费组中所有有 pending 消息的消费者。
   */
  private Set<String> fetchCurrentConsumers() {
    Set<String> consumers = new HashSet<>(16);
    try {
      PendingMessagesSummary summary = redisTemplate.opsForStream().pending(channel, groupName);
      if (summary != null) {
        // getPendingMessagesPerConsumer 返回 Map<String, Long>，key 为消费者名称
        for (Map.Entry<String, Long> entry : summary.getPendingMessagesPerConsumer().entrySet()) {
          if (entry.getValue() != null && entry.getValue() > 0) {
            consumers.add(entry.getKey());
          }
        }
      }
    } catch (Exception e) {
      log.debug("[RebalanceMonitor] 获取消费者列表异常: {}", e.getMessage());
    }
    return consumers;
  }

  /** 检测消费者变化并触发事件 */
  private void detectChanges(Set<String> currentConsumers) {
    if (lastConsumerSnapshot.isEmpty() && !currentConsumers.isEmpty()) {
      // 首次扫描，记录快照不触发事件
      return;
    }

    // 检测新增的消费者
    for (String consumer : currentConsumers) {
      if (!lastConsumerSnapshot.contains(consumer)) {
        fireEvent(consumer, EventType.CONSUMER_ADDED, currentConsumers.size());
      }
    }

    // 检测移除的消费者
    for (String consumer : lastConsumerSnapshot) {
      if (!currentConsumers.contains(consumer)) {
        fireEvent(consumer, EventType.CONSUMER_REMOVED, currentConsumers.size());
      }
    }
  }

  /** 触发消费组事件 */
  private void fireEvent(String consumerName, EventType eventType, int totalConsumers) {
    ConsumerGroupEvent event =
        ConsumerGroupEvent.builder()
            .groupName(groupName)
            .channel(channel)
            .consumerName(consumerName)
            .eventType(eventType)
            .totalConsumers(totalConsumers)
            .timestamp(LocalDateTime.now())
            .build();

    for (ConsumerGroupRebalanceListener listener : listeners) {
      try {
        listener.onGroupChange(event);
      } catch (Exception e) {
        log.warn(
            "[RebalanceMonitor] 监听器执行异常, eventType={}, listener={}, error={}",
            eventType,
            listener.getClass().getSimpleName(),
            e.getMessage());
      }
    }
    log.info(
        "[RebalanceMonitor] 消费组事件: type={}, consumer={}, total={}, channel={}, group={}",
        eventType,
        consumerName,
        totalConsumers,
        channel,
        groupName);
  }
}
