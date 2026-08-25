package com.njydsz.agent.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.dto.PromptTemplateDTO;
import com.njydsz.agent.domain.repository.PromptTemplateRepository;
import com.njydsz.agent.domain.vo.PromptTemplateVO;
import com.njydsz.agent.infra.converter.AgentConverter;
import com.njydsz.agent.infra.entity.PromptTemplate;
import com.njydsz.agent.infra.mapper.PromptTemplateMapper;

/**
 * Prompt 模板 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link PromptTemplateRepository} 接口。
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
public class PromptTemplateRepositoryImpl implements PromptTemplateRepository {

  private final PromptTemplateMapper promptTemplateMapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(PromptTemplateDTO dto) {
    PromptTemplate entity = converter.dtoToEntity(dto);
    return promptTemplateMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(PromptTemplateDTO dto) {
    PromptTemplate entity = converter.dtoToEntityWithId(dto);
    return promptTemplateMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return promptTemplateMapper.deleteById(id) > 0;
  }

  @Override
  public Optional<PromptTemplateVO> findByCode(String templateCode) {
    return Optional.ofNullable(
            promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>()
                    .eq(PromptTemplate::getTemplateCode, templateCode)))
        .map(converter::entityToVO);
  }

  @Override
  public List<PromptTemplateVO> findAllActive() {
    return converter.promptTemplateListToVO(
        promptTemplateMapper.selectList(
            new LambdaQueryWrapper<PromptTemplate>().orderByDesc(PromptTemplate::getCreatedAt)));
  }
}
