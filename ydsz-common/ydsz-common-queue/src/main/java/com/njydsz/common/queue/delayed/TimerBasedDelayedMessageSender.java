package com.njydsz.common.queue.delayed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;

/**
 * 基于定时器的延时消息发送器
 *
 * <p>使用 {@link ScheduledExecutorService} 实现延时消息发送， 适用于不支持原生延时的 MQ 实现（如 Redis、RabbitMQ 等）。
 *
 * <p><b>工作原理：</b>
 *
 * <ol>
 *   <li>接收延时消息后，将消息提交到定时器队列
 *   <li>定时器到期后，将消息投递到底层 MQ
 *   <li>支持消息取消（仅到期前有效）
 * </ol>
 *
 * <p><b>使用限制：</b>
 *
 * <ul>
 *   <li>消息在定时器到期前存储于内存，宕机后会丢失
 *   <li>大量延时消息会占用内存，建议控制并发量
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * TimerBasedDelayedMessageSender sender = new TimerBasedDelayedMessageSender(publisher, executor);
 * sender.publishDelayed(QueueMessage.of("hello"), 30, TimeUnit.SECONDS);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TimerBasedDelayedMessageSender implements DelayedMessageSender, DisposableBean {

  /** 待投递任务映射：messageId -> 定时任务 */
  private final Map<String, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();

  private final IMessagePublisher publisher;
  private final ScheduledExecutorService scheduler;

  /**
   * 创建定时器延时消息发送器
   *
   * @param publisher 底层消息发布者
   * @param scheduler 定时器调度器（可为 null，默认创建单线程调度器）
   */
  public TimerBasedDelayedMessageSender(
      IMessagePublisher publisher, ScheduledExecutorService scheduler) {
    if (publisher == null) {
      throw new IllegalArgumentException("消息发布者不能为空");
    }
    this.publisher = publisher;
    this.scheduler = scheduler != null ? scheduler : createDefaultScheduler();
  }

  /**
   * 创建定时器延时消息发送器（使用默认调度器）
   *
   * @param publisher 底层消息发布者
   */
  public TimerBasedDelayedMessageSender(IMessagePublisher publisher) {
    this(publisher, null);
  }

  @Override
  public void publishDelayed(QueueMessage message, long delay, TimeUnit timeUnit) {
    publishDelayed(message, DelaySpec.fixed(delay, timeUnit));
  }

  @Override
  public void publishDelayed(QueueMessage message, DelaySpec delaySpec) {
    if (message == null) {
      return;
    }
    if (delaySpec == null) {
      delaySpec = DelaySpec.fixed(0, TimeUnit.MILLISECONDS);
    }

    long delayMillis = delaySpec.toMillis();
    if (delayMillis <= 0) {
      // 无延迟，直接投递
      publisher.publish(message);
      return;
    }

    String messageId = message.getTraceId();
    // CHECKSTYLE.OFF: ThreadPoolCreate
    // 定时器调度：用于延时投递，短生命周期任务无上下文传播需求。
    ScheduledFuture<?> future =
        scheduler.schedule(
            () -> deliverMessage(message, messageId), delayMillis, TimeUnit.MILLISECONDS);
    // CHECKSTYLE.ON: ThreadPoolCreate
    pendingTasks.put(messageId, future);

    log.debug(
        "[DelayedMessage] 延时消息已调度，messageId={}, delay={}ms, publisher={}",
        messageId,
        delayMillis,
        publisher.getClass().getSimpleName());
  }

  @Override
  public boolean cancelDelayed(String messageId) {
    if (messageId == null) {
      return false;
    }
    ScheduledFuture<?> future = pendingTasks.remove(messageId);
    if (future == null) {
      return false;
    }
    boolean cancelled = future.cancel(false);
    if (cancelled) {
      log.info("[DelayedMessage] 延时消息已取消，messageId={}", messageId);
    }
    return cancelled;
  }

  @Override
  public IMessagePublisher getPublisher() {
    return publisher;
  }

  @Override
  public void destroy() {
    close();
  }

  @Override
  public void close() {
    // 取消所有待投递的定时任务
    for (Map.Entry<String, ScheduledFuture<?>> entry : pendingTasks.entrySet()) {
      entry.getValue().cancel(false);
    }
    pendingTasks.clear();
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdownNow();
    }
    log.info("[DelayedMessage] 延时消息发送器已关闭, publisher={}", publisher.getClass().getSimpleName());
  }

  /**
   * 获取待投递的延时消息数量（用于监控）
   *
   * @return 待投递数量
   */
  public int getPendingCount() {
    return pendingTasks.size();
  }

  /** 投递消息到底层 MQ */
  private void deliverMessage(QueueMessage message, String messageId) {
    pendingTasks.remove(messageId);
    try {
      publisher.publish(message);
      log.debug("[DelayedMessage] 延时消息已投递，messageId={}", messageId);
    } catch (Exception e) {
      log.error("[DelayedMessage] 延时消息投递失败，messageId={}", messageId, e);
    }
  }

  /** 创建默认的单线程调度器 */
  private static ScheduledExecutorService createDefaultScheduler() {
    // CHECKSTYLE.OFF: RegexpSinglelineJava|IllegalImport
    // 默认单线程调度器：仅在外部未提供 scheduler 时使用。
    // 生产环境建议通过参数注入以获得统一管理。
    return Executors.newSingleThreadScheduledExecutor(
        r -> new Thread(r, "ydsz-queue-delayed-sender"));
    // CHECKSTYLE.ON: RegexpSinglelineJava|IllegalImport
  }
}
