package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.SpaceTemplateDTO;
import com.njydsz.nextwiki.domain.repository.SpaceTemplateRepository;
import com.njydsz.nextwiki.infra.converter.NextwikiConverter;
import com.njydsz.nextwiki.infra.entity.SpaceTemplateDO;
import com.njydsz.nextwiki.infra.mapper.SpaceTemplateMapper;

/**
 * 空间模板仓储实现
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SpaceTemplateRepositoryImpl implements SpaceTemplateRepository {

  private final SpaceTemplateMapper spaceTemplateMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final NextwikiConverter nextwikiConverter;

  @Override
  public int save(SpaceTemplateDTO dto) {
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    SpaceTemplateDO entity = nextwikiConverter.toSpaceTemplateDO(dto);
    return spaceTemplateMapper.insert(entity);
  }

  @Override
  public int update(SpaceTemplateDTO dto) {
    SpaceTemplateDO entity = nextwikiConverter.toSpaceTemplateDO(dto);
    return spaceTemplateMapper.updateById(entity);
  }

  @Override
  public Optional<SpaceTemplateDTO> findById(String id) {
    SpaceTemplateDO entity = spaceTemplateMapper.selectById(id);
    return Optional.ofNullable(entity).map(nextwikiConverter::toSpaceTemplateDTO);
  }

  @Override
  public List<SpaceTemplateDTO> findAvailableTemplates(String tenantId, String category) {
    List<SpaceTemplateDO> entities = spaceTemplateMapper.selectAvailableTemplates(tenantId, category);
    return entities.stream()
        .map(nextwikiConverter::toSpaceTemplateDTO)
        .collect(Collectors.toList());
  }

  @Override
  public List<SpaceTemplateDTO> findWithPage(String tenantId, String category, int offset, int limit) {
    List<SpaceTemplateDO> entities = spaceTemplateMapper.selectWithPage(tenantId, category, offset, limit);
    return entities.stream()
        .map(nextwikiConverter::toSpaceTemplateDTO)
        .collect(Collectors.toList());
  }

  @Override
  public int countByTenantId(String tenantId, String category) {
    return spaceTemplateMapper.countByCondition(tenantId, category);
  }

  @Override
  public int incrementUsageCount(String id) {
    SpaceTemplateDO entity = spaceTemplateMapper.selectById(id);
    if (entity != null) {
      int count = entity.getUsageCount() != null ? entity.getUsageCount() : 0;
      entity.setUsageCount(count + 1);
      return spaceTemplateMapper.updateById(entity);
    }
    return 0;
  }

  @Override
  public int deleteById(String id) {
    return spaceTemplateMapper.deleteById(id);
  }
}
