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
import com.njydsz.message.infra.entity.OutboxEvent;
import com.njydsz.message.infra.mapper.OutboxEventMapper;

/**
 * Outbox 事件仓储实现（Infra 层）。
 *
 * <p>实现 {@link OutboxEventRepository} 接口，封装 OutboxEventMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
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
    OutboxEvent entity = toEntity(event);
    return outboxEventMapper.insert(entity) > 0;
  }

  @Override
  public Optional<OutboxEvent> findById(String id) {
    return Optional.ofNullable(outboxEventMapper.selectById(id)).map(this::toEvent);
  }

  @Override
  public List<OutboxEvent> findPending(int limit, LocalDateTime beforeTime) {
    Page<OutboxEvent> page = new Page<>(1, limit);
    LambdaQueryWrapper<OutboxEvent> wrapper =
        new LambdaQueryWrapper<OutboxEvent>()
            .eq(OutboxEvent::getStatus, "PENDING")
            .le(OutboxEvent::getCreatedAt, beforeTime)
            .orderByAsc(OutboxEvent::getCreatedAt);
    List<OutboxEvent> records = outboxEventMapper.selectPage(page, wrapper).getRecords();
    return records.stream().map(this::toEvent).toList();
  }

  @Override
  public boolean markPublishing(String id) {
    OutboxEvent current = outboxEventMapper.selectById(id);
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
    Page<OutboxEvent> page = new Page<>(pageNum, Math.min(pageSize, 100));
    LambdaQueryWrapper<OutboxEvent> wrapper = new LambdaQueryWrapper<>();
    if (status != null && !status.isBlank()) {
      wrapper.eq(OutboxEvent::getStatus, status);
    }
    wrapper.orderByDesc(OutboxEvent::getCreatedAt);
    Page<OutboxEvent> resultPage = outboxEventMapper.selectPage(page, wrapper);
    List<OutboxEvent> events = resultPage.getRecords().stream().map(this::toEvent).toList();
    return PageResponse.success(
        resultPage.getTotal(),
        (long) pageNum,
        (long) pageSize,
        events);
  }

  /** Entity → Event 转换。 */
  private OutboxEvent toEvent(OutboxEvent entity) {
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
  private OutboxEvent toEntity(OutboxEvent event) {
    OutboxEvent entity = new OutboxEvent();
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
