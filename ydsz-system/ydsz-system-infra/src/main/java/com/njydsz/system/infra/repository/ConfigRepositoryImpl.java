package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.ConfigDO;
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
            new LambdaQueryWrapper<ConfigDO>().eq(ConfigDO::getConfigKey, configKey).eq(ConfigDO::getDeleted, 0).last("LIMIT 1")))
        .map(converter::entityToVO);
  }

  @Override
  public List<ConfigVO> findEnabledByGroup(String configGroup) {
    return converter.configListToVO(configMapper.selectList(
        new LambdaQueryWrapper<ConfigDO>()
            .eq(ConfigDO::getConfigGroup, configGroup)
            .eq(ConfigDO::getStatus, STATUS_ENABLED)
            .orderByAsc(ConfigDO::getSortOrder)));
  }

  @Override
  public List<ConfigVO> findPublicEnabled() {
    return converter.configListToVO(configMapper.selectList(
        new LambdaQueryWrapper<ConfigDO>()
            .eq(ConfigDO::getIsPublic, 1)
            .eq(ConfigDO::getStatus, STATUS_ENABLED)
            .orderByAsc(ConfigDO::getSortOrder)));
  }

  @Override
  public boolean existsByGroupAndKey(String configGroup, String configKey) {
    Long count =
        configMapper.selectCount(
            new LambdaQueryWrapper<ConfigDO>()
                .eq(ConfigDO::getConfigGroup, configGroup)
                .eq(ConfigDO::getConfigKey, configKey));
    return count != null && count > 0;
  }

  @Override
  public PageResponse<List<ConfigVO>> findByPage(ConfigPageQuery query) {
    Page<ConfigDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<ConfigDO> wrapper = new LambdaQueryWrapper<>();
    if (query.getConfigGroup() != null && !query.getConfigGroup().isBlank()) {
      wrapper.eq(ConfigDO::getConfigGroup, query.getConfigGroup());
    }
    if (query.getConfigKey() != null && !query.getConfigKey().isBlank()) {
      wrapper.like(ConfigDO::getConfigKey, query.getConfigKey());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(ConfigDO::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(ConfigDO::getCreatedAt);
    com.baomidou.mybatisplus.core.metadata.IPage<ConfigDO> result = configMapper.selectPage(page, wrapper);
    List<ConfigVO> vos = converter.configListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long)query.getPageNum(), (long)query.getPageSize(), vos);
  }

  @Override
  public boolean insert(ConfigDTO dto) {
    ConfigDO entity = converter.dtoToEntity(dto);
    return configMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(ConfigDTO dto) {
    ConfigDO entity = converter.dtoToEntityWithId(dto);
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
    List<ConfigDO> entities = converter.configDtosToEntities(dtos);
    return configMapper.insertBatch(entities) > 0;
  }

  @Override
  public List<ConfigVO> findForCursor(String configGroup, String configKey, String cursor, int limit) {
    LambdaQueryWrapper<ConfigDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ConfigDO::getDeleted, 0);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq(ConfigDO::getConfigGroup, configGroup);
    }
    if (configKey != null && !configKey.isBlank()) {
      wrapper.like(ConfigDO::getConfigKey, configKey);
    }
    if (cursor != null && !cursor.isBlank()) {
      wrapper.gt(ConfigDO::getId, cursor);
    }
    wrapper.orderByAsc(ConfigDO::getId);
    wrapper.last("LIMIT " + limit);
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public boolean existsAfterCursor(String configGroup, String configKey, String cursor) {
    LambdaQueryWrapper<ConfigDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ConfigDO::getDeleted, 0);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq(ConfigDO::getConfigGroup, configGroup);
    }
    if (configKey != null && !configKey.isBlank()) {
      wrapper.like(ConfigDO::getConfigKey, configKey);
    }
    if (cursor != null && !cursor.isBlank()) {
      wrapper.gt(ConfigDO::getId, cursor);
    }
    return configMapper.selectCount(wrapper) > 0;
  }

  @Override
  public List<ConfigVO> findForExport(String configGroup) {
    LambdaQueryWrapper<ConfigDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ConfigDO::getDeleted, 0);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq(ConfigDO::getConfigGroup, configGroup).orderByAsc(ConfigDO::getSortOrder);
    } else {
      wrapper.orderByAsc(ConfigDO::getConfigGroup, ConfigDO::getSortOrder);
    }
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public List<ConfigVO> findEnabledConfigs() {
    LambdaQueryWrapper<ConfigDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ConfigDO::getStatus, STATUS_ENABLED).eq(ConfigDO::getDeleted, 0);
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public List<ConfigVO> findByGroup(String configGroup) {
    LambdaQueryWrapper<ConfigDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ConfigDO::getConfigGroup, configGroup).eq(ConfigDO::getDeleted, 0);
    return converter.configListToVO(configMapper.selectList(wrapper));
  }


}
