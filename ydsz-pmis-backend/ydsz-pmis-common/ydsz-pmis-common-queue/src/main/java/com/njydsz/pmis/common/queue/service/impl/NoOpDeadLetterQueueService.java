package com.njydsz.pmis.common.queue.service.impl;

import java.util.List;

import com.njydsz.pmis.common.queue.service.DeadLetterQueueService;

/**
 * No-Op 死信队列服务实现
 *
 * <p>当 RedisService 不可用时使用的空实现，避免返回 null Bean。
 * 所有方法均为空操作，确保依赖注入不会失败。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
public class NoOpDeadLetterQueueService implements DeadLetterQueueService {

    @Override
    public void sendToDeadLetter(String topic, String messageId, String messageBody, String failureReason) {
        // No-op: 死信队列服务不可用
    }

    @Override
    public List<String> queryDeadLetters(String topic, int limit) {
        return List.of();
    }

    @Override
    public boolean retry(String topic, String messageId) {
        return false;
    }

    @Override
    public int retryAll() {
        return 0;
    }

    @Override
    public int getDeadLetterCount(String topic) {
        return 0;
    }

    @Override
    public int getRetryCount(String topic, String messageId) {
        return 0;
    }
}
