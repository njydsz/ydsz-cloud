package com.njydsz.pmis.common.event.gateway;

import java.util.List;

import com.njydsz.pmis.common.event.model.OutboxMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 空操作事件投递网关（降级实现）
 *
 * <p>当容器中不存在其他 {@link EventPublishGateway} 实现时使用。
 * 记录 WARN 日志但不实际投递消息，返回 true 使消息被标记为 SENT 并清理。
 *
 * <p>生产环境应配置实际的 {@link EventPublishGateway} 实现（如 RocketMQ/Kafka），
 * 否则事件将丢失。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class NoopEventPublishGateway implements EventPublishGateway {

    private static final Logger log = LoggerFactory.getLogger(NoopEventPublishGateway.class);

    @Override
    public boolean publish(OutboxMessage message) {
        log.warn("NoopEventPublishGateway: message id={}, type={}/{}, aggregate={}/{} not actually published",
                message.getId(), message.getAggregateType(), message.getEventType(),
                message.getAggregateType(), message.getAggregateId());
        return true;
    }

    @Override
    public List<Boolean> publishBatch(List<OutboxMessage> messages) {
        for (OutboxMessage message : messages) {
            log.warn("NoopEventPublishGateway: batch message id={}, type={} not actually published",
                    message.getId(), message.getEventType());
        }
        return messages.stream().map(msg -> true).toList();
    }
}
