package com.njydsz.common.event.processor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.event.config.EventProperties;
import com.njydsz.common.event.gateway.EventPublishGateway;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.OutboxStatus;
import com.njydsz.common.event.repository.OutboxRepository;
import com.njydsz.common.thread.factory.InternalExecutorFactory;

/**
 * Outbox 后台轮询处理器
 *
 * <p>定时扫描 PENDING 状态的 Outbox 消息，通过 {@link EventPublishGateway} 投递到消息队列。 投递成功标记为
 * SENT，失败则增加重试计数并指数退避。
 *
 * <p>核心增强：
 *
 * <ul>
 *   <li>批量 claim：单条 SQL 原子批量抢占消息，避免 N+1 查询
 *   <li>多线程投递：轮询和投递分离，MQ 慢时不阻塞轮询
 *   <li>超时回收：定期回收卡在 PROCESSING 状态的消息
 *   <li>Gauge 指标：队列深度按状态暴露到 Prometheus
 *   <li>分离 Timer：批量投递和单条投递独立计时
 *   <li>自动清理：定期清理已投递的历史消息
 * </ul>
 *
 * <p>退避策略：baseDelay * 2^min(retryCount,30)，最大不超过 maxBackoffSeconds。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class OutboxProcessor {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(OutboxProcessor.class);

  /** 位移量上限，防止 1L << retryCount 整数溢出 */
  private static final int MAX_SHIFT = 30;

  /** Outbox 仓储 */
  private final OutboxRepository outboxRepository;

  /** 事件投递网关 */
  private final EventPublishGateway publishGateway;

  /** 事件配置属性 */
  private final EventProperties properties;

  /** 调度线程池（单线程，仅负责轮询和 claim） */
  private final ScheduledExecutorService scheduler;

  /** 投递线程池（可配置线程数，负责实际 MQ 发送） */
  private final ExecutorService publishExecutor;

  /** 投递成功计数器 */
  private final Counter publishSuccessCounter;

  /** 投递失败计数器 */
  private final Counter publishFailureCounter;

  /** 死信计数器 */
  private final Counter deadLetterCounter;

  /** 单条投递耗时计时器 */
  private final Timer singlePublishTimer;

  /** 批量投递耗时计时器 */
  private final Timer batchPublishTimer;

  /** 缓存的队列深度（每次轮询后更新，供 Gauge 读取） */
  private volatile Map<String, Long> cachedStatusCounts = Map.of();

  /** 运行状态标志 */
  private volatile boolean running = false;

  /**
   * 创建 Outbox 处理器（使用默认自创建线程池）。
   *
   * <p>调度线程（单线程）和投递线程池均使用 {@code ydsz-} 前缀命名， 符合云顶编码规范 15.4.4 命名约定。
   *
   * @param outboxRepository Outbox 仓储
   * @param publishGateway 投递网关
   * @param properties 事件配置属性
   * @param meterRegistry Micrometer 指标注册器（可为 null）
   */
  public OutboxProcessor(
      OutboxRepository outboxRepository,
      EventPublishGateway publishGateway,
      EventProperties properties,
      MeterRegistry meterRegistry) {
    this(
        outboxRepository,
        publishGateway,
        properties,
        meterRegistry,
        createDefaultScheduler(),
        createDefaultPublishExecutor(properties));
  }

  /**
   * 创建 Outbox 处理器（使用外部注入的线程池）。
   *
   * <p>允许业务方注入自定义线程池以替换默认实现。
   *
   * @param outboxRepository Outbox 仓储
   * @param publishGateway 投递网关
   * @param properties 事件配置属性
   * @param meterRegistry Micrometer 指标注册器（可为 null）
   * @param scheduler 外部注入的调度线程池
   * @param publishExecutor 外部注入的投递线程池
   */
  public OutboxProcessor(
      OutboxRepository outboxRepository,
      EventPublishGateway publishGateway,
      EventProperties properties,
      MeterRegistry meterRegistry,
      ScheduledExecutorService scheduler,
      ExecutorService publishExecutor) {
    this.outboxRepository = outboxRepository;
    this.publishGateway = publishGateway;
    this.properties = properties;
    this.scheduler = scheduler;
    this.publishExecutor = publishExecutor;

    if (meterRegistry != null) {
      this.publishSuccessCounter =
          Counter.builder("ydsz.outbox.publish.success")
              .description("Outbox messages published successfully")
              .register(meterRegistry);
      this.publishFailureCounter =
          Counter.builder("ydsz.outbox.publish.failure")
              .description("Outbox messages failed to publish")
              .register(meterRegistry);
      this.deadLetterCounter =
          Counter.builder("ydsz.outbox.dead_letter")
              .description("Outbox messages moved to dead letter")
              .register(meterRegistry);
      this.singlePublishTimer =
          Timer.builder("ydsz.outbox.publish.single.duration")
              .description("Outbox single message publish duration")
              .publishPercentiles(0.5, 0.9, 0.99)
              .publishPercentileHistogram()
              .register(meterRegistry);
      this.batchPublishTimer =
          Timer.builder("ydsz.outbox.publish.batch.duration")
              .description("Outbox batch publish duration")
              .publishPercentiles(0.5, 0.9, 0.99)
              .publishPercentileHistogram()
              .register(meterRegistry);

      // 队列深度 Gauge
      for (OutboxStatus status : OutboxStatus.values()) {
        Gauge.builder("ydsz.outbox.queue.size", () -> getCachedCount(status))
            .tag("status", status.name())
            .description("Outbox queue depth by status")
            .register(meterRegistry);
      }
    } else {
      this.publishSuccessCounter = null;
      this.publishFailureCounter = null;
      this.deadLetterCounter = null;
      this.singlePublishTimer = null;
      this.batchPublishTimer = null;
    }
  }

  private long getCachedCount(OutboxStatus status) {
    return cachedStatusCounts.getOrDefault(status.name(), 0L);
  }

  /**
   * 启动 Outbox 轮询处理器
   *
   * <p>启动以下定时任务：
   *
   * <ul>
   *   <li>主轮询任务：定期扫描 PENDING 消息并投递
   *   <li>超时回收任务：定期回收 PROCESSING 状态超时的消息
   *   <li>自动清理任务：定期清理已投递的历史消息
   * </ul>
   */
  public void start() {
    if (running) {
      return;
    }
    running = true;

    long pollInterval = properties.getPollIntervalSeconds();

    // 主轮询任务
    scheduler.scheduleWithFixedDelay(
        this::processBatch, pollInterval, pollInterval, TimeUnit.SECONDS);

    // 超时回收任务（每 2 倍轮询间隔执行一次）
    int staleThreshold = properties.getStaleProcessingThresholdMinutes();
    scheduler.scheduleWithFixedDelay(
        () -> reclaimStaleMessages(staleThreshold),
        pollInterval * 2,
        pollInterval * 2,
        TimeUnit.SECONDS);

    // 自动清理任务
    if (properties.isAutoCleanup() && properties.getSentRetentionDays() > 0) {
      long cleanupInterval = properties.getCleanupIntervalHours();
      scheduler.scheduleWithFixedDelay(
          () -> cleanupSentMessages(properties.getSentRetentionDays()),
          cleanupInterval,
          cleanupInterval,
          TimeUnit.HOURS);
    }

    LOG.info(
        "OutboxProcessor started: pollInterval={}s, batchSize={}, workerThreads={}, staleThreshold={}min",
        pollInterval,
        properties.getBatchSize(),
        properties.getWorkerThreads(),
        staleThreshold);
  }

  /**
   * 停止 Outbox 轮询处理器
   *
   * <p>优雅关闭调度线程和投递线程池，等待最多 {@code awaitTerminationSeconds} 秒。
   */
  public void stop() {
    running = false;
    scheduler.shutdown();
    publishExecutor.shutdown();
    try {
      int timeout = properties.getAwaitTerminationSeconds();
      if (!scheduler.awaitTermination(timeout, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
      if (!publishExecutor.awaitTermination(timeout, TimeUnit.SECONDS)) {
        publishExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      publishExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    LOG.info("OutboxProcessor stopped");
  }

  /**
   * 处理一批待投递消息
   *
   * <p>执行流程：查询 PENDING 消息 → 批量 claim → 分发投递任务
   */
  void processBatch() {
    try {
      // 更新队列深度缓存（供 Gauge 读取），使用缓存版本减少 DB 压力
      cachedStatusCounts = outboxRepository.countByStatus(true);

      List<OutboxMessage> messages = outboxRepository.findPending(properties.getBatchSize());
      if (messages.isEmpty()) {
        return;
      }
      LOG.debug("Processing {} pending outbox messages", messages.size());

      // 批量 claim（单条 SQL）
      List<String> ids = messages.stream().map(OutboxMessage::getId).toList();
      int claimedCount = outboxRepository.claimBatchForProcessing(ids);

      if (claimedCount == 0) {
        return;
      }

      if (claimedCount == messages.size()) {
        // 快速路径：全部 claim 成功
        dispatchPublish(messages);
      } else {
        // 部分被其他实例 claim，逐条 claim 失败的消息跳过
        for (OutboxMessage msg : messages) {
          if (outboxRepository.claimForProcessing(msg.getId())) {
            dispatchPublish(List.of(msg));
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Error processing outbox batch", e);
    }
  }

  /**
   * 分发投递任务到工作线程池
   *
   * <p>始终提交到工作线程池执行，避免调度线程被慢 MQ 阻塞。 当 worker 线程池满时，{@link
   * java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy} 会回退到调用线程（调度线程）执行，提供自然的背压——即 MQ
   * 慢时自动降低轮询频率， 但不会因为单次慢投递阻塞整个轮询周期。
   */
  private void dispatchPublish(List<OutboxMessage> messages) {
    Runnable task =
        messages.size() > 1
            ? () -> processBatchPublish(messages)
            : () -> processSingle(messages.get(0));
    publishExecutor.execute(task);
  }

  /**
   * 批量投递消息到 MQ
   *
   * <p>批量投递失败时降级为逐条投递。
   *
   * @param messages 待投递消息列表
   */
  private void processBatchPublish(List<OutboxMessage> messages) {
    long startNanos = System.nanoTime();
    try {
      List<Boolean> results = publishGateway.publishBatch(messages);
      long durationNanos = System.nanoTime() - startNanos;

      for (int i = 0; i < messages.size() && i < results.size(); i++) {
        OutboxMessage message = messages.get(i);
        if (Boolean.TRUE.equals(results.get(i))) {
          outboxRepository.markAsSent(message.getId());
          incrementCounter(publishSuccessCounter);
          LOG.debug("Outbox message sent: id={}, type={}", message.getId(), message.getEventType());
        } else {
          handleFailure(message, "Gateway returned false in batch");
        }
      }
      recordTimer(batchPublishTimer, durationNanos);
    } catch (Throwable e) {
      recordTimer(batchPublishTimer, System.nanoTime() - startNanos);
      // 批量投递失败，降级为逐条投递
      LOG.warn("Batch publish failed, falling back to single publish", e);
      for (OutboxMessage message : messages) {
        processSingle(message);
      }
    }
  }

  /**
   * 处理单条消息投递
   *
   * @param message Outbox 消息
   */
  private void processSingle(OutboxMessage message) {
    long startNanos = System.nanoTime();
    try {
      boolean success = publishGateway.publish(message);
      recordTimer(singlePublishTimer, System.nanoTime() - startNanos);

      if (success) {
        outboxRepository.markAsSent(message.getId());
        incrementCounter(publishSuccessCounter);
        LOG.debug("Outbox message sent: id={}, type={}", message.getId(), message.getEventType());
      } else {
        handleFailure(message, "Gateway returned false");
      }
    } catch (Throwable e) {
      recordTimer(singlePublishTimer, System.nanoTime() - startNanos);
      handleFailure(message, e.getMessage());
    }
  }

  /**
   * 处理投递失败：更新重试计数、计算退避时间、判断是否进入死信
   *
   * @param message Outbox 消息
   * @param errorMessage 错误信息
   */
  private void handleFailure(OutboxMessage message, String errorMessage) {
    long backoff = calculateBackoff(message.getRetryCount());
    outboxRepository.markAsFailed(message.getId(), errorMessage, backoff);

    incrementCounter(publishFailureCounter);

    if (message.getRetryCount() + 1 >= message.getMaxRetries()) {
      incrementCounter(deadLetterCounter);
      LOG.warn(
          "Outbox message moved to dead letter: id={}, retryCount={}, error={}",
          message.getId(),
          message.getRetryCount() + 1,
          errorMessage);
    } else {
      LOG.warn(
          "Outbox message publish failed, will retry: id={}, retryCount={}, backoff={}s, error={}",
          message.getId(),
          message.getRetryCount() + 1,
          backoff,
          errorMessage);
    }
  }

  /**
   * 指数退避计算
   *
   * <p>使用 {@code Math.min(retryCount, MAX_SHIFT)} 防止位移溢出。
   *
   * @param retryCount 当前重试次数
   * @return 退避秒数
   */
  private long calculateBackoff(int retryCount) {
    int shift = Math.min(retryCount, MAX_SHIFT);
    long backoff = properties.getBaseBackoffSeconds() * (1L << shift);
    return Math.min(backoff, properties.getMaxBackoffSeconds());
  }

  /**
   * 回收超时的 PROCESSING 消息
   *
   * @param thresholdMinutes 超时阈值（分钟）
   */
  private void reclaimStaleMessages(int thresholdMinutes) {
    try {
      outboxRepository.reclaimStaleProcessing(thresholdMinutes);
    } catch (Exception e) {
      LOG.error("Error reclaiming stale processing messages", e);
    }
  }

  /**
   * 清理已投递的历史消息
   *
   * @param retentionDays 保留天数，早于此天数的 SENT 消息将被删除
   */
  public void cleanupSentMessages(int retentionDays) {
    Instant cutoff = Instant.now().minusSeconds(retentionDays * 86400L);
    int deleted = outboxRepository.deleteSentBefore(cutoff);
    if (deleted > 0) {
      LOG.info("Cleaned up {} sent outbox messages older than {} days", deleted, retentionDays);
    }
  }

  /**
   * 递增计数器（空安全）
   *
   * @param counter 计数器，可为 null
   */
  private void incrementCounter(Counter counter) {
    if (counter != null) {
      counter.increment();
    }
  }

  private void recordTimer(Timer timer, long durationNanos) {
    if (timer != null) {
      timer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * 创建默认调度线程池（单线程，负责轮询和 claim）。
   *
   * <p>命名符合云顶编码规范 15.4.4 约定：ydsz-{module}-{biz}-。
   *
   * @return 默认调度线程池
   */
  static ScheduledExecutorService createDefaultScheduler() {
    return InternalExecutorFactory.newSingleThreadScheduledPool("outbox-scheduler");
  }

  /**
   * 创建默认投递线程池（可配置线程数，负责实际 MQ 发送）。
   *
   * <p>使用 {@link SynchronousQueue} 实现自然的背压（worker 满时 CallerRuns）。
   *
   * @param properties 事件配置属性
   * @return 默认投递线程池
   */
  static ExecutorService createDefaultPublishExecutor(EventProperties properties) {
    int workerThreads = Math.max(1, properties.getWorkerThreads());
    return InternalExecutorFactory.newFixedThreadPool("outbox-worker", workerThreads);
  }
}
