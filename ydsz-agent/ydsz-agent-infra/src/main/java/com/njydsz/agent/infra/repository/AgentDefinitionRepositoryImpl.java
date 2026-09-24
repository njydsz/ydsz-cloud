package com.njydsz.agent.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.dto.AgentDefinitionDTO;
import com.njydsz.agent.domain.entity.AgentDefinition;
import com.njydsz.agent.domain.repository.AgentDefinitionRepository;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;

/**
 * Agent 定义 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentDefinitionRepository} 接口。
 * 写入时通过 {@link AgentPoConverter} 将 domain 实体转为 PO（含 MP 注解），
 * 读取时通过 {@link AgentPoConverter} 将 PO 转为 domain 后再经 {@link AgentConverter} 转为 VO。
 *
 * <p><b>DDD 分层：</b> domain 层 AgentDefinition 为纯净 POJO；PO 层承载 MP 注解。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class AgentDefinitionRepositoryImpl implements AgentDefinitionRepository {

  private final BaseMapper<AgentDefinition> agentDefinitionMapper;

  private final AgentConverter converter;

  @Override
  public Optional<AgentDefinitionVO> findById(String id) {
    AgentDefinition entity = agentDefinitionMapper.selectById(id);
    return Optional.ofNullable(entity)
        .map(converter::entityToVO);
  }

  @Override
  public Optional<AgentDefinitionVO> findByCode(String agentCode) {
    AgentDefinition entity = agentDefinitionMapper.selectOne(
        new LambdaQueryWrapper<AgentDefinition>()
            .eq(AgentDefinition::getAgentCode, agentCode));
    return Optional.ofNullable(entity)
        .map(converter::entityToVO);
  }

  @Override
  public List<AgentDefinitionVO> findActive() {
    List<AgentDefinition> entityList = agentDefinitionMapper.selectList(
        new LambdaQueryWrapper<AgentDefinition>()
            .eq(AgentDefinition::getStatus, "ACTIVE")
            .orderByDesc(AgentDefinition::getCreatedAt));
    return converter.agentDefinitionListToVO(entityList);
  }

  @Override
  public boolean insert(AgentDefinitionDTO dto) {
    AgentDefinition entity = converter.dtoToEntity(dto);
    return agentDefinitionMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(AgentDefinitionDTO dto) {
    AgentDefinition entity = converter.dtoToEntityWithId(dto);
    return agentDefinitionMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return agentDefinitionMapper.deleteById(id) > 0;
  }
}
