package com.njydsz.agent.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.dto.post.AgentDefinitionPostDTO;
import com.njydsz.agent.domain.dto.put.AgentDefinitionPutDTO;
import com.njydsz.agent.domain.repository.AgentDefinitionRepository;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;
import com.njydsz.agent.infra.converter.AgentConverter;
import com.njydsz.agent.infra.entity.AgentDefinitionDO;
import com.njydsz.agent.infra.mapper.AgentDefinitionMapper;

/**
 * Agent 定义 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link AgentDefinitionRepository} 接口。
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
public class AgentDefinitionRepositoryImpl implements AgentDefinitionRepository {

  private final AgentDefinitionMapper agentDefinitionMapper;

  private final AgentConverter converter;

  @Override
  public Optional<AgentDefinitionVO> findById(String id) {
    return Optional.ofNullable(agentDefinitionMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<AgentDefinitionVO> findByCode(String agentCode) {
    return Optional.ofNullable(
            agentDefinitionMapper.selectOne(
                new LambdaQueryWrapper<AgentDefinitionDO>().eq(AgentDefinitionDO::getAgentCode, agentCode)))
        .map(converter::entityToVO);
  }

  @Override
  public List<AgentDefinitionVO> findActive() {
    return converter.agentDefinitionListToVO(
        agentDefinitionMapper.selectList(
            new LambdaQueryWrapper<AgentDefinitionDO>()
                .eq(AgentDefinitionDO::getStatus, "ACTIVE")
                .orderByDesc(AgentDefinitionDO::getCreatedAt)));
  }

  @Override
  public boolean insert(AgentDefinitionPostDTO dto) {
    AgentDefinitionDO entity = converter.postDtoToEntity(dto);
    return agentDefinitionMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(AgentDefinitionPutDTO dto) {
    AgentDefinitionDO entity = converter.putDtoToEntity(dto);
    return agentDefinitionMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return agentDefinitionMapper.deleteById(id) > 0;
  }
}
