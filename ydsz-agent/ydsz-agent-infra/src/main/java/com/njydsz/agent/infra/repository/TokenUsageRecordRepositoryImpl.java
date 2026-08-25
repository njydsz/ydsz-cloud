package com.njydsz.agent.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.dto.TokenUsageRecordDTO;
import com.njydsz.agent.domain.repository.TokenUsageRecordRepository;
import com.njydsz.agent.domain.vo.TokenUsageRecordVO;
import com.njydsz.agent.infra.converter.AgentConverter;
import com.njydsz.agent.infra.entity.TokenUsageRecord;
import com.njydsz.agent.infra.mapper.TokenUsageRecordMapper;

/**
 * Token 用量记录 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link TokenUsageRecordRepository} 接口。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>通过 {@link AgentConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link AgentConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TokenUsageRecordRepositoryImpl implements TokenUsageRecordRepository {

  private final TokenUsageRecordMapper tokenUsageRecordMapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(TokenUsageRecordDTO dto) {
    TokenUsageRecord entity = converter.dtoToEntity(dto);
    return tokenUsageRecordMapper.insert(entity) > 0;
  }

  @Override
  public List<TokenUsageRecordVO> findByCreatedAtRange(LocalDateTime startTime, LocalDateTime endTime) {
    return converter.tokenUsageRecordListToVO(
        tokenUsageRecordMapper.selectList(
            new LambdaQueryWrapper<TokenUsageRecord>()
                .ge(TokenUsageRecord::getCreatedAt, startTime)
                .le(TokenUsageRecord::getCreatedAt, endTime)
                .orderByAsc(TokenUsageRecord::getCreatedAt)));
  }
}
