package com.njydsz.cronjob.infra.repository.outbox;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.entity.outbox.OutboxEvent;
import com.njydsz.cronjob.domain.repository.outbox.OutboxEventRepository;
import com.njydsz.cronjob.infra.mapper.outbox.OutboxEventMapper;

/**
 * Outbox 事件仓储实现（P0-2：事务性 Outbox 事件模式）。
 *
 * <p>提供事件写入、查询、状态变更的 MyBatis 实现。事件写入通过 {@link OutboxEventMapper} 完成，
 * 可与业务操作共用同一事务（通过 Spring 事务管理）。
 *
 * <p><b>注意</b>：{@link #save(OutboxEvent)} 和 {@link #saveAll(List)} 方法应在业务事务内调用，
 * 不使用独立事务（{@code @Transactional(propagation = Propagation.MANDATORY)} 语义由调用方保证）。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

  private final OutboxEventMapper outboxEventMapper;

  @Override
  public OutboxEvent save(OutboxEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("OutboxEvent 不能为空");
    }
    LocalDateTime now = LocalDateTime.now();
    if (event.getCreateTime() == null) {
      event.setCreateTime(now);
    }
    if (event.getStatus() == null) {
      event.setStatus(OutboxEvent.OutboxStatus.PENDING);
    }
    if (event.getRetryCount() == null) {
      event.setRetryCount(0);
    }
    if (event.getNextRetryTime() == null) {
      event.setNextRetryTime(now);
    }
    event.setUpdateTime(now);
    outboxEventMapper.insert(event);
    return event;
  }

  @Override
  public List<OutboxEvent> saveAll(List<OutboxEvent> events) {
    if (events == null || events.isEmpty()) {
      throw new IllegalArgumentException("OutboxEvent 列表不能为空");
    }
    LocalDateTime now = LocalDateTime.now();
    for (OutboxEvent event : events) {
      if (event.getCreateTime() == null) {
        event.setCreateTime(now);
      }
      if (event.getStatus() == null) {
        event.setStatus(OutboxEvent.OutboxStatus.PENDING);
      }
      if (event.getRetryCount() == null) {
        event.setRetryCount(0);
      }
      if (event.getNextRetryTime() == null) {
        event.setNextRetryTime(now);
      }
      event.setUpdateTime(now);
      outboxEventMapper.insert(event);
    }
    return events;
  }

  @Override
  public List<OutboxEvent> findPending(LocalDateTime now, int maxRetry, int batchSize) {
    return outboxEventMapper.selectPending(now, maxRetry, batchSize);
  }

  @Override
  public boolean markPublished(Long id) {
    return outboxEventMapper.markPublished(id) > 0;
  }

  @Override
  public boolean markDead(Long id) {
    return outboxEventMapper.markDead(id) > 0;
  }

  @Override
  public boolean incrementRetry(Long id, LocalDateTime nextRetry) {
    return outboxEventMapper.incrementRetry(id, nextRetry) > 0;
  }

  @Override
  public int deletePublishedBefore(LocalDateTime beforeTime) {
    return outboxEventMapper.deletePublishedBefore(beforeTime);
  }
}
