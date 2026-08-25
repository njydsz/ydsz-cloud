package com.njydsz.agent.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.dto.PromptVersionDTO;
import com.njydsz.agent.domain.repository.PromptVersionRepository;
import com.njydsz.agent.domain.vo.PromptVersionVO;
import com.njydsz.agent.infra.converter.AgentConverter;
import com.njydsz.agent.infra.entity.PromptVersion;
import com.njydsz.agent.infra.mapper.PromptVersionMapper;

/**
 * Prompt 模板版本 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link PromptVersionRepository} 接口。
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
public class PromptVersionRepositoryImpl implements PromptVersionRepository {

  private final PromptVersionMapper promptVersionMapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(PromptVersionDTO dto) {
    PromptVersion entity = converter.dtoToEntity(dto);
    return promptVersionMapper.insert(entity) > 0;
  }

  @Override
  public Optional<PromptVersionVO> findByTemplateCodeAndVersion(String templateCode, int version) {
    return Optional.ofNullable(
            promptVersionMapper.selectOne(
                new LambdaQueryWrapper<PromptVersion>()
                    .eq(PromptVersion::getTemplateCode, templateCode)
                    .eq(PromptVersion::getVersion, version)))
        .map(converter::entityToVO);
  }

  @Override
  public List<PromptVersionVO> findByTemplateCode(String templateCode) {
    return converter.promptVersionListToVO(
        promptVersionMapper.selectList(
            new LambdaQueryWrapper<PromptVersion>()
                .eq(PromptVersion::getTemplateCode, templateCode)
                .orderByAsc(PromptVersion::getVersion)));
  }
}
