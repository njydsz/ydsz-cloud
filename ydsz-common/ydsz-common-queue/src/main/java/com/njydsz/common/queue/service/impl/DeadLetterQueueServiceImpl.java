package com.njydsz.common.queue.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.scheduler.DeadLetterEntry;
import com.njydsz.common.queue.scheduler.DeadLetterReplayer;
import com.njydsz.common.queue.scheduler.DeadLetterReplayerRegistry;
import com.njydsz.common.queue.service.DeadLetterQueueService;
import com.njydsz.common.queue.service.IMessagePublisher;

/**
 * Redis 死信队列服务实现
 *
 * <p>封装 Redis Stream + ZSet 实现的死信队列：消息多次重试失败后入队。
 *
 * <p>重试时优先尝试引擎级回放器（Kafka seek / RocketMQ setConsumeTimestamp），无匹配时降级为"重新发布到 topic 末尾"。
 *
 * <p>供后台调度消费做告警、人工干预、归档等处理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class DeadLetterQueueServiceImpl implements DeadLetterQueueService {

  private static final String DLQ_KEY_PREFIX = "ydsz:queue:dlq:";
  private static final String DLQ_RETRY_KEY_PREFIX = "ydsz:queue:dlq:retry:";
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  private final RedisTemplate<String, Object> redisTemplate;
  private final IMessageQueueProvider queueProvider;
  private final QueueProperties queueProperties;
  private final DeadLetterReplayerRegistry replayerRegistry;

  public DeadLetterQueueServiceImpl(
      RedisTemplate<String, Object> redisTemplate,
      IMessageQueueProvider queueProvider,
      QueueProperties queueProperties,
      DeadLetterReplayerRegistry replayerRegistry) {
    this.redisTemplate = redisTemplate;
    this.queueProvider = queueProvider;
    this.queueProperties = queueProperties;
    this.replayerRegistry = replayerRegistry;
  }

  @Override
  public void sendToDeadLetter(
      String topic, String messageId, String messageBody, String failureReason) {
    String dlqKey = DLQ_KEY_PREFIX + topic;
    String retryKey = DLQ_RETRY_KEY_PREFIX + topic;

    DeadLetterEntry entry = new DeadLetterEntry();
    entry.setMessageId(messageId);
    entry.setMessageBody(messageBody);
    entry.setFailureReason(failureReason != null ? failureReason : "");
    entry.setEnterTime(LocalDateTime.now().format(FORMATTER));
    entry.setRetryCount(0);
    entry.setTopic(topic);
    entry.setEngineType(resolveEngineTypeForTopic());

    String dlqMessageJson = YdszJson.toJson(entry);
    redisTemplate.opsForHash().put(dlqKey, messageId, dlqMessageJson);
    redisTemplate.opsForHash().put(retryKey, messageId, "0");

    // 注意：不设置整个 Hash key 的 TTL，因为每次新增消息会重置 TTL 导致旧消息永不过期。
    // 改为依赖定时任务 cleanExpiredDeadLetters() 清理超过 7 天的单条消息。

    log.info(
        "[DeadLetterQueue] 消息进入死信队列: topic={}, messageId={}, engineType={}, reason={}",
        topic,
        messageId,
        entry.getEngineType(),
        entry.getFailureReason());
  }

  @Override
  public List<String> queryDeadLetters(String topic, int limit) {
    String dlqKey = DLQ_KEY_PREFIX + topic;
    List<Object> values = redisTemplate.opsForHash().values(dlqKey);
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return values.stream().limit(limit).map(Object::toString).toList();
  }

  @Override
  public boolean retry(String topic, String messageId) {
    String dlqKey = DLQ_KEY_PREFIX + topic;

    Object dlqMessageObj = redisTemplate.opsForHash().get(dlqKey, messageId);
    if (dlqMessageObj == null) {
      log.warn("[DeadLetterQueue] 重试失败，消息不存在: topic={}, messageId={}", topic, messageId);
      return false;
    }

    DeadLetterEntry entry =
        YdszJson.fromJson(dlqMessageObj.toString(), DeadLetterEntry.class);
    if (entry == null) {
      log.warn("[DeadLetterQueue] 重试失败，消息反序列化失败: topic={}, messageId={}", topic, messageId);
      return false;
    }

    int maxRetries = queueProperties.getDeadLetterMaxRetries();
    int currentRetryCount = getRetryCount(topic, messageId);

    if (currentRetryCount >= maxRetries) {
      log.warn(
          "[DeadLetterQueue] 消息已达到最大重试次数，永久删除: topic={}, messageId={}, retries={}",
          topic,
          messageId,
          currentRetryCount);
      removeDeadLetter(topic, messageId);
      return false;
    }

    // ── 优先尝试引擎级回放器（Kafka seek / RocketMQ setConsumeTimestamp）──
    String engineType = entry.getEngineType();
    DeadLetterReplayer replayer = replayerRegistry.find(engineType);
    if (replayer != null) {
      log.info(
          "[DeadLetterQueue] 使用引擎回放器：replayer={}, topic={}, messageId={}, retryCount={}",
          replayer.name(),
          topic,
          messageId,
          currentRetryCount + 1);
      boolean replayed = false;
      try {
        replayed = replayer.replay(entry);
      } catch (Exception e) {
        log.warn(
            "[DeadLetterQueue] 引擎回放器异常，降级为重新发布: replayer={}, error={}",
            replayer.name(),
            e.getMessage());
        replayed = false;
      }
      if (replayed) {
        // 回放成功：更新计数、保留在队列中供下次续试（引擎会再次消费同一 offset）
        incrementRetryCount(topic, messageId, currentRetryCount);
        return true;
      }
      log.info("[DeadLetterQueue] 引擎回放器返回未处理，降级为重新发布: replayer={}", replayer.name());
    }

    // ── 降级：通用"重新发布到 topic 末尾"语义 ──
    return retryByRepublish(topic, messageId, entry, currentRetryCount);
  }

  /** 通用重新发布的降级处理 */
  private boolean retryByRepublish(
      String topic, String messageId, DeadLetterEntry entry, int currentRetryCount) {
    QueueMessage queueMessage = QueueMessage.fromPayload(entry.getMessageBody());
    if (queueMessage != null) {
      queueMessage.setRetryCount(currentRetryCount + 1);
    }

    try {
      IMessagePublisher publisher =
          queueProvider
              .createMessageQueue(queueProperties.getResolvedType())
              .createPublisher(topic);
      QueueMessage toPublish =
          queueMessage != null ? queueMessage : QueueMessage.of(entry.getMessageBody());
      publisher.publish(toPublish);

      // 重试成功后从死信队列彻底移除（不再保留 retryCount）
      redisTemplate.opsForHash().delete(DLQ_KEY_PREFIX + topic, messageId);
      redisTemplate.opsForHash().delete(DLQ_RETRY_KEY_PREFIX + topic, messageId);

      log.info(
          "[DeadLetterQueue] 消息重新发布重试成功: topic={}, messageId={}, retryCount={}",
          topic,
          messageId,
          currentRetryCount + 1);
      return true;
    } catch (Exception e) {
      // 重试失败：更新 retryCount，保留在死信队列中等待下次重试
      incrementRetryCount(topic, messageId, currentRetryCount);
      log.error(
          "[DeadLetterQueue] 消息重新发布重试失败: topic={}, messageId={}, error={}",
          topic,
          messageId,
          e.getMessage(),
          e);
      return false;
    }
  }

  /** 递增重试计数并持久化 */
  private void incrementRetryCount(String topic, String messageId, int currentRetryCount) {
    redisTemplate.opsForHash().put(DLQ_RETRY_KEY_PREFIX + topic,
        messageId, String.valueOf((long) currentRetryCount + 1));
  }

  /**
   * 清理过期的死信消息。
   *
   * <p>遍历所有死信队列，删除进入时间超过 7 天的消息。 应由定时任务（如 DeadLetterRetryScheduler）定期调用。
   *
   * @return 清理的消息数量
   */
  public int cleanExpiredDeadLetters() {
    int cleanedCount = 0;
    long maxAgeMillis = TimeUnit.DAYS.toMillis(7);
    long now = System.currentTimeMillis();

    ScanOptions scanOptions =
        ScanOptions.scanOptions().match(DLQ_KEY_PREFIX + "*").count(100).build();
    try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
      while (cursor.hasNext()) {
        String dlqKey = cursor.next();
        // 跳过 retry key
        if (dlqKey.startsWith(DLQ_RETRY_KEY_PREFIX)) {
          continue;
        }
        String topic = dlqKey.substring(DLQ_KEY_PREFIX.length());
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(dlqKey);
        if (entries == null || entries.isEmpty()) {
          continue;
        }

        for (Map.Entry<Object, Object> mapEntry : entries.entrySet()) {
          String messageId = mapEntry.getKey().toString();
          try {
            DeadLetterEntry entry =
                YdszJson.fromJson(mapEntry.getValue().toString(), DeadLetterEntry.class);
            if (entry != null && entry.getEnterTime() != null) {
              LocalDateTime enterTime = LocalDateTime.parse(entry.getEnterTime(), FORMATTER);
              long ageMillis =
                  now - enterTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
              if (ageMillis > maxAgeMillis) {
                redisTemplate.opsForHash().delete(dlqKey, messageId);
                redisTemplate.opsForHash().delete(DLQ_RETRY_KEY_PREFIX + topic, messageId);
                cleanedCount++;
              }
            }
          } catch (Exception e) {
            log.debug(
                "[DeadLetterQueue] 清理过期消息解析失败: topic={}, messageId={}", topic, messageId);
          }
        }
      }
    } catch (Exception e) {
      log.error("[DeadLetterQueue] 清理过期死信消息失败: {}", e.getMessage(), e);
    }

    if (cleanedCount > 0) {
      log.info("[DeadLetterQueue] 清理过期死信消息完成: cleanedCount={}", cleanedCount);
    }
    return cleanedCount;
  }

  @Override
  public int retryAll() {
    int successCount = 0;

    // 使用 SCAN 替代 KEYS，避免阻塞 Redis
    ScanOptions scanOptions =
        ScanOptions.scanOptions().match(DLQ_KEY_PREFIX + "*").count(100).build();
    try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
      while (cursor.hasNext()) {
        String dlqKey = cursor.next();
        String topic = dlqKey.substring(DLQ_KEY_PREFIX.length());
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(dlqKey);
        if (entries == null || entries.isEmpty()) {
          continue;
        }

        for (Object messageIdObj : entries.keySet()) {
          String messageId = messageIdObj.toString();
          if (retry(topic, messageId)) {
            successCount++;
          }
        }
      }
    } catch (Exception e) {
      log.error("[DeadLetterQueue] 批量重试失败: {}", e.getMessage(), e);
    }

    log.info("[DeadLetterQueue] 批量重试完成: successCount={}", successCount);
    return successCount;
  }

  @Override
  public int getDeadLetterCount(String topic) {
    String dlqKey = DLQ_KEY_PREFIX + topic;
    Long size = redisTemplate.opsForHash().size(dlqKey);
    return size != null ? size.intValue() : 0;
  }

  @Override
  public int getRetryCount(String topic, String messageId) {
    String retryKey = DLQ_RETRY_KEY_PREFIX + topic;
    Object countObj = redisTemplate.opsForHash().get(retryKey, messageId);
    if (countObj == null) {
      return 0;
    }
    try {
      return Integer.parseInt(countObj.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** 解析当前 topic 对应的目标引擎类型（空格分隔的多值中取第一个匹配值）。 */
  private String resolveEngineTypeForTopic() {
    try {
      QueueType resolvedType = queueProperties.getResolvedType();
      return resolvedType.getValue();
    } catch (Exception e) {
      return null;
    }
  }

  /** 从死信队列中移除指定消息 */
  private void removeDeadLetter(String topic, String messageId) {
    String dlqKey = DLQ_KEY_PREFIX + topic;
    String retryKey = DLQ_RETRY_KEY_PREFIX + topic;
    redisTemplate.opsForHash().delete(dlqKey, messageId);
    redisTemplate.opsForHash().delete(retryKey, messageId);
    log.info("[DeadLetterQueue] 消息已从死信队列永久删除: topic={}, messageId={}", topic, messageId);
  }
}
