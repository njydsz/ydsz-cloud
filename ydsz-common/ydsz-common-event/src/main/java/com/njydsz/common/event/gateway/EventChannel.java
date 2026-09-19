package com.njydsz.common.event.gateway;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 事件通道注解（O-3）
 *
 * <p>用于声明 DomainEvent 或 Service 类所属的事件通道。多个事件类型可共享同一通道， 通道决定消息路由到哪个 MQ topic / 序列化策略 / QoS 等。
 *
 * <p>使用方式：
 *
 * <ul>
 *   <li>在 DomainEvent 子类上声明：{@code @EventChannel("order-events") public class OrderEvent extends DomainEvent}
 *   <li>在 OutboxService 上声明默认通道：{@code @EventChannel("default") @Service public class TradeOutboxService}
 * </ul>
 *
 * <p><b>设计原则：</b>参考 Spring Cloud Stream 的 {@code @Output}/{@Input} 语义， 但保持为事件模块内部抽象（不依赖 Spring Cloud Stream），
 * 确保事件模块零外部依赖。
 *
 * <p>通道注册表通过 {@link EventChannelRegistry} 统一管理， 在 OutboxService 写入时根据事件类型查找通道配置，将通道标签写入 metadata。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see EventChannelRegistry
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventChannel {

  /**
   * 通道名称（全局唯一标识）
   *
   * <p>建议使用模块前缀，如 {@code "order-events"}、{@code "user-events"}、{@code "flow-events"}。
   *
   * @return 通道名称
   */
  String value();

  /**
   * 目标 topic 名称（可选，未配置时使用通道名作为默认 topic）
   *
   * @return topic 名称
   */
  String topic() default "";

  /**
   * 消息投递优先级（0-9，数值越大优先级越高）
   *
   * @return 投递优先级
   */
  int priority() default 0;

  /**
   * 序列化策略标识（可选，默认 JSON）
   *
   * @return 序列化策略名
   */
  String serialization() default "json";
}
