package com.njydsz.cronjob.infra.repository.event;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.cronjob.domain.event.JobEvent;
import com.njydsz.cronjob.domain.repository.event.EventStoreRepository;
import com.njydsz.cronjob.infra.entity.event.StoredEvent;
import com.njydsz.cronjob.infra.mapper.event.StoredEventMapper;

/**
 * 事件存储 Repository 实现（P3-1 Event Sourcing）。
 *
 * <p>实现 {@link EventStoreRepository} 接口，封装 ydsz_event_store 表的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class EventStoreRepositoryImpl implements EventStoreRepository {

  private final StoredEventMapper storedEventMapper;

  @Override
  public JobEvent append(JobEvent event) {
    StoredEvent entity = new StoredEvent();
    entity.setId(event.eventId());
    entity.setAggregateType(event.aggregateType());
    entity.setAggregateId(event.aggregateId());
    entity.setEventType(event.eventType());
    entity.setPayload(event.payload());
    entity.setOperator(event.operator());
    entity.setOccurredAt(event.occurredAt());
    storedEventMapper.insert(entity);
    return event;
  }

  @Override
  public List<JobEvent> findByAggregateId(String aggregateId) {
    List<StoredEvent> entities = storedEventMapper.selectByAggregateId(aggregateId);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return entities.stream().map(this::toJobEvent).toList();
  }

  @Override
  public List<JobEvent> findByAggregateIdAndTimeRange(
      String aggregateId, LocalDateTime startTime, LocalDateTime endTime) {
    List<StoredEvent> entities =
        storedEventMapper.selectByAggregateIdAndTimeRange(aggregateId, startTime, endTime);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return entities.stream().map(this::toJobEvent).toList();
  }

  @Override
  public List<JobEvent> findByType(String eventType, int limit, int offset) {
    List<StoredEvent> entities = storedEventMapper.selectByType(eventType, limit, offset);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return entities.stream().map(this::toJobEvent).toList();
  }

  @Override
  public long countByType(String eventType) {
    return storedEventMapper.countByType(eventType);
  }

  @Override
  public int deleteBefore(LocalDateTime beforeTime) {
    return storedEventMapper.delete(
        new QueryWrapper<StoredEvent>()
            .lt("occurred_at", beforeTime));
  }

  private JobEvent toJobEvent(StoredEvent entity) {
    return new JobEvent(
        entity.getId(),
        entity.getAggregateId(),
        entity.getEventType(),
        entity.getPayload(),
        entity.getOperator(),
        entity.getOccurredAt());
  }
}
