package com.njydsz.pmis.common.queue.queue;

import com.njydsz.pmis.common.queue.enums.QueueType;

/**
 * 消息队列对象工厂
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 * @Date: 2024/1/25 14:23
 */
public interface IMessageQueueProvider extends AutoCloseable {
    IMessageQueue createMessageQueue(QueueType type, String... args);

    @Override
    default void close() {
        // 默认空实现，子类可覆盖
    }
}
