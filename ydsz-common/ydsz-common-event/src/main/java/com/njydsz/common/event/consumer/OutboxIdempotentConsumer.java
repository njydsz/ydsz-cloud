package com.njydsz.common.event.consumer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Outbox 幂等消费注解（F-3）
 *
 * <p>标注在 OutboxMessage 事件监听方法上，通过 Redis / DB 实现消费去重：
 *
 * <ul>
 *   <li>首次消费：执行方法体，记录消费标记
 *   <li>重复消费：检测到已处理，跳过方法体（记录 DEBUG 日志）
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;Service
 * public class FlowEventListener {
 *
 *     &#64;Async
 *     &#64;EventListener(condition = "#message.eventType == T(com.njydsz.common.event.api.DomainEventTypes).FLOW_INSTANCE_APPROVED")
 *     &#64;OutboxIdempotentConsumer(idempotencyKey = "#message.eventId", expireSeconds = 86400)
 *     public void onFlowApproved(OutboxMessage message) {
 *         // 如重复消费则不会执行此方法体
 *         flowService.updateStatus(message.getAggregateId(), FlowStatus.APPROVED);
 *     }
 * }
 * }</pre>
 *
 * <p><b>幂等保证：</b>
 *
 * <ul>
 *   <li>通过 SpEL 表达式 {@link #idempotencyKey()} 解析唯一键（如 {@code #message.eventId}）
 *   *   <li>消费标记存储在 Redis（需引入 ydsz-common-redis），SETNX + TTL 实现滑动去重窗口
 *   <li>Redis 不可用时降级为 JVM 本地 ConcurrentHashMap（单实例有效，多实例需外部存储）
 * </ul>
 *
 * <p><b>编码规范遵循：</b>YDIZ-API-001（版本通过 Header 协商，不耦合路径）
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OutboxIdempotentConsumer {

  /**
   * SpEL 表达式，从方法参数中解析唯一幂等键
   *
   * <p>建议使用 OutboxMessage 的 eventId 或 idempotencyKey 字段。 SpEL 变量：
   *
   * <ul>
   *   <li>{@code #message} - 方法第一个参数（OutboxMessage 类型）
   *   <li>{@code #message.eventId} - 事件 ID
   *   <li>{@code #message.idempotencyKey} - 幂等去重键
   * </ul>
   *
   * @return SpEL 表达式
   */
  String idempotencyKey();

  /**
   * 幂等标记过期时间（秒），默认 86400（24 小时）
   *
   * <p>过期后允许重新消费（适用于消息重放场景）。 建议设置为业务最大处理延迟的 2 倍以上。
   *
   * @return 过期秒数
   */
  int expireSeconds() default 86400;

  /**
   * 消费失败后是否移除幂等标记（允许重试）
   *
   * <p>true：消费失败时删除标记，下次消息到达时可重试。 false：消费失败时保留标记（防止重复执行失败操作）。
   *
   * @return true 表示失败时移除标记
   */
  boolean removeOnFailure() default true;
}
