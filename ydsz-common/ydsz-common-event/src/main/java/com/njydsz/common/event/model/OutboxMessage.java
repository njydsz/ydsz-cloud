package com.njydsz.common.event.model;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Outbox 消息实体
 *
 * <p>遵循 Transactional Outbox 模式：业务操作与事件写入在同一数据库事务中完成， 后台轮询器异步将 PENDING 状态的消息投递到消息队列。
 *
 * <p>核心字段：
 *
 * <ul>
 *   <li>{@code id} - 雪花 ID，全局唯一
 *   <li>{@code aggregateType} - 聚合根类型（如 "Order"）
 *   <li>{@code aggregateId} - 聚合根 ID（如订单号）
 *   <li>{@code eventType} - 事件类型（如 "OrderCreated"）
 *   <li>{@code payload} - 事件负载（JSON 字符串，可能为 GZIP 压缩）
 *   <li>{@code status} - 投递状态
 *   <li>{@code retryCount} - 当前重试次数
 *   <li>{@code maxRetries} - 最大重试次数
 *   <li>{@code nextRetryAt} - 下次重试时间（指数退避）
 *   <li>{@code tenantId} - 租户 ID（多租户隔离）
 *   <li>{@code idempotencyKey} - 幂等去重键（下游消费端去重）
 *   <li>{@code schemaVersion} - 事件 schema 版本号（默认 1，用于向前兼容）
 *   <li>{@code compressed} - payload 是否经过 GZIP 压缩（超过 4KB 自动压缩）
 *   <li>{@code traceId} - 链路追踪 ID
 *   <li>{@code createdAt} - 创建时间
 *   <li>{@code updatedAt} - 最后更新时间
 *   <li>{@code sentAt} - 投递成功时间
 *   <li>{@code errorMessage} - 错误信息（保留首部 800 字符 + 尾部 1200 字符）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.01 精简字段：移除 headers/schemaVersion/contentType/priority 四个未验证字段， 移除非约定的 mutable state
 *     方法（markAsProcessing/Sent/Failed）和 fromDraft 工厂方法， 实体回归纯 POJO + Builder 模式
 * @since 26.09.19 E-2 字段对齐：deduplicationId → idempotencyKey，对齐 DDL 列名 idempotency_key
 * @since 26.09.19 O-4 增加 schemaVersion 字段（默认 1），用于事件 schema 向前兼容
 * @since 26.09.19 P3 增加 compressed 字段，标记 payload 是否 GZIP 压缩存储
 * @since 26.09.29 P0 Outbox 统一（YDIZ-EVENT-001）：新增 {@code topic}（事件路由主题）和 {@code extInfo}（业务扩展 JSON）。
 *     业务模块差异化字段统一进 {@code extInfo}（JSON 格式），避免在 {@code OutboxMessage} 频繁增加列。
 *     topic 用于事件通道路由（webhook / metrics / audit 等），由 {@link com.njydsz.common.event.gateway.EventChannelRegistry} 根据此字段分发。
 */
@Getter
@Builder
@ToString
public class OutboxMessage {

  /** 主键 ID（雪花 ID，由 IdGenerator 生成，禁止自增主键） */
  private final String id;

  /** 聚合根 ID（如订单号） */
  private final String aggregateId;

  /** 聚合根类型（如 "Order"） */
  private final String aggregateType;

  /** 事件类型（如 "OrderCreated"），FQN 限定名（YDIZ-CODE-006：代码体禁止 FQN，但 eventType 内部字符串不受此限） */
  private final String eventType;

  /** 事件路由主题（可选，如 "webhook"/"metrics"/"audit"），由 EventChannelRegistry 按 topic 分发到不同订阅者 */
  private final String topic;

  /** 事件负载（JSON 字符串，compressed=true 时为 GZIP 压缩数据 Base64 编码） */
  private final String payload;

  /** 业务扩展信息（JSON 字符串，用于携带各业务差异化属性，避免 OutboxMessage 频繁加列） */
  private final String extInfo;

  /** 投递状态 */
  private final OutboxStatus status;

  /** 重试次数 */
  private final long retryCount;

  /** 最大重试次数 */
  private final long maxRetries;

  /** 下次重试时间（指数退避） */
  private final Instant nextRetryAt;

  /** 创建时间 */
  private final Instant createdAt;

  /** 最后更新时间 */
  private final Instant updatedAt;

  /** 投递成功时间 */
  private final Instant sentAt;

  /** 错误信息（最后一次失败的异常消息，保留首部+尾部各 800 字符，中间截断标注） */
  private final String errorMessage;

  /** 租户 ID（多租户隔离） */
  private final String tenantId;

  /** 幂等去重键（下游消费端去重），对齐 DDL 列名 idempotency_key */
  private final String idempotencyKey;

  /** 事件 schema 版本号（默认 1，用于向前兼容） */
  private final int schemaVersion;

  /** payload 是否经过 GZIP 压缩（超过 4KB 自动压缩存储） */
  private final boolean isCompressed;

  /** 链路追踪 ID */
  private final String traceId;
}
