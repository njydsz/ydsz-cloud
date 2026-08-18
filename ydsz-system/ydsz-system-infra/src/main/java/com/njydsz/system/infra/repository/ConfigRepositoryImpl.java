package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.Config;
import com.njydsz.system.infra.mapper.ConfigMapper;
import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;

/**
 * 系统配置仓储实现（Infra 层）。
 *
 * <p>实现 {@link ConfigRepository} 接口，封装 {@link ConfigMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link SystemConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link SystemConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Repository
@RequiredArgsConstructor
public class ConfigRepositoryImpl implements ConfigRepository {

  /** 状态常量：启用 */
  private static final String STATUS_ENABLED = "ENABLED";

  private final ConfigMapper configMapper;

  private final SystemConverter converter;

  @Override
  public Optional<ConfigVO> findEnabledByKey(String configKey) {
    return Optional.ofNullable(configMapper.selectByConfigKey(configKey))
        .map(converter::entityToVO);
  }

  @Override
  public Optional<ConfigVO> findByKeyIgnoreStatus(String configKey) {
    return Optional.ofNullable(
        configMapper.selectOne(
            new QueryWrapper<Config>().eq("config_key", configKey).eq("deleted", 0).last("LIMIT 1")))
        .map(converter::entityToVO);
  }

  @Override
  public List<ConfigVO> findEnabledByGroup(String configGroup) {
    return converter.configListToVO(configMapper.selectList(
        new QueryWrapper<Config>()
            .eq("config_group", configGroup)
            .eq("status", STATUS_ENABLED)
            .orderByAsc("sort_order")));
  }

  @Override
  public List<ConfigVO> findPublicEnabled() {
    return converter.configListToVO(configMapper.selectList(
        new QueryWrapper<Config>()
            .eq("is_public", 1)
            .eq("status", STATUS_ENABLED)
            .orderByAsc("sort_order")));
  }

  @Override
  public boolean existsByGroupAndKey(String configGroup, String configKey) {
    Long count =
        configMapper.selectCount(
            new QueryWrapper<Config>()
                .eq("config_group", configGroup)
                .eq("config_key", configKey));
    return count != null && count > 0;
  }

  @Override
  public IPage<ConfigVO> findByPage(ConfigPageQuery query) {
    Page<Config> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    if (query.getConfigGroup() != null && !query.getConfigGroup().isBlank()) {
      wrapper.eq("config_group", query.getConfigGroup());
    }
    if (query.getConfigKey() != null && !query.getConfigKey().isBlank()) {
      wrapper.like("config_key", query.getConfigKey());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.orderByDesc("created_at");
    IPage<Config> entityPage = configMapper.selectPage(page, wrapper);
    // DO → VO 转换
    List<ConfigVO> vos = converter.configListToVO(entityPage.getRecords());
    Page<ConfigVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    voPage.setRecords(vos);
    return voPage;
  }

  @Override
  public boolean insert(ConfigDTO dto) {
    Config entity = converter.dtoToEntity(dto);
    return configMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(ConfigDTO dto) {
    Config entity = converter.dtoToEntityWithId(dto);
    return configMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return configMapper.deleteById(id) > 0;
  }

  @Override
  public Optional<ConfigVO> findById(String id) {
    return Optional.ofNullable(configMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public boolean insertBatch(List<ConfigDTO> dtos) {
    List<Config> entities = converter.configDtosToEntities(dtos);
    return configMapper.insertBatch(entities) > 0;
  }

  @Override
  public List<ConfigVO> findForCursor(String configGroup, String configKey, String cursor, int limit) {
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq("config_group", configGroup);
    }
    if (configKey != null && !configKey.isBlank()) {
      wrapper.like("config_key", configKey);
    }
    if (cursor != null && !cursor.isBlank()) {
      wrapper.gt("id", cursor);
    }
    wrapper.orderByAsc("id");
    wrapper.last("LIMIT " + limit);
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public boolean existsAfterCursor(String configGroup, String configKey, String cursor) {
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq("config_group", configGroup);
    }
    if (configKey != null && !configKey.isBlank()) {
      wrapper.like("config_key", configKey);
    }
    if (cursor != null && !cursor.isBlank()) {
      wrapper.gt("id", cursor);
    }
    return configMapper.selectCount(wrapper) > 0;
  }

  @Override
  public List<ConfigVO> findForExport(String configGroup) {
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq("config_group", configGroup).orderByAsc("sort_order");
    } else {
      wrapper.orderByAsc("config_group", "sort_order");
    }
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public List<ConfigVO> findEnabledConfigs() {
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    wrapper.eq("status", STATUS_ENABLED).eq("deleted", 0);
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public List<ConfigVO> findByGroup(String configGroup) {
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    wrapper.eq("config_group", configGroup).eq("deleted", 0);
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public List<ConfigVO> findAll() {
    return converter.configListToVO(
        configMapper.selectList(new QueryWrapper<Config>().eq("deleted", 0)));
  }

  @Override
  public List<ConfigVO> findByTenantId(String tenantId) {
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    wrapper.eq("deleted", 0);
    if (tenantId != null && !tenantId.isBlank()) {
      wrapper.eq("tenant_id", tenantId);
    }
    return converter.configListToVO(configMapper.selectList(wrapper));
  }
}
