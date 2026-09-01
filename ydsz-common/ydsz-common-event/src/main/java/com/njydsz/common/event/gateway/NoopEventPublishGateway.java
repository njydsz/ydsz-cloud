package com.njydsz.common.event.gateway;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * 空操作事件投递网关（降级实现）
 *
 * <p>当容器中不存在其他 {@link EventPublishGateway} 实现时使用。 记录 WARN 日志且不实际投递，返回 false 使消息进入
 * 重试/死信流程，避免消息被错误标记为已发送而静默丢失。
 *
 * <p>生产环境应配置实际的 {@link EventPublishGateway} 实现（如 RocketMQ/Kafka）， 否则事件将持续重试直至进入死信。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NoopEventPublishGateway implements EventPublishGateway {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(NoopEventPublishGateway.class);

  /**
   * 空操作投递（记录 WARN 日志但不实际投递）
   *
   * @param message Outbox 消息
   * @return 始终返回 false，使消息进入重试/死信流程而非标记为已发送
   */
  @Override
  public boolean publish(OutboxMessage message) {
    LOG.warn(
        "NoopEventPublishGateway: message id={}, type={}, aggregate={}/{} not actually published",
        message.getId(),
        message.getEventType(),
        message.getAggregateType(),
        message.getAggregateId());
    return false;
  }

  /**
   * 空操作批量投递（记录 WARN 日志但不实际投递）
   *
   * @param messages Outbox 消息列表
   * @return 始终返回全 false 列表，使所有消息进入重试/死信流程
   */
  @Override
  public List<Boolean> publishBatch(List<OutboxMessage> messages) {
    for (OutboxMessage message : messages) {
      LOG.warn(
          "NoopEventPublishGateway: batch message id={}, type={} not actually published",
          message.getId(),
          message.getEventType());
    }
    return messages.stream().map(msg -> false).toList();
  }
}
