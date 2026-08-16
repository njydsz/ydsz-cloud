package com.njydsz.common.event.model;

import java.time.Instant;
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
 *   <li>{@code id} - 雪花 ID，全局唯一</li>
 *   <li>{@code aggregateType} - 聚合根类型（如 "Order"）</li>
 *   <li>{@code aggregateId} - 聚合根 ID（如订单号）</li>
 *   <li>{@code eventType} - 事件类型（如 "OrderCreated"）</li>
 *   <li>{@code payload} - 事件负载（JSON 字符串）</li>
 *   <li>{@code status} - 投递状态</li>
 *   <li>{@code retryCount} - 当前重试次数</li>
 *   <li>{@code maxRetries} - 最大重试次数</li>
 *   <li>{@code nextRetryAt} - 下次重试时间（指数退避）</li>
 *   <li>{@code tenantId} - 租户 ID（多租户隔离）</li>
 *   <li>{@code deduplicationId} - 幂等去重 ID</li>
 *   <li>{@code traceId} - 链路追踪 ID</li>
 *   <li>{@code createdAt} - 创建时间</li>
 *   <li>{@code updatedAt} - 最后更新时间</li>
 *   <li>{@code sentAt} - 投递成功时间</li>
 *   <li>{@code errorMessage} - 错误信息</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.7.0 精简字段：移除 headers/schemaVersion/contentType/priority 四个未验证字段，
 *              移除非约定的 mutable state 方法（markAsProcessing/Sent/Failed）和 fromDraft 工厂方法，
 *              实体回归纯 POJO + Builder 模式
 */
@Getter
@Builder
@ToString
public class OutboxMessage {

    /** 主键 ID（雪花 ID） */
    private final String id;

    /** 聚合根 ID（如订单号） */
    private final String aggregateId;

    /** 聚合根类型（如 "Order"） */
    private final String aggregateType;

    /** 事件类型（如 "OrderCreated"） */
    private final String eventType;

    /** 事件负载（JSON 字符串） */
    private final String payload;

    /** 投递状态 */
    private final OutboxStatus status;

    /** 重试次数 */
    private final int retryCount;

    /** 最大重试次数 */
    private final int maxRetries;

    /** 下次重试时间（指数退避） */
    private final Instant nextRetryAt;

    /** 创建时间 */
    private final Instant createdAt;

    /** 最后更新时间 */
    private final Instant updatedAt;

    /** 投递成功时间 */
    private final Instant sentAt;

    /** 错误信息（最后一次失败的异常消息） */
    private final String errorMessage;

    /** 租户 ID（多租户隔离） */
    private final String tenantId;

    /** 幂等去重 ID（下游消费端去重） */
    private final String deduplicationId;

    /** 链路追踪 ID */
    private final String traceId;
}
