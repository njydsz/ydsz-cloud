package com.njydsz.agent.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.repository.PromptVersionRepository;
import com.njydsz.agent.infra.entity.PromptVersionDO;
import com.njydsz.agent.infra.mapper.PromptVersionMapper;

/**
 * Prompt 模板版本 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link PromptVersionRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class PromptVersionRepositoryImpl implements PromptVersionRepository {

  private final PromptVersionMapper promptVersionMapper;

  @Override
  public void insert(PromptVersionDO entity) {
    promptVersionMapper.insert(entity);
  }

  @Override
  public PromptVersionDO findByTemplateCodeAndVersion(String templateCode, int version) {
    return promptVersionMapper.selectOne(
        new LambdaQueryWrapper<PromptVersionDO>()
            .eq(PromptVersionDO::getTemplateCode, templateCode)
            .eq(PromptVersionDO::getVersion, version));
  }

  @Override
  public List<PromptVersionDO> findByTemplateCode(String templateCode) {
    return promptVersionMapper.selectList(
        new LambdaQueryWrapper<PromptVersionDO>()
            .eq(PromptVersionDO::getTemplateCode, templateCode)
            .orderByAsc(PromptVersionDO::getVersion));
  }
}
