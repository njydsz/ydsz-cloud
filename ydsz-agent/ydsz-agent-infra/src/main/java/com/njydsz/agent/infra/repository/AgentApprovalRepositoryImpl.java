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
import com.njydsz.agent.infra.converter.AgentPoConverter;
import com.njydsz.agent.infra.entity.AgentApprovalPO;

/**
 * Agent 人工审批请求 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentApprovalRepository} 接口。
 * 写入时通过 {@link AgentPoConverter} 将 domain 实体转为 PO（含 MP 注解），
 * 读取时通过 {@link AgentPoConverter} 将 PO 转为 domain 实体后再经 {@link AgentConverter} 转为 VO。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>domain 层实体不携带 MP 注解 — 由 PO 层承载持久化映射</li>
 *   <li>通过 {@link AgentPoConverter} 实现 Domain ↔ PO 转换</li>
 *   <li>CUD 入参 DTO 通过 {@link AgentConverter} 转换为 Domain 后再转 PO 执行数据库操作</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class AgentApprovalRepositoryImpl implements AgentApprovalRepository {

  private final BaseMapper<AgentApprovalPO> agentApprovalMapper;

  private final AgentConverter converter;

  private final AgentPoConverter poConverter;

  @Override
  public boolean insert(AgentApprovalDTO dto) {
    AgentApproval domainEntity = converter.dtoToEntity(dto);
    AgentApprovalPO po = poConverter.domainToPo(domainEntity);
    return agentApprovalMapper.insert(po) > 0;
  }

  @Override
  public Optional<AgentApprovalVO> findById(String id) {
    AgentApprovalPO po = agentApprovalMapper.selectById(id);
    return Optional.ofNullable(po)
        .map(poConverter::poToDomain)
        .map(converter::entityToVO);
  }

  @Override
  public List<AgentApprovalVO> findPending(String status) {
    LambdaQueryWrapper<AgentApprovalPO> wrapper = new LambdaQueryWrapper<AgentApprovalPO>()
        .eq(AgentApprovalPO::getStatus, status)
        .orderByDesc(AgentApprovalPO::getCreatedAt);
    List<AgentApprovalPO> poList = agentApprovalMapper.selectList(wrapper);
    return poList.stream()
        .map(poConverter::poToDomain)
        .collect(java.util.stream.Collectors.toList())
        .stream()
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
    AgentApprovalPO po = poConverter.domainToPo(update);
    return agentApprovalMapper.updateById(po) > 0;
  }

  @Override
  public int expirePendingBefore(
      String status, LocalDateTime cutoff, String expiredStatus, LocalDateTime now) {
    LambdaUpdateWrapper<AgentApprovalPO> wrapper = new LambdaUpdateWrapper<AgentApprovalPO>()
        .eq(AgentApprovalPO::getStatus, status)
        .lt(AgentApprovalPO::getCreatedAt, cutoff)
        .set(AgentApprovalPO::getStatus, expiredStatus)
        .set(AgentApprovalPO::getResolvedAt, now);
    agentApprovalMapper.update(null, wrapper);
    return wrapper.getEntity() != null ? 1 : 0;
  }
}
