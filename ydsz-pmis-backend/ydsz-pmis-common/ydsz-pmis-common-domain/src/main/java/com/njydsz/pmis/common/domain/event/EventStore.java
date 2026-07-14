package com.njydsz.pmis.common.domain.event;

import java.util.List;
import java.util.Optional;

/**
 * 领域事件存储接口
 *
 * <p>定义领域事件的持久化存储契约，支持事件溯源（Event Sourcing）模式。
 * 实现类可以将领域事件持久化到数据库、消息队列或其他存储介质。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>追加存储领域事件（不可变，仅追加）</li>
 *   <li>按聚合根ID查询事件流</li>
 *   <li>按事件类型查询</li>
 *   <li>按时间范围查询</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 存储事件
 * eventStore.append(orderCreatedEvent);
 *
 * // 查询聚合根的事件流
 * List<DomainEvent> events = eventStore.findByAggregate("order-123", "Order");
 *
 * // 重放事件以重建聚合根状态
 * for (DomainEvent event : events) {
 *     order.apply(event);
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
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

    /**
     * 按聚合根ID查询事件流
     *
     * @param aggregateId   聚合根ID
     * @param aggregateType 聚合根类型
     * @return 事件列表（按版本升序）
     */
    List<DomainEvent> findByAggregate(String aggregateId, String aggregateType);

    /**
     * 按事件类型查询
     *
     * @param eventType 事件类型
     * @return 事件列表
     */
    List<DomainEvent> findByType(String eventType);

    /**
     * 按事件ID查询
     *
     * @param eventId 事件ID
     * @return 事件Optional
     */
    Optional<DomainEvent> findById(String eventId);

    /**
     * 获取聚合根的最新版本
     *
     * @param aggregateId   聚合根ID
     * @param aggregateType 聚合根类型
     * @return 最新版本，无事件返回 0
     */
    int getLatestVersion(String aggregateId, String aggregateType);
}
