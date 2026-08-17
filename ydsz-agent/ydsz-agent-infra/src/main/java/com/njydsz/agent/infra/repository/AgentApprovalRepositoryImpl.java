package com.njydsz.agent.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.repository.AgentApprovalRepository;
import com.njydsz.agent.infra.entity.AgentApprovalDO;
import com.njydsz.agent.infra.mapper.AgentApprovalMapper;

/**
 * Agent 人工审批请求 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentApprovalRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class AgentApprovalRepositoryImpl implements AgentApprovalRepository {

  private final AgentApprovalMapper agentApprovalMapper;

  @Override
  public void insert(AgentApprovalDO entity) {
    agentApprovalMapper.insert(entity);
  }

  @Override
  public AgentApprovalDO findById(String id) {
    return agentApprovalMapper.selectById(id);
  }

  @Override
  public List<AgentApprovalDO> findPending(String status) {
    return agentApprovalMapper.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentApprovalDO>()
            .eq(AgentApprovalDO::getStatus, status)
            .orderByDesc(AgentApprovalDO::getCreatedAt));
  }

  @Override
  public void updateStatus(
      String id, String status, String approver, String comment, LocalDateTime resolvedAt) {
    AgentApprovalDO update = AgentApprovalDO.builder()
        .id(id)
        .status(status)
        .approver(approver)
        .comment(comment)
        .resolvedAt(resolvedAt)
        .build();
    agentApprovalMapper.updateById(update);
  }

  @Override
  public void expirePendingBefore(
      String status, LocalDateTime cutoff, String expiredStatus, LocalDateTime now) {
    LambdaUpdateWrapper<AgentApprovalDO> wrapper = new LambdaUpdateWrapper<AgentApprovalDO>()
        .eq(AgentApprovalDO::getStatus, status)
        .lt(AgentApprovalDO::getCreatedAt, cutoff)
        .set(AgentApprovalDO::getStatus, expiredStatus)
        .set(AgentApprovalDO::getResolvedAt, now);
    agentApprovalMapper.update(null, wrapper);
  }
}
