package com.njydsz.pmis.common.notify.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.notify.enums.NotifyChannel;

/**
 * 内存死信队列实现（P0-2）
 *
 * <p>当 Redis 不可用时的降级方案。消息存储在内存队列中，
 * 服务重启后丢失。生产环境建议使用 Redis 持久化实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class InMemoryDeadLetterHandler implements DeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDeadLetterHandler.class);

    private static final int MAX_DLQ_SIZE = 10000;

    private final ConcurrentLinkedQueue<DeadLetterEntry> deadLetterQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger(0);

    @Override
    public void moveToDeadLetter(NotifyChannel channel, String receiver, String title,
                                 String content, int failedAttempts, String lastError) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        DeadLetterEntry entry = new DeadLetterEntry(
                messageId, channel, receiver, title, content,
                failedAttempts, lastError, System.currentTimeMillis());

        if (count.get() >= MAX_DLQ_SIZE) {
            deadLetterQueue.poll();
            count.decrementAndGet();
            log.warn("[InMemoryDeadLetterHandler] 死信队列已满，丢弃最旧的消息");
        }

        deadLetterQueue.offer(entry);
        count.incrementAndGet();
        log.error("[InMemoryDeadLetterHandler] 消息移入死信队列: messageId={}, channel={}, receiver={}, attempts={}, error={}",
                messageId, channel.getName(), receiver, failedAttempts, lastError);
    }

    @Override
    public List<DeadLetterEntry> getDeadLetters(int maxCount) {
        int limit = maxCount > 0 ? maxCount : 100;
        List<DeadLetterEntry> result = new ArrayList<>();
        for (DeadLetterEntry entry : deadLetterQueue) {
            if (result.size() >= limit) {
                break;
            }
            result.add(entry);
        }
        return result;
    }

    @Override
    public boolean retryDeadLetter(String messageId) {
        DeadLetterEntry toRemove = null;
        for (DeadLetterEntry entry : deadLetterQueue) {
            if (entry.getMessageId().equals(messageId)) {
                toRemove = entry;
                break;
            }
        }
        if (toRemove != null) {
            deadLetterQueue.remove(toRemove);
            count.decrementAndGet();
            log.info("[InMemoryDeadLetterHandler] 死信消息已移除等待重试: messageId={}", messageId);
            return true;
        }
        return false;
    }

    @Override
    public int getDeadLetterCount() {
        return count.get();
    }
}
