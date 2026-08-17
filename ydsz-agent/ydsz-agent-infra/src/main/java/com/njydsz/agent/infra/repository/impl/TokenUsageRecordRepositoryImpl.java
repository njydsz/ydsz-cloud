package com.njydsz.agent.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.entity.TokenUsageRecordDO;
import com.njydsz.agent.infra.mapper.TokenUsageRecordMapper;
import com.njydsz.agent.infra.repository.TokenUsageRecordRepository;

/**
 * Token 用量记录 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link TokenUsageRecordRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TokenUsageRecordRepositoryImpl implements TokenUsageRecordRepository {

  private final TokenUsageRecordMapper tokenUsageRecordMapper;

  @Override
  public void insert(TokenUsageRecordDO record) {
    tokenUsageRecordMapper.insert(record);
  }

  @Override
  public List<TokenUsageRecordDO> findByCreatedAtRange(LocalDateTime startTime, LocalDateTime endTime) {
    return tokenUsageRecordMapper.selectList(
        new QueryWrapper<TokenUsageRecordDO>()
            .ge("created_at", startTime)
            .le("created_at", endTime)
            .orderByAsc("created_at"));
  }
}
