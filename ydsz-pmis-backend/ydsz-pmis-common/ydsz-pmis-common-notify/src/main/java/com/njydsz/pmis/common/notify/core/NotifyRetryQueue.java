package com.njydsz.pmis.common.notify.core;

import com.njydsz.pmis.common.notify.enums.NotifyChannel;

/**
 * 通知发送失败重试队列接�?
 *
 * <p>当通知发送失败时，将消息加入重试队列，采用指数退避策略进行重试�?
 * 支持设置最大重试次数和队列容量，超过最大重试次数的消息将标记为永久失败�?/p>
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
public interface NotifyRetryQueue {

    /**
     * 将失败消息加入重试队�?
     *
     * @param channel  通知渠道
     * @param receiver 接收�?
     * @param title    消息标题
     * @param content  消息内容
     * @param lastError 最后错误信�?
     */
    void offer(NotifyChannel channel, String receiver, String title,
               String content, String lastError);

    /**
     * 执行单条重试
     *
     * @param notifyService 通知服务
     */
    void retry(NotifyService notifyService);

    /**
     * 批量执行重试
     *
     * @param notifyService 通知服务
     * @param maxBatchSize  批量处理的最大消息数
     * @return 本次处理的消息数�?
     */
    int retryBatch(NotifyService notifyService, int maxBatchSize);

    /**
     * 使用默认批量大小执行批量重试
     *
     * @param notifyService 通知服务
     * @return 本次处理的消息数�?
     */
    int retryBatch(NotifyService notifyService);

    /** 当前队列中待处理消息数量 */
    int getQueueSize();

    /** 历史入队总计�?*/
    int getQueuedCount();

    /** 永久失败消息计数 */
    int getPermanentFailCount();

    /** 丢弃消息计数 */
    int getDroppedCount();

    /** 队列容量 */
    int getCapacity();

    /** 默认批量大小 */
    int getBatchSize();
}
