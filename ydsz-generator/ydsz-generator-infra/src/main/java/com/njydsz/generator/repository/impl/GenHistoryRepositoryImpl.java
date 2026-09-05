package com.njydsz.generator.repository.impl;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.generator.entity.GenHistory;
import com.njydsz.generator.mapper.GenHistoryMapper;
import com.njydsz.generator.repository.GenHistoryRepository;

/**
 * 代码生成任务历史 Repository 实现。
 *
 * <p>基于 MyBatis-Plus BaseMapper，直接使用 domain Entity 作为持久化实体。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GenHistoryRepositoryImpl implements GenHistoryRepository {

  private final GenHistoryMapper mapper;

  @Override
  public GenHistory save(final GenHistory history) {
    if (history.getId() == null) {
      mapper.insert(history);
    } else {
      mapper.updateById(history);
    }
    log.info("保存生成任务 id={} status={}", history.getId(), history.getStatus());
    return history;
  }

  @Override
  public Optional<GenHistory> findById(final Long id) {
    return Optional.ofNullable(mapper.selectById(id));
  }

  @Override
  public List<GenHistory> findRecent(final int limit) {
    return mapper.selectRecent(limit);
  }

  @Override
  public List<GenHistory> findByStatus(final String status) {
    return mapper.selectByStatus(status);
  }

  @Override
  public void deleteById(final Long id) {
    mapper.deleteById(id);
    log.info("删除任务记录 id={}", id);
  }

  @Override
  public long count() {
    return mapper.selectCount(null);
  }
}
