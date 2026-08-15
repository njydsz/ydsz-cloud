package com.njydsz.common.queue.service;

import java.util.List;

import com.njydsz.common.queue.domain.QueueMessage;

/**
 * 批量消息处理器接口
 *
 * <p>定义批量消费场景下的消息处理回调，一次处理一批消息。
 * 处理整批消息，任意一条消息处理失败时整批视为失败（可配置为部分成功）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * BatchMessageHandler handler = messages -> {
 *     for (QueueMessage msg : messages) {
 *         processSingle(msg);
 *     }
 * };
 * subscriber.subscribeBatchAsync(handler, 20);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FunctionalInterface
public interface BatchMessageHandler {

    /**
     * 处理一批消息
     *
     * @param messages 消息列表，不会为 null
     * @throws Exception 如果处理失败，异常将触发整批消息重试
     */
    void onMessages(List<QueueMessage> messages) throws Exception;
}
