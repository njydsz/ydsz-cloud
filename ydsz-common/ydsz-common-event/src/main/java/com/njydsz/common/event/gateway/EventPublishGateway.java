package com.njydsz.common.event.gateway;

import com.njydsz.common.event.model.OutboxMessage;
import java.util.List;

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
   * @throws Exception 投递异常
   */
  boolean publish(OutboxMessage message) throws Exception;

  /**
   * 批量投递消息到消息队列
   *
   * <p>实现类可利用 MQ 的批量发送能力提升吞吐量。 默认实现逐条调用 {@link #publish}，支持批量发送的实现类应覆盖此方法。
   *
   * @param messages Outbox 消息列表
   * @return 每条消息的投递结果（true=成功，false=失败），顺序与输入一致
   * @throws Exception 投递异常
   */
  /**
   * 批量投递消息到消息队列（默认逐条调用 publish）
   *
   * @param messages Outbox 消息列表
   * @return 每条消息的投递结果（true=成功，false=失败），顺序与输入一致
   * @throws Exception 投递异常
   */
  default List<Boolean> publishBatch(List<OutboxMessage> messages) throws Exception {
    return messages.stream()
        .map(
            msg -> {
              try {
                return publish(msg);
              } catch (Exception e) {
                return false;
              }
            })
        .toList();
  }
}
