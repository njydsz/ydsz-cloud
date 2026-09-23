package com.njydsz.agent.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.dto.TokenUsageRecordDTO;
import com.njydsz.agent.domain.entity.TokenUsageRecord;
import com.njydsz.agent.domain.repository.TokenUsageRecordRepository;
import com.njydsz.agent.domain.vo.TokenUsageRecordVO;
import com.njydsz.agent.infra.converter.AgentPoConverter;
import com.njydsz.agent.infra.entity.TokenUsageRecordPO;

/**
 * Token 用量记录 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link TokenUsageRecordRepository} 接口。
 * 写入时通过 {@link AgentPoConverter} 将 domain 实体转为 PO，
 * 读取时通过 {@link AgentPoConverter} 将 PO 转为 domain 后再经 {@link AgentConverter} 转为 VO。
 *
 * <p><b>DDD 分层：</b> domain 层 TokenUsageRecord 为纯净 POJO；PO 层承载 MP 注解。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class TokenUsageRecordRepositoryImpl implements TokenUsageRecordRepository {

  private final BaseMapper<TokenUsageRecordPO> tokenUsageRecordMapper;

  private final AgentConverter converter;

  private final AgentPoConverter poConverter;

  @Override
  public boolean insert(TokenUsageRecordDTO dto) {
    TokenUsageRecord domainEntity = converter.dtoToEntity(dto);
    TokenUsageRecordPO po = poConverter.domainToPo(domainEntity);
    return tokenUsageRecordMapper.insert(po) > 0;
  }

  @Override
  public List<TokenUsageRecordVO> findByCreatedAtRange(LocalDateTime startTime, LocalDateTime endTime) {
    List<TokenUsageRecordPO> poList = tokenUsageRecordMapper.selectList(
        new LambdaQueryWrapper<TokenUsageRecordPO>()
            .ge(TokenUsageRecordPO::getCreatedAt, startTime)
            .le(TokenUsageRecordPO::getCreatedAt, endTime)
            .orderByAsc(TokenUsageRecordPO::getCreatedAt));
    List<TokenUsageRecord> domainList = poList.stream()
        .map(poConverter::poToDomain)
        .toList();
    return converter.tokenUsageRecordListToVO(domainList);
  }
}
