package com.njydsz.agent.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.dto.AgentApprovalDTO;
import com.njydsz.agent.domain.repository.AgentApprovalRepository;
import com.njydsz.agent.domain.vo.AgentApprovalVO;
import com.njydsz.agent.infra.converter.AgentConverter;
import com.njydsz.agent.infra.entity.AgentApproval;
import com.njydsz.agent.infra.mapper.AgentApprovalMapper;

/**
 * Agent 人工审批请求 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentApprovalRepository} 接口。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>通过 {@link AgentConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link AgentConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class AgentApprovalRepositoryImpl implements AgentApprovalRepository {

  private final AgentApprovalMapper agentApprovalMapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(AgentApprovalDTO dto) {
    AgentApproval entity = converter.dtoToEntity(dto);
    return agentApprovalMapper.insert(entity) > 0;
  }

  @Override
  public Optional<AgentApprovalVO> findById(String id) {
    return Optional.ofNullable(agentApprovalMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<AgentApprovalVO> findPending(String status) {
    return converter.agentApprovalListToVO(
        agentApprovalMapper.selectList(
            new LambdaQueryWrapper<AgentApproval>()
                .eq(AgentApproval::getStatus, status)
                .orderByDesc(AgentApproval::getCreatedAt)));
  }

  @Override
  public boolean updateStatus(
      String id, String status, String approver, String comment, LocalDateTime resolvedAt) {
    AgentApproval update = AgentApproval.builder()
        .id(id)
        .status(status)
        .approver(approver)
        .comment(comment)
        .resolvedAt(resolvedAt)
        .build();
    return agentApprovalMapper.updateById(update) > 0;
  }

  @Override
  public int expirePendingBefore(
      String status, LocalDateTime cutoff, String expiredStatus, LocalDateTime now) {
    LambdaUpdateWrapper<AgentApproval> wrapper = new LambdaUpdateWrapper<AgentApproval>()
        .eq(AgentApproval::getStatus, status)
        .lt(AgentApproval::getCreatedAt, cutoff)
        .set(AgentApproval::getStatus, expiredStatus)
        .set(AgentApproval::getResolvedAt, now);
    agentApprovalMapper.update(null, wrapper);
    return wrapper.getEntity() != null ? 1 : 0;
  }
}
