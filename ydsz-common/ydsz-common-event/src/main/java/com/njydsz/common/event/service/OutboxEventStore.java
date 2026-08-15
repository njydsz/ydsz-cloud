package com.njydsz.common.event.service;

import java.util.List;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.EventStore;

/**
 * 基于 Outbox 的领域事件存储适配器
 *
 * <p>实现 {@link EventStore} SPI 接口，将 {@code append} 操作委托给 {@link OutboxService}，
 * 实现领域事件与 Outbox 表的无缝集成。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>Outbox 模式专注于可靠投递（forward-only），不提供事件回放能力</li>
 *   <li>{@code append} / {@code appendAll} 委托给 {@link OutboxService}</li>
 *   <li>批量追加（{@link #appendAll}）使用 JDBC batchUpdate 实现真正的批量插入，
 *       相比逐条插入可显著减少数据库往返次数</li>
 *   <li>自 1.4.0 起 {@link EventStore} 接口已精简为追加契约，
 *       事件溯源查询能力不再由本适配器暴露（如需事件回放请实现独立的 EventStore）</li>
 * </ul>
 *
 * <p>当容器中不存在其他 {@link EventStore} 实现时，由 {@code EventAutoConfiguration}
 * 自动注册此适配器作为默认实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.6.0 appendAll 使用批量插入优化性能
 */
public class OutboxEventStore implements EventStore {

    /** Outbox 写入服务 */
    private final OutboxService outboxService;

    /**
     * 构造函数
     *
     * @param outboxService Outbox 写入服务
     */
    public OutboxEventStore(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    /**
     * 追加单个领域事件到 Outbox
     *
     * @param event 领域事件
     */
    @Override
    public void append(DomainEvent event) {
        outboxService.appendToOutbox(event);
    }

    /**
     * 批量追加领域事件到 Outbox
     *
     * <p>使用 {@link OutboxService#appendAllToOutbox} 实现真正的批量插入，
     * 相比逐条调用 {@link #append} 可显著减少数据库往返次数。
     *
     * @param events 领域事件列表，为空时直接返回
     */
    @Override
    public void appendAll(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        outboxService.appendAllToOutbox(events);
    }
}
