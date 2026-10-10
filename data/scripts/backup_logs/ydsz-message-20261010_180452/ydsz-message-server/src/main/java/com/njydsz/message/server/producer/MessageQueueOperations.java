package com.njydsz.message.server.producer;

import com.njydsz.message.domain.dto.MessageItemRequestDTO;

/**
 * 消息队列操作抽象接口。
 *
 * <p>将消息服务的 MQ 发送能力抽象化，底层可切换 RocketMQ / Kafka / RabbitMQ 等实现。
 * 当前统一使用 {@link CommonQueueMessageOperations}（基于 common-queue 的 {@code IMessagePublisher}）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface MessageQueueOperations {

  /**
   * 同步发送消息到消息队列。
   *
   * @param req 消息请求
   * @return MQ 消息 ID
   */
  String syncSend(MessageItemRequestDTO req);

  /**
   * 异步发送消息（不阻塞，底层引擎支持时自然异步）。
   *
   * @param req 消息请求
   */
  void asyncSend(MessageItemRequestDTO req);

  /**
   * 发送事务消息（发送前校验通道/模板，校验通过后同步投递）。
   *
   * <p>校验失败时抛出 BusinessException，不降级发送。
   *
   * @param req 消息请求
   * @return MQ 消息 ID
   */
  String sendTransactionMessage(MessageItemRequestDTO req);
}
