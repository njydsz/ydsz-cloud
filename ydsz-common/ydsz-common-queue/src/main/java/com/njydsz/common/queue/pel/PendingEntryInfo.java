package com.njydsz.common.queue.pel;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis Stream Pending Entry 信息
 *
 * <p>记录消费组中 Pending 消息的元数据，用于 PEL 清理决策。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingEntryInfo {

    /**
     * 消息 ID（Stream Entry ID）
     */
    private String entryId;

    /**
     * 消费者名称
     */
    private String consumerName;

    /**
     * 消息被读取后经过的毫秒数（idle time）
     */
    private long idleTimeMillis;

    /**
     * 消息被投递的次数
     */
    private int deliveryCount;

    /**
     * 消息首次被投递的时间
     */
    private LocalDateTime firstDeliveryTime;

    /**
     * 消息体摘要（前 100 字符）
     */
    private String payloadSummary;
}
