package com.njydsz.agent.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.repository.AgentDefinitionRepository;
import com.njydsz.agent.infra.entity.AgentDefinitionDO;
import com.njydsz.agent.infra.mapper.AgentDefinitionMapper;

/**
 * Agent 定义 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentDefinitionRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class AgentDefinitionRepositoryImpl implements AgentDefinitionRepository {

  private final AgentDefinitionMapper agentDefinitionMapper;

  @Override
  public AgentDefinitionDO findById(String id) {
    return agentDefinitionMapper.selectById(id);
  }

  @Override
  public AgentDefinitionDO findByCode(String agentCode) {
    return agentDefinitionMapper.selectOne(
        new LambdaQueryWrapper<AgentDefinitionDO>().eq(AgentDefinitionDO::getAgentCode, agentCode));
  }

  @Override
  public List<AgentDefinitionDO> findActive() {
    return agentDefinitionMapper.selectList(
        new LambdaQueryWrapper<AgentDefinitionDO>()
            .eq(AgentDefinitionDO::getStatus, "ACTIVE")
            .orderByDesc(AgentDefinitionDO::getCreatedAt));
  }

  @Override
  public void insert(AgentDefinitionDO entity) {
    agentDefinitionMapper.insert(entity);
  }

  @Override
  public void updateById(AgentDefinitionDO entity) {
    agentDefinitionMapper.updateById(entity);
  }

  @Override
  public boolean deleteById(String id) {
    return agentDefinitionMapper.deleteById(id) > 0;
  }
}
