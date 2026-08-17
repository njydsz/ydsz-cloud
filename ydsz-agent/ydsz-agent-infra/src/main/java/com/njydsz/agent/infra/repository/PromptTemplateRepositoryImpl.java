package com.njydsz.agent.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.repository.PromptTemplateRepository;
import com.njydsz.agent.infra.entity.PromptTemplateDO;
import com.njydsz.agent.infra.mapper.PromptTemplateMapper;

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
        new LambdaQueryWrapper<PromptTemplateDO>()
            .eq(PromptTemplateDO::getTemplateCode, templateCode));
  }

  @Override
  public List<PromptTemplateDO> findAllActive() {
    return promptTemplateMapper.selectList(
        new LambdaQueryWrapper<PromptTemplateDO>().orderByDesc(PromptTemplateDO::getCreatedAt));
  }
}
