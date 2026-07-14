package com.njydsz.pmis.common.domain.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 内存事件存储实现
 *
 * <p>基于 {@link ConcurrentHashMap} 和 {@link CopyOnWriteArrayList} 的线程安全内存事件存储。
 * 适用于单机环境、单元测试和开发调试。生产环境应使用数据库或消息队列实现。
 *
 * <p><b>限制：</b>
 * <ul>
 *   <li>不支持持久化，应用重启后数据丢失</li>
 *   <li>不支持分布式场景</li>
 *   <li>内存容量有限，不适合大量事件存储</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see EventStore
 */
public class InMemoryEventStore implements EventStore {

    /**
     * 全部事件列表（按追加顺序）
     */
    private final List<DomainEvent> allEvents = new CopyOnWriteArrayList<>();

    /**
     * 聚合根ID到事件列表的索引
     */
    private final ConcurrentHashMap<String, List<DomainEvent>> aggregateIndex = new ConcurrentHashMap<>();

    /**
     * 事件类型到事件列表的索引
     */
    private final ConcurrentHashMap<String, List<DomainEvent>> typeIndex = new ConcurrentHashMap<>();

    /**
     * 事件ID到事件的索引
     */
    private final ConcurrentHashMap<String, DomainEvent> idIndex = new ConcurrentHashMap<>();

    @Override
    public void append(DomainEvent event) {
        allEvents.add(event);
        idIndex.put(event.getEventId(), event);

        if (event.getAggregateId() != null && event.getAggregateType() != null) {
            String aggregateKey = buildAggregateKey(event.getAggregateId(), event.getAggregateType());
            aggregateIndex.computeIfAbsent(aggregateKey, k -> new CopyOnWriteArrayList<>()).add(event);
        }

        if (event.getEventType() != null) {
            typeIndex.computeIfAbsent(event.getEventType(), k -> new CopyOnWriteArrayList<>()).add(event);
        }
    }

    @Override
    public void appendAll(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (DomainEvent event : events) {
            append(event);
        }
    }

    @Override
    public List<DomainEvent> findByAggregate(String aggregateId, String aggregateType) {
        String key = buildAggregateKey(aggregateId, aggregateType);
        List<DomainEvent> events = aggregateIndex.get(key);
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(events);
    }

    @Override
    public List<DomainEvent> findByType(String eventType) {
        List<DomainEvent> events = typeIndex.get(eventType);
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(events);
    }

    @Override
    public Optional<DomainEvent> findById(String eventId) {
        return Optional.ofNullable(idIndex.get(eventId));
    }

    @Override
    public int getLatestVersion(String aggregateId, String aggregateType) {
        List<DomainEvent> events = findByAggregate(aggregateId, aggregateType);
        if (events.isEmpty()) {
            return 0;
        }
        return events.stream()
                .mapToInt(DomainEvent::getVersion)
                .max()
                .orElse(0);
    }

    /**
     * 获取全部事件数量
     *
     * @return 事件总数
     */
    public int size() {
        return allEvents.size();
    }

    /**
     * 清空所有存储的事件
     */
    public void clear() {
        allEvents.clear();
        aggregateIndex.clear();
        typeIndex.clear();
        idIndex.clear();
    }

    /**
     * 获取全部事件（不可变副本）
     *
     * @return 全部事件列表
     */
    public List<DomainEvent> getAll() {
        return new ArrayList<>(allEvents);
    }

    private String buildAggregateKey(String aggregateId, String aggregateType) {
        return aggregateType + ":" + aggregateId;
    }
}
