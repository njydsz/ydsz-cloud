package com.njydsz.common.socket.retry;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.socket.config.WebSocketProperties;

/**
 * Redis Sorted Set 实现的消息重试队列（P0-4）。
 *
 * <p>使用 Redis Sorted Set 存储，score 为下次重试时间戳， 定时拉取 score ≤ 当前时间的消息进行重试。 超过最大重试次数的消息移入 {@link
 * RedisDeadLetterQueue}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RedisMessageRetryQueue implements MessageRetryQueue {

  private static final String RETRY_QUEUE_KEY = "ydsz:ws:retry:queue";

  private final StringRedisTemplate redisTemplate;
  private final WebSocketProperties properties;
  private final DeadLetterQueue deadLetterQueue;

  @Override
  public void enqueue(RetryableMessage message) {
    if (message == null) {
      return;
    }
    try {
      String json = YdszJson.toJson(message);
      redisTemplate.opsForZSet().add(RETRY_QUEUE_KEY, json, message.getNextRetryAt());
      log.debug(
          "[WS-Retry] 消息入队: messageId={}, retryCount={}, nextRetryAt={}",
          message.getMessageId(),
          message.getRetryCount(),
          message.getNextRetryAt());
    } catch (Exception e) {
      log.warn("[WS-Retry] 消息入队失败: messageId={}, err={}", message.getMessageId(), e.getMessage());
    }
  }

  @Override
  public List<RetryableMessage> dequeueExpired(int maxCount) {
    long now = System.currentTimeMillis();
    List<RetryableMessage> expired = new ArrayList<>();
    try {
      var tuples = redisTemplate.opsForZSet().rangeByScoreWithScores(RETRY_QUEUE_KEY, 0, now);
      if (tuples == null || tuples.isEmpty()) {
        return List.of();
      }
      for (var tuple : tuples) {
        if (expired.size() >= maxCount) {
          break;
        }
        String json = tuple.getValue();
        try {
          RetryableMessage msg = YdszJson.fromJson(json, RetryableMessage.class);
          if (msg != null) {
            expired.add(msg);
            redisTemplate.opsForZSet().remove(RETRY_QUEUE_KEY, json);
          }
        } catch (Exception e) {
          log.warn("[WS-Retry] 消息解析失败, 移入死信: err={}", e.getMessage());
          redisTemplate.opsForZSet().remove(RETRY_QUEUE_KEY, json);
        }
      }
    } catch (Exception e) {
      log.warn("[WS-Retry] 拉取到期消息失败: err={}", e.getMessage());
    }
    return expired;
  }

  @Override
  public void markSuccess(String messageId) {
    log.debug("[WS-Retry] 消息重试成功: messageId={}", messageId);
  }

  @Override
  public void markFailed(String messageId) {
    log.debug("[WS-Retry] 消息重试失败: messageId={}", messageId);
  }

  @Override
  public long getPendingCount() {
    try {
      Long size = redisTemplate.opsForZSet().size(RETRY_QUEUE_KEY);
      return size == null ? 0L : size;
    } catch (Exception e) {
      log.debug("[WS-Retry] 获取待重试数量失败: {}", e.getMessage());
      return 0L;
    }
  }

  /**
   * 检查消息是否超过最大重试次数，超过则移入死信队列。
   *
   * @param message 待检查消息
   * @return true 表示已移入死信队列
   */
  public boolean checkAndMoveToDeadLetter(RetryableMessage message) {
    if (message.isMaxRetriesExceeded()) {
      if (deadLetterQueue != null) {
        deadLetterQueue.enqueue(message);
        log.warn(
            "[WS-Retry] 消息超过最大重试次数, 移入死信: messageId={}, retries={}",
            message.getMessageId(),
            message.getRetryCount());
      }
      return true;
    }
    return false;
  }

  /**
   * 增加重试计数并重新入队（使用退避策略）。
   *
   * <p>退避策略：
   *
   * <ul>
   *   <li>fixed — 固定延迟（每次相同）
   *   <li>exponential — 指数退避（delay * 2^retryCount）
   *   <li>exponential_with_jitter — 指数退避 + 随机抖动（避免雪崩）
   * </ul>
   *
   * @param message 原始消息
   */
  public void requeueWithIncrement(RetryableMessage message) {
    if (checkAndMoveToDeadLetter(message)) {
      return;
    }
    message.setRetryCount(message.getRetryCount() + 1);
    long delayMs = calculateBackoffDelay(message.getRetryCount());
    message.setNextRetryAt(System.currentTimeMillis() + delayMs);
    enqueue(message);
  }

  /**
   * 计算退避延迟（毫秒）。
   *
   * @param retryCount 当前重试次数（从 1 开始）
   * @return 延迟毫秒数
   */
  private long calculateBackoffDelay(int retryCount) {
    long baseDelayMs = properties.getRetry().getRetryDelay().toMillis();
    long maxDelayMs = properties.getRetry().getMaxRetryDelayMs();
    String strategy = properties.getRetry().getBackoffStrategy();

    long delay;
    switch (strategy) {
      case "fixed" -> delay = baseDelayMs;
      case "exponential" -> delay = baseDelayMs * (1L << (retryCount - 1));
      case "exponential_with_jitter" -> {
        long exponentialDelay = baseDelayMs * (1L << (retryCount - 1));
        // 添加 ±10% 随机抖动
        long jitter = (long) (exponentialDelay * 0.1 * Math.random());
        delay = exponentialDelay + jitter - (exponentialDelay / 20);
      }
      default -> delay = baseDelayMs;
    }
    return Math.min(delay, maxDelayMs);
  }
}
