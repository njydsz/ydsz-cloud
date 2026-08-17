package com.njydsz.agent.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.entity.AgentDefinitionDO;
import com.njydsz.agent.infra.mapper.AgentDefinitionMapper;
import com.njydsz.agent.infra.repository.AgentDefinitionRepository;

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
        new QueryWrapper<AgentDefinitionDO>()
            .eq("agent_code", agentCode)
            .eq("deleted", false)
            .last("LIMIT 1"));
  }

  @Override
  public List<AgentDefinitionDO> findActive() {
    return agentDefinitionMapper.selectList(
        new QueryWrapper<AgentDefinitionDO>()
            .eq("status", "ACTIVE")
            .eq("deleted", false)
            .orderByDesc("created_at"));
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
