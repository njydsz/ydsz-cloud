package com.njydsz.agent.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.dto.PromptVersionDTO;
import com.njydsz.agent.domain.entity.PromptVersion;
import com.njydsz.agent.domain.repository.PromptVersionRepository;
import com.njydsz.agent.domain.vo.PromptVersionVO;

/**
 * Prompt 模板版本 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link PromptVersionRepository} 接口。
 * 读取时通过 {@link AgentConverter} 将 domain 实体转为 VO。
 *
 * <p><b>DDD 分层：</b> domain 层 PromptVersion 直接承载 MP 注解用于持久化。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class PromptVersionRepositoryImpl implements PromptVersionRepository {

  private final BaseMapper<PromptVersion> promptVersionMapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(PromptVersionDTO dto) {
    PromptVersion domainEntity = converter.dtoToEntity(dto);
    return promptVersionMapper.insert(domainEntity) > 0;
  }

  @Override
  public Optional<PromptVersionVO> findByTemplateCodeAndVersion(String templateCode, int version) {
    PromptVersion entity = promptVersionMapper.selectOne(
        new LambdaQueryWrapper<PromptVersion>()
            .eq(PromptVersion::getTemplateCode, templateCode)
            .eq(PromptVersion::getVersion, version));
    return Optional.ofNullable(entity)
        .map(converter::entityToVO);
  }

  @Override
  public List<PromptVersionVO> findByTemplateCode(String templateCode) {
    List<PromptVersion> entityList = promptVersionMapper.selectList(
        new LambdaQueryWrapper<PromptVersion>()
            .eq(PromptVersion::getTemplateCode, templateCode)
            .orderByAsc(PromptVersion::getVersion));
    return converter.promptVersionListToVO(entityList);
  }
}
