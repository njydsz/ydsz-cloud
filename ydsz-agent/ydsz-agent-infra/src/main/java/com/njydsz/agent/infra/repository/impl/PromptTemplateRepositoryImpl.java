package com.njydsz.agent.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.entity.PromptTemplateDO;
import com.njydsz.agent.infra.mapper.PromptTemplateMapper;
import com.njydsz.agent.infra.repository.PromptTemplateRepository;

/**
 * Prompt 模板 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link PromptTemplateRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class PromptTemplateRepositoryImpl implements PromptTemplateRepository {

  private final PromptTemplateMapper promptTemplateMapper;

  @Override
  public void insert(PromptTemplateDO entity) {
    promptTemplateMapper.insert(entity);
  }

  @Override
  public void updateById(PromptTemplateDO entity) {
    promptTemplateMapper.updateById(entity);
  }

  @Override
  public void deleteById(Long id) {
    promptTemplateMapper.deleteById(id);
  }

  @Override
  public PromptTemplateDO findByCode(String templateCode) {
    return promptTemplateMapper.selectOne(
        new QueryWrapper<PromptTemplateDO>()
            .eq("template_code", templateCode)
            .eq("deleted", false)
            .last("LIMIT 1"));
  }

  @Override
  public List<PromptTemplateDO> findAllActive() {
    return promptTemplateMapper.selectList(
        new QueryWrapper<PromptTemplateDO>().eq("deleted", false));
  }
}
