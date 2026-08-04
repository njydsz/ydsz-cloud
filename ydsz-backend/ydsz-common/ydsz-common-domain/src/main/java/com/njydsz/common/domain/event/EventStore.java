package com.njydsz.common.domain.event;

import java.util.List;

/**
 * 领域事件追加存储接口
 *
 * <p>定义领域事件的持久化写入契约（append-only）。当前实现为 Outbox 模式，
 * 仅支持追加投递，不支持事件回放/事件溯源（Event Sourcing）。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>本接口只承诺"写入"能力，不承诺"读取"能力——避免调用方误用不存在的回放 API</li>
 *   <li>若未来确实需要事件溯源（按聚合根回放事件流），请扩展本接口或新建独立接口</li>
 *   <li>默认实现由 {@code ydsz-common-event} 模块的 OutboxEventStore 提供，
 *       通过 Spring 条件装配（ConditionalOnMissingBean）注册</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.4.0 精简：移除 findByAggregate/findByType/findById/getLatestVersion
 *              四个从未被实现的事件溯源查询方法（原实现一律抛 UnsupportedOperationException）
 *
 * @see DomainEvent
 */
public interface EventStore {

    /**
     * 追加存储领域事件
     *
     * @param event 领域事件
     */
    void append(DomainEvent event);

    /**
     * 批量追加存储领域事件
     *
     * @param events 领域事件列表
     */
    void appendAll(List<DomainEvent> events);
}
