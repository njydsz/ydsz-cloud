package com.njydsz.pmis.common.entity.event;

import java.util.List;

/**
 * 领域事件发布器接口
 *
 * <p>DDD 中领域事件的发布抽象，由 infra 层提供具体实现
 * （可基于 Spring ApplicationEventPublisher、RocketMQ、Redis Stream 等）。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public interface DomainEventPublisher {

    /**
     * 发布单个领域事件
     *
     * @param event 领域事件
     */
    void publish(DomainEvent event);

    /**
     * 批量发布领域事件
     *
     * @param events 领域事件列表
     */
    void publishAll(List<DomainEvent> events);
}
