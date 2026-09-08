package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.SpaceTemplateDTO;
import com.njydsz.nextwiki.domain.entity.SpaceTemplate;
import com.njydsz.nextwiki.domain.repository.SpaceTemplateRepository;
import com.njydsz.nextwiki.infra.mapper.SpaceTemplateMapper;

/**
 * 空间模板仓储实现
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SpaceTemplateRepositoryImpl implements SpaceTemplateRepository {

  private final SpaceTemplateMapper spaceTemplateMapper;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final NextwikiStructMapper mapper;

  @Override
  public int save(SpaceTemplateDTO dto) {
    if (dto.getId() == null || dto.getId().isEmpty()) {
      dto.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    SpaceTemplate entity = mapper.spaceTemplateToEntity(dto);
    return spaceTemplateMapper.insert(entity);
  }

  @Override
  public int update(SpaceTemplateDTO dto) {
    SpaceTemplate entity = mapper.spaceTemplateToEntity(dto);
    return spaceTemplateMapper.updateById(entity);
  }

  /**
   * 按模板 ID 查询单个模板。
   *
   * @param id 模板 ID
   * @return 模板 DTO（可能为空）
   */
  @Override
  public Optional<SpaceTemplateDTO> findById(String id) {
    SpaceTemplate entity = spaceTemplateMapper.selectById(id);
    return Optional.ofNullable(entity).map(mapper::spaceTemplateToDTO);
  }

  /**
   * 查询可用模板列表（系统公开模板 + 租户自定义模板）。
   *
   * @param tenantId 租户 ID
   * @param category 模板分类（可选）
   * @return 模板 DTO 列表
   */
  @Override
  public List<SpaceTemplateDTO> findAvailableTemplates(String tenantId, String category) {
    List<SpaceTemplate> entities = spaceTemplateMapper.selectAvailableTemplates(tenantId, category);
    return entities.stream()
        .map(mapper::spaceTemplateToDTO)
        .collect(Collectors.toList());
  }

  /**
   * 分页查询模板列表（按租户和分类筛选）。
   *
   * @param tenantId 租户 ID
   * @param category 模板分类
   * @param offset 分页偏移量
   * @param limit 每页条数
   * @return 模板 DTO 列表
   */
  @Override
  public List<SpaceTemplateDTO> findWithPage(String tenantId, String category, int offset, int limit) {
    List<SpaceTemplate> entities = spaceTemplateMapper.selectWithPage(tenantId, category, offset, limit);
    return entities.stream()
        .map(mapper::spaceTemplateToDTO)
        .collect(Collectors.toList());
  }

  /**
   * 统计租户下的模板数量。
   *
   * @param tenantId 租户 ID
   * @param category 模板分类
   * @return 模板数量
   */
  @Override
  public int countByTenantId(String tenantId, String category) {
    return spaceTemplateMapper.countByCondition(tenantId, category);
  }

  /**
   * 递增模板使用次数（使用模板创建空间时调用）。
   *
   * @param id 模板 ID
   * @return 更新记录数
   */
  @Override
  public int incrementUsageCount(String id) {
    SpaceTemplate entity = spaceTemplateMapper.selectById(id);
    if (entity != null) {
      int count = entity.getUsageCount() != null ? entity.getUsageCount() : 0;
      entity.setUsageCount(count + 1);
      return spaceTemplateMapper.updateById(entity);
    }
    return 0;
  }

  /**
   * 删除模板（仅自定义模板可删除，系统模板校验由 service 层处理）。
   *
   * @param id 模板 ID
   * @return 更新记录数
   */
  @Override
  public int deleteById(String id) {
    return spaceTemplateMapper.deleteById(id);
  }
}
