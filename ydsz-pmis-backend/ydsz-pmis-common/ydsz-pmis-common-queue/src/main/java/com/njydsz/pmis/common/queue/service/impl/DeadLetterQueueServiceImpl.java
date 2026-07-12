package com.njydsz.pmis.common.queue.service.impl;

import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.queue.config.QueueProperties;
import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.service.DeadLetterQueueService;
import com.njydsz.pmis.common.queue.service.IMessagePublisher;
import com.njydsz.pmis.common.queue.queue.IMessageQueueProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 死信队列服务实现类
 * <p>基于 Redis Hash 存储死信消息，提供重试、查询和统计能力
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class DeadLetterQueueServiceImpl implements DeadLetterQueueService {

    private static final String DLQ_KEY_PREFIX = "remi:queue:dlq:";
    private static final String DLQ_RETRY_KEY_PREFIX = "remi:queue:dlq:retry:";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final RedisTemplate<String, Object> redisTemplate;
    private final IMessageQueueProvider queueProvider;
    private final QueueProperties queueProperties;

    public DeadLetterQueueServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                      IMessageQueueProvider queueProvider,
                                      QueueProperties queueProperties) {
        this.redisTemplate = redisTemplate;
        this.queueProvider = queueProvider;
        this.queueProperties = queueProperties;
    }

    @Override
    public void sendToDeadLetter(String topic, String messageId, String messageBody, String failureReason) {
        String dlqKey = DLQ_KEY_PREFIX + topic;
        String retryKey = DLQ_RETRY_KEY_PREFIX + topic;

        DeadLetterMessage dlqMessage = new DeadLetterMessage();
        dlqMessage.setMessageId(messageId);
        dlqMessage.setMessageBody(messageBody);
        dlqMessage.setFailureReason(failureReason);
        dlqMessage.setEnterTime(LocalDateTime.now().format(FORMATTER));
        dlqMessage.setRetryCount(0);

        String dlqMessageJson = JsonUtils.toJson(dlqMessage);
        redisTemplate.opsForHash().put(dlqKey, messageId, dlqMessageJson);
        redisTemplate.opsForHash().put(retryKey, messageId, "0");

        // 注意：不设置整个 Hash key 的 TTL，因为每次新增消息会重置 TTL 导致旧消息永不过期。
        // 改为依赖定时任务 cleanExpiredDeadLetters() 清理超过 7 天的单条消息。

        log.info("[DeadLetterQueue] 消息进入死信队列: topic={}, messageId={}, reason={}",
                topic, messageId, failureReason);
    }

    @Override
    public List<String> queryDeadLetters(String topic, int limit) {
        String dlqKey = DLQ_KEY_PREFIX + topic;
        List<Object> values = redisTemplate.opsForHash().values(dlqKey);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
                .limit(limit)
                .map(Object::toString)
                .toList();
    }

    @Override
    public boolean retry(String topic, String messageId) {
        String dlqKey = DLQ_KEY_PREFIX + topic;
        String retryKey = DLQ_RETRY_KEY_PREFIX + topic;

        Object dlqMessageObj = redisTemplate.opsForHash().get(dlqKey, messageId);
        if (dlqMessageObj == null) {
            log.warn("[DeadLetterQueue] 重试失败，消息不存在: topic={}, messageId={}", topic, messageId);
            return false;
        }

        int maxRetries = queueProperties.resolvedDeadLetterMaxRetries();
        int currentRetryCount = getRetryCount(topic, messageId);

        if (currentRetryCount >= maxRetries) {
            log.warn("[DeadLetterQueue] 消息已达到最大重试次数，永久删除: topic={}, messageId={}, retries={}",
                    topic, messageId, currentRetryCount);
            removeDeadLetter(topic, messageId);
            return false;
        }

        DeadLetterMessage dlqMessage = JsonUtils.fromJson(dlqMessageObj.toString(), DeadLetterMessage.class);
        QueueMessage queueMessage = QueueMessage.fromPayload(dlqMessage.getMessageBody());
        if (queueMessage != null) {
            queueMessage.setRetryCount(currentRetryCount + 1);
        }

        try {
            IMessagePublisher publisher = queueProvider.createMessageQueue(queueProperties.resolvedType())
                    .createPublisher(topic);
            publisher.publish(queueMessage != null ? queueMessage : QueueMessage.of(dlqMessage.getMessageBody()));

            // 重试成功后从死信队列彻底移除（不再保留 retryCount）
            redisTemplate.opsForHash().delete(dlqKey, messageId);
            redisTemplate.opsForHash().delete(retryKey, messageId);

            log.info("[DeadLetterQueue] 消息重试成功: topic={}, messageId={}, retryCount={}",
                    topic, messageId, currentRetryCount + 1);
            return true;
        } catch (Exception e) {
            // 重试失败：更新 retryCount，保留在死信队列中等待下次重试
            redisTemplate.opsForHash().put(retryKey, messageId, String.valueOf(currentRetryCount + 1));
            log.error("[DeadLetterQueue] 消息重试失败: topic={}, messageId={}, error={}",
                    topic, messageId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 清理过期的死信消息。
     *
     * <p>遍历所有死信队列，删除进入时间超过 7 天的消息。
     * 应由定时任务（如 DeadLetterRetryScheduler）定期调用。
     *
     * @return 清理的消息数量
     */
    public int cleanExpiredDeadLetters() {
        int cleanedCount = 0;
        long maxAgeMillis = TimeUnit.DAYS.toMillis(7);
        long now = System.currentTimeMillis();
        DateTimeFormatter formatter = FORMATTER;

        ScanOptions scanOptions = ScanOptions.scanOptions().match(DLQ_KEY_PREFIX + "*").count(100).build();
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

                for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                    String messageId = entry.getKey().toString();
                    try {
                        DeadLetterMessage msg = JsonUtils.fromJson(entry.getValue().toString(), DeadLetterMessage.class);
                        if (msg != null && msg.getEnterTime() != null) {
                            LocalDateTime enterTime = LocalDateTime.parse(msg.getEnterTime(), formatter);
                            long ageMillis = now - enterTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                            if (ageMillis > maxAgeMillis) {
                                redisTemplate.opsForHash().delete(dlqKey, messageId);
                                redisTemplate.opsForHash().delete(DLQ_RETRY_KEY_PREFIX + topic, messageId);
                                cleanedCount++;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[DeadLetterQueue] 清理过期消息解析失败: topic={}, messageId={}", topic, messageId);
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
        ScanOptions scanOptions = ScanOptions.scanOptions().match(DLQ_KEY_PREFIX + "*").count(100).build();
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

    /**
     * 从死信队列中移除指定消息
     *
     * @param topic     主题
     * @param messageId 消息 ID
     */
    private void removeDeadLetter(String topic, String messageId) {
        String dlqKey = DLQ_KEY_PREFIX + topic;
        String retryKey = DLQ_RETRY_KEY_PREFIX + topic;
        redisTemplate.opsForHash().delete(dlqKey, messageId);
        redisTemplate.opsForHash().delete(retryKey, messageId);
        log.info("[DeadLetterQueue] 消息已从死信队列永久删除: topic={}, messageId={}", topic, messageId);
    }

    /**
     * 死信消息内部模型
     */
    @SuppressWarnings("unused")
    private static class DeadLetterMessage {
        private String messageId;
        private String messageBody;
        private String failureReason;
        private String enterTime;
        private int retryCount;

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public String getMessageBody() {
            return messageBody;
        }

        public void setMessageBody(String messageBody) {
            this.messageBody = messageBody;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public void setFailureReason(String failureReason) {
            this.failureReason = failureReason;
        }

        public String getEnterTime() {
            return enterTime;
        }

        public void setEnterTime(String enterTime) {
            this.enterTime = enterTime;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }
    }
}
