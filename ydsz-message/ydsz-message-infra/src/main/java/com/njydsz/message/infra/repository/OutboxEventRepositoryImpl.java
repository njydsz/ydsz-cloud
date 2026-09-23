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
import com.njydsz.message.domain.entity.OutboxEventEntity;
import com.njydsz.message.domain.event.OutboxEntry;
import com.njydsz.message.domain.repository.OutboxEventRepository;
import com.njydsz.message.infra.mapper.OutboxEventMapper;

/**
 * Outbox 事件仓储实现（Infra 层）。
 *
 * <p>实现 {@link OutboxEventRepository} 接口，封装 OutboxEventMapper 数据访问细节。
 *
 * <p>实体类 {@link OutboxEventEntity} 与领域事件 {@link OutboxEvent} 通过命名后缀区分，
 * 本实现负责两者之间的双向转换。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  private final OutboxEventMapper outboxEventMapper;

  /** 默认最大重试次数 */
  private static final int DEFAULT_MAX_RETRIES = 5;

  @Override
  public boolean save(OutboxEntry entry) {
    OutboxEventEntity entity = toEntity(entry);
    return outboxEventMapper.insert(entity) > 0;
  }

  @Override
  public Optional<OutboxEntry> findById(String id) {
    return Optional.ofNullable(outboxEventMapper.selectById(id)).map(this::toEntry);
  }

  @Override
  public List<OutboxEntry> findPending(int limit, LocalDateTime beforeTime) {
    Page<OutboxEventEntity> page = new Page<>(1, limit);
    LambdaQueryWrapper<OutboxEventEntity> wrapper =
        new LambdaQueryWrapper<OutboxEventEntity>()
            .eq(OutboxEventEntity::getStatus, "PENDING")
            .le(OutboxEventEntity::getCreatedAt, beforeTime)
            .orderByAsc(OutboxEventEntity::getCreatedAt);
    List<OutboxEventEntity> records =
        outboxEventMapper.selectPage(page, wrapper).getRecords();
    return records.stream().map(this::toEntry).toList();
  }

  @Override
  public boolean markPublishing(String id) {
    OutboxEventEntity current = outboxEventMapper.selectById(id);
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
    Map<String, Long> result = new HashMap<>(COLLECTION_CAPACITY);
    for (Map<String, Object> row : rows) {
      String status = (String) row.get("status");
      Long count = ((Number) row.get("count")).longValue();
      result.put(status, count);
    }
    return result;
  }

  @Override
  public PageResponse<List<OutboxEntry>> findPage(String status, int pageNum, int pageSize) {
    Page<OutboxEventEntity> page =
        new Page<>(pageNum, Math.min(pageSize, 100));
    LambdaQueryWrapper<OutboxEventEntity> wrapper =
        new LambdaQueryWrapper<>();
    if (status != null && !status.isBlank()) {
      wrapper.eq(OutboxEventEntity::getStatus, status);
    }
    wrapper.orderByDesc(OutboxEventEntity::getCreatedAt);
    Page<OutboxEventEntity> resultPage =
        outboxEventMapper.selectPage(page, wrapper);
    List<OutboxEntry> entries = resultPage.getRecords().stream().map(this::toEntry).toList();
    return PageResponse.success(
        resultPage.getTotal(),
        (long) pageNum,
        (long) pageSize,
        entries);
  }

  /** Entity → Entry 转换。 */
  private OutboxEntry toEntry(OutboxEventEntity entity) {
    OutboxEntry entry = new OutboxEntry();
    entry.setId(entity.getId());
    entry.setAggregateType(entity.getAggregateType());
    entry.setAggregateId(entity.getAggregateId());
    entry.setEventType(entity.getEventType());
    entry.setPayload(entity.getPayload());
    entry.setTenantId(entity.getTenantId());
    entry.setCreatedAt(entity.getCreatedAt());
    entry.setPublishedAt(entity.getPublishedAt());
    entry.setPublishAttempts(entity.getPublishAttempts());
    entry.setStatus(entity.getStatus());
    return entry;
  }

  /** Entry → Entity 转换。 */
  private OutboxEventEntity toEntity(OutboxEntry entry) {
    var entity = new OutboxEventEntity();
    entity.setId(entry.getId());
    entity.setAggregateType(entry.getAggregateType());
    entity.setAggregateId(entry.getAggregateId());
    entity.setEventType(entry.getEventType());
    entity.setPayload(entry.getPayload());
    entity.setTenantId(entry.getTenantId());
    entity.setCreatedAt(entry.getCreatedAt());
    entity.setPublishedAt(entry.getPublishedAt());
    entity.setPublishAttempts(entry.getPublishAttempts());
    entity.setStatus(entry.getStatus());
    return entity;
  }
}
