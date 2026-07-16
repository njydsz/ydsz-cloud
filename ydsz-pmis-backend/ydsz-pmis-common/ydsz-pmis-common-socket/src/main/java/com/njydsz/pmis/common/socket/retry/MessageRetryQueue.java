package com.njydsz.pmis.common.socket.retry;

import java.util.List;

/**
 * 消息重试队列接口（P0-4）。
 *
 * <p>对推送失败的消息进行重试管理，支持：
 * <ul>
 *   <li>入队：推送失败时将消息加入重试队列</li>
 *   <li>出队：定时拉取到期重试的消息</li>
 *   <li>死信：超过最大重试次数的消息移入死信队列</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MessageRetryQueue {

    /**
     * 将推送失败的消息加入重试队列。
     *
     * @param message 待重试的消息
     */
    void enqueue(RetryableMessage message);

    /**
     * 拉取到期重试的消息列表。
     *
     * @param maxCount 最大拉取数量
     * @return 到期重试的消息列表
     */
    List<RetryableMessage> dequeueExpired(int maxCount);

    /**
     * 标记消息重试成功，从队列移除。
     *
     * @param messageId 消息 ID
     */
    void markSuccess(String messageId);

    /**
     * 标记消息重试失败，增加重试计数。
     *
     * @param messageId 消息 ID
     */
    void markFailed(String messageId);

    /**
     * 获取当前待重试消息数量。
     *
     * @return 待重试消息数
     */
    long getPendingCount();
}
