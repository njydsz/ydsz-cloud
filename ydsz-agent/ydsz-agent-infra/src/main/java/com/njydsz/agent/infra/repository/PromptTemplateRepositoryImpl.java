package com.njydsz.agent.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.dto.PromptTemplateDTO;
import com.njydsz.agent.domain.entity.PromptTemplate;
import com.njydsz.agent.domain.repository.PromptTemplateRepository;
import com.njydsz.agent.domain.vo.PromptTemplateVO;
import com.njydsz.agent.infra.converter.AgentPoConverter;
import com.njydsz.agent.infra.entity.PromptTemplatePO;

/**
 * Prompt 模板 Repository 实现
 *
 * <p>基于 MyBatis-Plus 实现 {@link PromptTemplateRepository} 接口。
 * 写入时通过 {@link AgentPoConverter} 将 domain 实体转为 PO，
 * 读取时通过 {@link AgentPoConverter} 将 PO 转为 domain 后再经 {@link AgentConverter} 转为 VO。
 *
 * <p><b>DDD 分层：</b> domain 层 PromptTemplate 为纯净 POJO；PO 层承载 MP 注解。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class PromptTemplateRepositoryImpl implements PromptTemplateRepository {

  private final BaseMapper<PromptTemplatePO> promptTemplateMapper;

  private final AgentConverter converter;

  private final AgentPoConverter poConverter;

  @Override
  public boolean insert(PromptTemplateDTO dto) {
    PromptTemplate domainEntity = converter.dtoToEntity(dto);
    return promptTemplateMapper.insert(poConverter.domainToPo(domainEntity)) > 0;
  }

  @Override
  public boolean updateById(PromptTemplateDTO dto) {
    PromptTemplate domainEntity = converter.dtoToEntityWithId(dto);
    return promptTemplateMapper.updateById(poConverter.domainToPo(domainEntity)) > 0;
  }

  @Override
  public boolean updateByIdAbTest(PromptTemplateDTO dto) {
    PromptTemplate domainEntity = new PromptTemplate();
    domainEntity.setId(dto.getId());
    domainEntity.setIsAbTestEnabled(dto.getIsAbTestEnabled());
    domainEntity.setAbTargetVersion(dto.getAbTargetVersion());
    domainEntity.setAbTrafficPercent(dto.getAbTrafficPercent());
    return promptTemplateMapper.updateById(poConverter.domainToPo(domainEntity)) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return promptTemplateMapper.deleteById(id) > 0;
  }

  @Override
  public Optional<PromptTemplateVO> findByCode(String templateCode) {
    PromptTemplatePO po = promptTemplateMapper.selectOne(
        new LambdaQueryWrapper<PromptTemplatePO>()
            .eq(PromptTemplatePO::getTemplateCode, templateCode));
    return Optional.ofNullable(po)
        .map(poConverter::poToDomain)
        .map(converter::entityToVO);
  }

  @Override
  public List<PromptTemplateVO> findAllActive() {
    List<PromptTemplatePO> poList = promptTemplateMapper.selectList(
        new LambdaQueryWrapper<PromptTemplatePO>().orderByDesc(PromptTemplatePO::getCreatedAt));
    List<PromptTemplate> domainList = poList.stream()
        .map(poConverter::poToDomain)
        .toList();
    return converter.promptTemplateListToVO(domainList);
  }
}
