package com.njydsz.cronjob.server.core.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.entity.outbox.OutboxEvent;
import com.njydsz.cronjob.domain.repository.outbox.OutboxEventRepository;

/**
 * Outbox 事件发布器（P0-2：事务性 Outbox 事件模式）。
 *
 * <p>扫描 {@code ydsz_job_outbox} 表中待发布的事件，根据 {@link OutboxEvent#getTopic()} 路由到对应的
 * 订阅者（WebHook / Metrics / Audit），发布成功后标记为 PUBLISHED，失败则递增重试计数。
 *
 * <h3>投递语义</h3>
 *
 * <ul>
 *   <li><b>至少一次</b>：事件发布后若消费者 ack 失败，下次扫描会重试
 *   <li><b>幂等去重</b>：消费者应基于 {@code eventKey} 做幂等处理
 *   <li><b>指数退避</b>：重试间隔 1s / 5s / 25s（3 次后标记 DEAD）
 * </ul>
 *
 * <h3>对标</h3>
 *
 * <p>对标 Debezium Outbox Pattern、Eventuate Tram、Axon Framework 的 Event Bus。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OutboxPublisher {

  private final OutboxEventRepository outboxEventRepository;

  /** 最大重试次数（3 次，对应退避 1s / 5s / 25s） */
  private static final int MAX_RETRY = 3;

  /** 每批最多处理事件数 */
  private static final int BATCH_SIZE = 100;

  /** 事件发布保留天数（超过此天数的 PUBLISHED 事件自动清理） */
  private static final int PUBLISHED_RETAIN_DAYS = 7;

  /** 订阅者列表（按 topic 过滤） */
  private final List<Consumer<OutboxEvent>> subscribers;

  /**
   * 执行一次事件发布扫描。
   *
   * <p>查询待发布事件，路由到对应订阅者，处理成功后标记 PUBLISHED，失败则递增重试。
   */
  public void publishPending() {
    List<OutboxEvent> pendingEvents =
        outboxEventRepository.findPending(LocalDateTime.now(), MAX_RETRY, BATCH_SIZE);
    if (pendingEvents.isEmpty()) {
      return;
    }
    log.debug("[OutboxPublisher] 扫描到 {} 个待发布事件", pendingEvents.size());

    int published = 0;
    int failed = 0;
    int dead = 0;
    for (OutboxEvent event : pendingEvents) {
      PublishResult result = publishSingle(event);
      switch (result) {
        case SUCCESS -> published++;
        case RETRYABLE_FAILURE -> failed++;
        case EXHAUSTED -> dead++;
      }
    }
    if (published + failed + dead > 0) {
      log.info("[OutboxPublisher] 发布完成: total={} published={} failed={} dead={}",
          pendingEvents.size(), published, failed, dead);
    }
  }

  /**
   * 发布单个事件。
   *
   * @param event 待发布事件
   * @return 发布结果
   */
  private PublishResult publishSingle(OutboxEvent event) {
    // 查找匹配的订阅者（按 topic 过滤）
    Consumer<OutboxEvent> matchedSubscriber = subscribers.stream()
        .filter(sub -> supportsTopic(sub, event.getTopic()))
        .findFirst()
        .orElse(null);

    if (matchedSubscriber == null) {
      log.warn("[OutboxPublisher] 无订阅者, topic={} eventKey={}", event.getTopic(), event.getEventKey());
      // 无订阅者时直接标记已发布（不重试）
      outboxEventRepository.markPublished(event.getId());
      return PublishResult.SUCCESS;
    }

    try {
      matchedSubscriber.accept(event);
      outboxEventRepository.markPublished(event.getId());
      return PublishResult.SUCCESS;
    } catch (Exception e) {
      log.warn("[OutboxPublisher] 发布失败: eventKey={} topic={} retry={} reason={}",
          event.getEventKey(), event.getTopic(), event.getRetryCount(), e.getMessage());
      return handleFailure(event);
    }
  }

  /**
   * 判断订阅者是否支持指定 topic。
   *
   * <p>通过订阅者的类名约定判断：类名包含 "Webhook" 则支持 "webhook" topic，包含 "Metrics" 则支持 "metrics" topic，以此类推。
   *
   * @param subscriber 订阅者
   * @param topic      主题
   * @return true 表示支持
   */
  private boolean supportsTopic(Consumer<OutboxEvent> subscriber, String topic) {
    String className = subscriber.getClass().getSimpleName().toLowerCase();
    return className.contains(topic.toLowerCase());
  }

  /**
   * 处理发布失败：递增重试或标记死亡信。
   *
   * @param event 失败的事件
   * @return 发布结果
   */
  private PublishResult handleFailure(OutboxEvent event) {
    int currentRetry = event.getRetryCount() != null ? event.getRetryCount() : 0;
    if (currentRetry >= MAX_RETRY - 1) {
      // 重试耗尽，标记死亡信（需要人工介入）
      outboxEventRepository.markDead(event.getId());
      log.error("[OutboxPublisher] 事件重试耗尽, 标记 DEAD: eventKey={} topic={}",
          event.getEventKey(), event.getTopic());
      return PublishResult.EXHAUSTED;
    }
    // 指数退避：1s, 5s, 25s
    long backoffSeconds = (long) Math.pow(5, currentRetry);
    LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(backoffSeconds);
    outboxEventRepository.incrementRetry(event.getId(), nextRetry);
    return PublishResult.RETRYABLE_FAILURE;
  }

  /**
   * 清理已发布的历史事件。
   *
   * <p>应在低峰期调用（如每天凌晨），删除超过保留天数的事件。
   */
  public void cleanupPublishedEvents() {
    LocalDateTime threshold = LocalDateTime.now().minusDays(PUBLISHED_RETAIN_DAYS);
    int deleted = outboxEventRepository.deletePublishedBefore(threshold);
    if (deleted > 0) {
      log.info("[OutboxPublisher] 清理已发布事件: count={} before={}", deleted, threshold);
    }
  }

  /** 发布结果枚举。 */
  private enum PublishResult {
    /** 发布成功 */
    SUCCESS,
    /** 可重试失败 */
    RETRYABLE_FAILURE,
    /** 重试耗尽（死亡信） */
    EXHAUSTED
  }
}
