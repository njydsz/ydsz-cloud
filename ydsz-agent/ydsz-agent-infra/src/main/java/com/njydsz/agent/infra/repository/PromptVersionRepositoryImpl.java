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
import com.njydsz.agent.infra.converter.AgentPoConverter;
import com.njydsz.agent.infra.entity.PromptVersionPO;

/**
 * Prompt 模板版本 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link PromptVersionRepository} 接口。
 * 写入时通过 {@link AgentPoConverter} 将 domain 实体转为 PO，
 * 读取时通过 {@link AgentPoConverter} 将 PO 转为 domain 后再经 {@link AgentConverter} 转为 VO。
 *
 * <p><b>DDD 分层：</b> domain 层 PromptVersion 为纯净 POJO；PO 层承载 MP 注解。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class PromptVersionRepositoryImpl implements PromptVersionRepository {

  private final BaseMapper<PromptVersionPO> promptVersionMapper;

  private final AgentConverter converter;

  private final AgentPoConverter poConverter;

  @Override
  public boolean insert(PromptVersionDTO dto) {
    PromptVersion domainEntity = converter.dtoToEntity(dto);
    PromptVersionPO po = poConverter.domainToPo(domainEntity);
    return promptVersionMapper.insert(po) > 0;
  }

  @Override
  public Optional<PromptVersionVO> findByTemplateCodeAndVersion(String templateCode, int version) {
    PromptVersionPO po = promptVersionMapper.selectOne(
        new LambdaQueryWrapper<PromptVersionPO>()
            .eq(PromptVersionPO::getTemplateCode, templateCode)
            .eq(PromptVersionPO::getVersion, version));
    return Optional.ofNullable(po)
        .map(poConverter::poToDomain)
        .map(converter::entityToVO);
  }

  @Override
  public List<PromptVersionVO> findByTemplateCode(String templateCode) {
    List<PromptVersionPO> poList = promptVersionMapper.selectList(
        new LambdaQueryWrapper<PromptVersionPO>()
            .eq(PromptVersionPO::getTemplateCode, templateCode)
            .orderByAsc(PromptVersionPO::getVersion));
    List<PromptVersion> domainList = poList.stream()
        .map(poConverter::poToDomain)
        .toList();
    return converter.promptVersionListToVO(domainList);
  }
}
