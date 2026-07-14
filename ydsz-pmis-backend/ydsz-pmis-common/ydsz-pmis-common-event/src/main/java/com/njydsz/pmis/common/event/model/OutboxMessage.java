package com.njydsz.pmis.common.event.model;

import java.time.Instant;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Outbox 消息实体
 *
 * <p>遵循 Transactional Outbox 模式：业务操作与事件写入在同一数据库事务中完成，
 * 后台轮询器异步将 PENDING 状态的消息投递到消息队列。
 *
 * <p>核心字段：
 * <ul>
 *   <li>{@code id} - 雪花 ID / UUID，全局唯一</li>
 *   <li>{@code aggregateId} - 聚合根 ID（如订单号）</li>
 *   <li>{@code aggregateType} - 聚合根类型（如 "Order"）</li>
 *   <li>{@code eventType} - 事件类型（如 "OrderCreated"）</li>
 *   <li>{@code payload} - 事件负载（JSON 字符串）</li>
 *   <li>{@code headers} - 扩展头（可选，用于路由 / 追踪）</li>
 *   <li>{@code status} - 投递状态</li>
 *   <li>{@code retryCount} - 重试次数</li>
 *   <li>{@code nextRetryAt} - 下次重试时间（指数退避）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
@Builder
@ToString
public class OutboxMessage {

    /** 主键 ID */
    private final String id;

    /** 聚合根 ID */
    private final String aggregateId;

    /** 聚合根类型 */
    private final String aggregateType;

    /** 事件类型 */
    private final String eventType;

    /** 事件负载（JSON） */
    private final String payload;

    /** 扩展头 */
    private final Map<String, String> headers;

    /** 投递状态 */
    private OutboxStatus status;

    /** 重试次数 */
    private int retryCount;

    /** 最大重试次数 */
    private final int maxRetries;

    /** 下次重试时间 */
    private Instant nextRetryAt;

    /** 创建时间 */
    private final Instant createdAt;

    /** 最后更新时间 */
    private Instant updatedAt;

    /** 投递成功时间 */
    private Instant sentAt;

    /** 错误信息（最后一次失败的异常消息） */
    private String errorMessage;

    /**
     * 标记为已投递成功
     */
    public void markAsSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
        this.updatedAt = Instant.now();
        this.errorMessage = null;
    }

    /**
     * 标记为投递失败，增加重试计数
     *
     * @param errorMessage 错误信息
     * @param backoffSeconds 退避秒数
     */
    public void markAsFailed(String errorMessage, long backoffSeconds) {
        this.retryCount++;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
        this.nextRetryAt = Instant.now().plusSeconds(backoffSeconds);

        if (this.retryCount >= this.maxRetries) {
            this.status = OutboxStatus.DEAD_LETTER;
        } else {
            this.status = OutboxStatus.PENDING;
        }
    }
}
