package com.njydsz.common.queue.group;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消费者组事件
 *
 * <p>记录 Redis Stream 消费组中消费者变化的事件信息，
 * 包括消费者ID、事件类型、触发时间等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerGroupEvent {

    /**
     * 消费组名称
     */
    private String groupName;

    /**
     * Stream Key（频道名称）
     */
    private String channel;

    /**
     * 消费者名称
     */
    private String consumerName;

    /**
     * 事件类型
     */
    private EventType eventType;

    /**
     * 当前消费组中的消费者总数
     */
    private int totalConsumers;

    /**
     * 事件触发时间
     */
    private LocalDateTime timestamp;

    /**
     * 消费者组事件类型
     */
    public enum EventType {
        /**
         * 消费者加入消费组
         */
        CONSUMER_ADDED,

        /**
         * 消费者离开消费组（超时/宕机移除）
         */
        CONSUMER_REMOVED,

        /**
         * 分区重新分配完成
         */
        REBALANCE_COMPLETED,

        /**
         * 消费者组创建
         */
        GROUP_CREATED,

        /**
         * 消费者组销毁
         */
        GROUP_DESTROYED
    }
}
