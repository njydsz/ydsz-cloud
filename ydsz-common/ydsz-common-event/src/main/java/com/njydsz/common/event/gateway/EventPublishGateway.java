package com.njydsz.common.event.gateway;

import java.util.List;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * 事件投递网关 SPI
 *
 * <p>由具体的消息队列实现提供（如 RocketMQ / Redis Stream / Kafka）。 OutboxProcessor 调用此接口将消息投递到消息队列。
 *
 * <p>实现类应在 {@code AutoConfiguration} 中注册为 Bean， 当容器中不存在此接口的 Bean 时，Outbox 模块使用
 * NoopEventPublishGateway 降级。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface EventPublishGateway {

  /**
   * 投递消息到消息队列
   *
   * @param message Outbox 消息
   * @return true 投递成功，false 投递失败（将触发重试）
   * @throws Throwable 投递异常
   */
  boolean publish(OutboxMessage message) throws Throwable;

  /**
   * 批量投递消息到消息队列（默认逐条调用 publish）
   *
   * @param messages Outbox 消息列表
   * @return 每条消息的投递结果（true=成功，false=失败），顺序与输入一致
   * @throws Throwable 投递异常
   */
  default List<Boolean> publishBatch(List<OutboxMessage> messages) throws Throwable {
    return messages.stream()
        .map(
            msg -> {
              try {
                return publish(msg);
              } catch (Throwable e) {
                return false;
              }
            })
        .toList();
  }
}
