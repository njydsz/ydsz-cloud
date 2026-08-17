package com.njydsz.agent.infra.repository.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.entity.AgentApprovalDO;
import com.njydsz.agent.infra.mapper.AgentApprovalMapper;
import com.njydsz.agent.infra.repository.AgentApprovalRepository;

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
        new LambdaQueryWrapper<AgentApprovalDO>()
            .eq(AgentApprovalDO::getStatus, status)
            .orderByDesc(AgentApprovalDO::getCreatedAt));
  }

  @Override
  public void updateStatus(
      String id, String status, String approver, String comment, LocalDateTime resolvedAt) {
    agentApprovalMapper.update(
        null,
        new LambdaUpdateWrapper<AgentApprovalDO>()
            .eq(AgentApprovalDO::getId, id)
            .set(AgentApprovalDO::getStatus, status)
            .set(AgentApprovalDO::getApprover, approver)
            .set(AgentApprovalDO::getComment, comment)
            .set(AgentApprovalDO::getResolvedAt, resolvedAt));
  }

  @Override
  public void expirePendingBefore(
      String status, LocalDateTime cutoff, String expiredStatus, LocalDateTime now) {
    agentApprovalMapper.update(
        null,
        new LambdaUpdateWrapper<AgentApprovalDO>()
            .eq(AgentApprovalDO::getStatus, status)
            .lt(AgentApprovalDO::getCreatedAt, cutoff)
            .set(AgentApprovalDO::getStatus, expiredStatus)
            .set(AgentApprovalDO::getResolvedAt, now));
  }
}
