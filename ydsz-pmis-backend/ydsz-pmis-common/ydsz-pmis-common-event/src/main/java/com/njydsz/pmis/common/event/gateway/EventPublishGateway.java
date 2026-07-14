package com.njydsz.pmis.common.event.gateway;

import com.njydsz.pmis.common.event.model.OutboxMessage;

/**
 * 事件投递网关 SPI
 *
 * <p>由具体的消息队列实现提供（如 RocketMQ / Redis Stream / Kafka）。
 * OutboxProcessor 调用此接口将消息投递到消息队列。
 *
 * <p>实现类应在 {@code AutoConfiguration} 中注册为 Bean，
 * 当容器中不存在此接口的 Bean 时，Outbox 模块使用 NoopEventPublishGateway 降级。
 *
 * @author Marvin Lee
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
}
