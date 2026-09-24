package com.njydsz.agent.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.dto.AgentApprovalDTO;
import com.njydsz.agent.domain.entity.AgentApproval;
import com.njydsz.agent.domain.repository.AgentApprovalRepository;
import com.njydsz.agent.domain.vo.AgentApprovalVO;

/**
 * Agent 人工审批请求 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentApprovalRepository} 接口。
 * 读取时通过 {@link AgentConverter} 将 domain 实体转为 VO。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>domain 层实体直接承载 MP 持久化注解</li>
 *   <li>CUD 入参 DTO 通过 {@link AgentConverter} 转换为 Domain 后执行数据库操作</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class AgentApprovalRepositoryImpl implements AgentApprovalRepository {

  private final BaseMapper<AgentApproval> agentApprovalMapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(AgentApprovalDTO dto) {
    AgentApproval domainEntity = converter.dtoToEntity(dto);
    return agentApprovalMapper.insert(domainEntity) > 0;
  }

  @Override
  public Optional<AgentApprovalVO> findById(String id) {
    AgentApproval entity = agentApprovalMapper.selectById(id);
    return Optional.ofNullable(entity)
        .map(converter::entityToVO);
  }

  @Override
  public List<AgentApprovalVO> findPending(String status) {
    LambdaQueryWrapper<AgentApproval> wrapper = new LambdaQueryWrapper<AgentApproval>()
        .eq(AgentApproval::getStatus, status)
        .orderByDesc(AgentApproval::getCreatedAt);
    List<AgentApproval> entityList = agentApprovalMapper.selectList(wrapper);
    return entityList.stream()
        .map(converter::entityToVO)
        .toList();
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
