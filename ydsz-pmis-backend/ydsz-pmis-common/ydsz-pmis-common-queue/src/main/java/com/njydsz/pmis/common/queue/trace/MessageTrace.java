package com.njydsz.pmis.common.queue.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息轨迹实体
 *
 * <p>记录消息从生产到消费的完整生命周期轨迹，
 * 包含消息ID、主题、生产者/消费者、状态、时间戳等信息。
 *
 * <p><b>状态流转：</b>
 * <pre>{@code
 * SENT -> DELIVERED -> CONSUMED
 *                  \-> FAILED
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 主题/通道名称
     */
    private String topic;

    /**
     * 生产者ID
     */
    private String producerId;

    /**
     * 消费者ID
     */
    private String consumerId;

    /**
     * 消息状态
     * <p>SENT: 已发送, DELIVERED: 已投递, CONSUMED: 已消费, FAILED: 消费失败
     */
    private TraceStatus status;

    /**
     * 各阶段时间戳
     * <p>key: 阶段名称(sent/delivered/consumed/failed), value: 时间戳(毫秒)
     */
    @Builder.Default
    private transient Map<String, Long> timestamps = new HashMap<>();

    /**
     * 关联的链路追踪ID
     */
    private String traceId;

    /**
     * 重试次数
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 失败时的异常信息
     */
    private String errorMessage;

    /**
     * 记录阶段时间戳
     *
     * @param stage 阶段名称
     */
    public void addTimestamp(String stage) {
        if (this.timestamps == null) {
            this.timestamps = new HashMap<>();
        }
        this.timestamps.put(stage, System.currentTimeMillis());
    }

    /**
     * 获取指定阶段的时间戳
     *
     * @param stage 阶段名称
     * @return 时间戳(毫秒)，不存在时返回 null
     */
    public Long getTimestamp(String stage) {
        return this.timestamps != null ? this.timestamps.get(stage) : null;
    }

    /**
     * 消息轨迹状态枚举
     */
    public enum TraceStatus {
        /**
         * 消息已发送
         */
        SENT,

        /**
         * 消息已投递到队列
         */
        DELIVERED,

        /**
         * 消息已被消费
         */
        CONSUMED,

        /**
         * 消费失败
         */
        FAILED
    }
}
