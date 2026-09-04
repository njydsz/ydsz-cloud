package com.njydsz.cronjob.infra.repository.outbox;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.outbox.OutboxEventRepository;
import com.njydsz.cronjob.domain.vo.OutboxEventVO;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.entity.OutboxEvent;
import com.njydsz.cronjob.infra.mapper.outbox.OutboxEventMapper;

/**
 * Outbox 事件仓储实现（Infra 层）。
 *
 * <p>实现 {@link OutboxEventRepository} 接口，封装 OutboxEventMapper 数据访问细节。
 *
 * <p>通过 {@link CronjobConverter} 将 Entity 转换为 VO 后返回。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

  private final OutboxEventMapper outboxEventMapper;

  private final CronjobConverter converter;

  @Override
  public OutboxEventVO save(OutboxEventVO event) {
    OutboxEvent entity = converter.voToEntity(event);
    outboxEventMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public List<OutboxEventVO> saveAll(List<OutboxEventVO> events) {
    List<OutboxEvent> entities = converter.outboxEventVOsToEntities(events);
    for (OutboxEvent entity : entities) {
      outboxEventMapper.insert(entity);
    }
    return converter.outboxEventListToVO(entities);
  }

  @Override
  public List<OutboxEventVO> findPending(LocalDateTime now, int maxRetry, int batchSize) {
    return converter.outboxEventListToVO(outboxEventMapper.selectPending(now, maxRetry, batchSize));
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
