package com.njydsz.common.event.api;

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
 * <p><b>废弃建议：</b>新代码不应再实现或注入本接口。
 * 统一使用 {@link com.njydsz.common.event.publish.DomainEventPublisher} 发布领域事件，
 * 或通过 {@link com.njydsz.common.event.service.OutboxService#appendToOutbox(com.njydsz.common.event.api.DomainEvent)} 写入 Outbox。
 * 本接口保留仅为兼容 1.x 调用方，计划 2.0 移除。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.4.0 精简：移除 findByAggregate/findByType/findById/getLatestVersion
 *              四个从未被实现的事件溯源查询方法（原实现一律抛 UnsupportedOperationException）
 * @since 1.5.0 由 common-domain 迁入 common-event
 * @deprecated 1.8.0 使用 {@link com.njydsz.common.event.publish.DomainEventPublisher} 替代
 *
 * @see DomainEvent
 */
@Deprecated
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
