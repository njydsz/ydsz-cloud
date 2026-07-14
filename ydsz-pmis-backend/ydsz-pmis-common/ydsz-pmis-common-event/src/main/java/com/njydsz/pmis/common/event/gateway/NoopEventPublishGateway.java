package com.njydsz.pmis.common.event.gateway;

import com.njydsz.pmis.common.event.model.OutboxMessage;

/**
 * 空操作事件投递网关（降级实现）
 *
 * <p>当容器中不存在其他 {@link EventPublishGateway} 实现时使用。
 * 仅记录日志，不实际投递消息。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class NoopEventPublishGateway implements EventPublishGateway {

    @Override
    public boolean publish(OutboxMessage message) {
        return true;
    }
}
