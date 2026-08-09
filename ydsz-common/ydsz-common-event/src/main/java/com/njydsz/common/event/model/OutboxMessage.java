package com.njydsz.common.event.model;

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
 *   <li>{@code tenantId} - 租户 ID（多租户隔离）</li>
 *   <li>{@code deduplicationId} - 幂等去重 ID（下游消费端去重）</li>
 *   <li>{@code schemaVersion} - 事件 Schema 版本号（如 "v1.0.0"）</li>
 *   <li>{@code contentType} - 内容类型（如 "application/vnd.ydsz.order.v1+json"）</li>
 *   <li>{@code priority} - 优先级（0-9，9 最高，null 表示未设置由 OutboxService 填充默认值）</li>
 *   <li>{@code traceId} - 链路追踪 ID</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Builder
@ToString
public class OutboxMessage {

    /** 默认优先级 */
    public static final int DEFAULT_PRIORITY = 5;

    /** 主键 ID（UUID） */
    private final String id;

    /** 聚合根 ID（如订单号） */
    private final String aggregateId;

    /** 聚合根类型（如 "Order"） */
    private final String aggregateType;

    /** 事件类型（如 "OrderCreated"） */
    private final String eventType;

    /** 事件负载（JSON 字符串） */
    private final String payload;

    /** 扩展头（可选，用于路由/追踪） */
    private final Map<String, String> headers;

    /** 投递状态 */
    private OutboxStatus status;

    /** 重试次数 */
    private int retryCount;

    /** 最大重试次数 */
    private final int maxRetries;

    /** 下次重试时间（指数退避） */
    private Instant nextRetryAt;

    /** 创建时间 */
    private final Instant createdAt;

    /** 最后更新时间 */
    private Instant updatedAt;

    /** 投递成功时间 */
    private Instant sentAt;

    /** 错误信息（最后一次失败的异常消息） */
    private String errorMessage;

    /** 租户 ID（多租户隔离） */
    private final String tenantId;

    /** 幂等去重 ID（下游消费端去重） */
    private final String deduplicationId;

    /** 事件 Schema 版本号（如 "v1.0.0"） */
    private final String schemaVersion;

    /** 内容类型（如 "application/vnd.ydsz.order.v1+json"） */
    private final String contentType;

    /**
     * 优先级（0-9，9 最高）
     *
     * <p>使用 {@code Integer} 包装类型，{@code null} 表示未设置，
     * 由 {@code OutboxService} 填充配置的默认优先级。
     * 这样可以正确区分"用户显式设置 0"和"未设置"两种情况。
     */
    private final Integer priority;

    /** 链路追踪 ID */
    private final String traceId;

    /**
     * 标记为处理中
     *
     * <p>将状态从 PENDING 改为 PROCESSING，表示已被某个实例 claim，正在投递。
     */
    public void markAsProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    /**
     * 标记为已投递成功
     *
     * <p>设置状态为 SENT，记录投递成功时间，清空错误信息。
     */
    public void markAsSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
        this.updatedAt = Instant.now();
        this.errorMessage = null;
    }

    /**
     * 标记为投递失败，增加重试计数并按指数退避策略设置下次重试时间
     *
     * <p>当 {@code retryCount} 达到 {@code maxRetries} 时，状态流转为 {@link OutboxStatus#DEAD_LETTER}；
     * 否则状态回退为 {@link OutboxStatus#PENDING} 等待下次调度。
     *
     * @param errorMessage   失败错误信息
     * @param backoffSeconds 退避秒数（下次重试时间 = 当前时间 + backoffSeconds）
     */
    public void markAsFailed(String errorMessage, int backoffSeconds) {
        this.retryCount++;
        this.errorMessage = errorMessage;
        this.nextRetryAt = Instant.now().plusSeconds(backoffSeconds);
        this.updatedAt = Instant.now();
        this.status = this.retryCount >= this.maxRetries
                ? OutboxStatus.DEAD_LETTER
                : OutboxStatus.PENDING;
    }
}
