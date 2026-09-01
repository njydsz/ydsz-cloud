package com.njydsz.message.infra.repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.event.OutboxEvent;
import com.njydsz.message.domain.repository.OutboxEventRepository;
import com.njydsz.message.infra.mapper.OutboxEventMapper;

/**
 * Outbox 事件仓储实现（Infra 层）。
 *
 * <p>实现 {@link OutboxEventRepository} 接口，封装 OutboxEventMapper 数据访问细节。
 * 因 domain 事件 {@code OutboxEvent} 与 infra 实体 {@code OutboxEvent} 同名冲突，
 * 依据规范 5.4 节，infra 实体以行内 FQN 引用并附 FQN-OK 注释。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

  private final OutboxEventMapper outboxEventMapper;

  /** 默认最大重试次数 */
  private static final int DEFAULT_MAX_RETRIES = 5;

  @Override
  public boolean save(OutboxEvent event) {
    com.njydsz.message.infra.entity.OutboxEvent entity = toEntity(event); // FQN-OK: name conflict with OutboxEvent
    return outboxEventMapper.insert(entity) > 0;
  }

  @Override
  public Optional<OutboxEvent> findById(String id) {
    return Optional.ofNullable(outboxEventMapper.selectById(id)).map(this::toEvent);
  }

  @Override
  public List<OutboxEvent> findPending(int limit, LocalDateTime beforeTime) {
    Page<com.njydsz.message.infra.entity.OutboxEvent> page = new Page<>(1, limit); // FQN-OK: name conflict with OutboxEvent
    LambdaQueryWrapper<com.njydsz.message.infra.entity.OutboxEvent> wrapper = // FQN-OK: name conflict with OutboxEvent
        new LambdaQueryWrapper<com.njydsz.message.infra.entity.OutboxEvent>() // FQN-OK: name conflict with OutboxEvent
            .eq(com.njydsz.message.infra.entity.OutboxEvent::getStatus, "PENDING") // FQN-OK: name conflict with OutboxEvent
            .le(com.njydsz.message.infra.entity.OutboxEvent::getCreatedAt, beforeTime) // FQN-OK: name conflict with OutboxEvent
            .orderByAsc(com.njydsz.message.infra.entity.OutboxEvent::getCreatedAt); // FQN-OK: name conflict with OutboxEvent
    List<com.njydsz.message.infra.entity.OutboxEvent> records = // FQN-OK: name conflict with OutboxEvent
        outboxEventMapper.selectPage(page, wrapper).getRecords();
    return records.stream().map(this::toEvent).toList();
  }

  @Override
  public boolean markPublishing(String id) {
    com.njydsz.message.infra.entity.OutboxEvent current = outboxEventMapper.selectById(id); // FQN-OK: name conflict with OutboxEvent
    if (current == null) {
      return false;
    }
    return outboxEventMapper.casMarkPublishing(id, current.getPublishAttempts()) > 0;
  }

  @Override
  public boolean markPublished(String id) {
    return outboxEventMapper.casMarkPublished(id) > 0;
  }

  @Override
  public boolean markFailed(String id, int maxRetries) {
    return outboxEventMapper.casMarkFailed(id, maxRetries) > 0;
  }

  @Override
  public Map<String, Long> countByStatus() {
    List<Map<String, Object>> rows = outboxEventMapper.countGroupByStatus();
    Map<String, Long> result = new HashMap<>();
    for (Map<String, Object> row : rows) {
      String status = (String) row.get("status");
      Long count = ((Number) row.get("count")).longValue();
      result.put(status, count);
    }
    return result;
  }

  @Override
  public PageResponse<List<OutboxEvent>> findPage(String status, int pageNum, int pageSize) {
    Page<com.njydsz.message.infra.entity.OutboxEvent> page = // FQN-OK: name conflict with OutboxEvent
        new Page<>(pageNum, Math.min(pageSize, 100));
    LambdaQueryWrapper<com.njydsz.message.infra.entity.OutboxEvent> wrapper = // FQN-OK: name conflict with OutboxEvent
        new LambdaQueryWrapper<>();
    if (status != null && !status.isBlank()) {
      wrapper.eq(com.njydsz.message.infra.entity.OutboxEvent::getStatus, status); // FQN-OK: name conflict with OutboxEvent
    }
    wrapper.orderByDesc(com.njydsz.message.infra.entity.OutboxEvent::getCreatedAt); // FQN-OK: name conflict with OutboxEvent
    Page<com.njydsz.message.infra.entity.OutboxEvent> resultPage = // FQN-OK: name conflict with OutboxEvent
        outboxEventMapper.selectPage(page, wrapper);
    List<OutboxEvent> events = resultPage.getRecords().stream().map(this::toEvent).toList();
    return PageResponse.success(
        resultPage.getTotal(),
        (long) pageNum,
        (long) pageSize,
        events);
  }

  /** Entity → Event 转换。 */
  private OutboxEvent toEvent(com.njydsz.message.infra.entity.OutboxEvent entity) { // FQN-OK: name conflict with OutboxEvent
    OutboxEvent event = new OutboxEvent();
    event.setId(entity.getId());
    event.setAggregateType(entity.getAggregateType());
    event.setAggregateId(entity.getAggregateId());
    event.setEventType(entity.getEventType());
    event.setPayload(entity.getPayload());
    event.setTenantId(entity.getTenantId());
    event.setCreatedAt(entity.getCreatedAt());
    event.setPublishedAt(entity.getPublishedAt());
    event.setPublishAttempts(entity.getPublishAttempts());
    event.setStatus(entity.getStatus());
    return event;
  }

  /** Event → Entity 转换。 */
  private com.njydsz.message.infra.entity.OutboxEvent toEntity(OutboxEvent event) { // FQN-OK: name conflict with OutboxEvent
    var entity = new com.njydsz.message.infra.entity.OutboxEvent(); // FQN-OK: name conflict with OutboxEvent
    entity.setId(event.getId());
    entity.setAggregateType(event.getAggregateType());
    entity.setAggregateId(event.getAggregateId());
    entity.setEventType(event.getEventType());
    entity.setPayload(event.getPayload());
    entity.setTenantId(event.getTenantId());
    entity.setCreatedAt(event.getCreatedAt());
    entity.setPublishedAt(event.getPublishedAt());
    entity.setPublishAttempts(event.getPublishAttempts());
    entity.setStatus(event.getStatus());
    return entity;
  }
}
