package com.njydsz.system.infra.repository;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.repository.ConfigRepository;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.infra.mapper.ConfigMapper;




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
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class ConfigRepositoryImpl implements ConfigRepository {

  /** 状态常量：启用 */
  private static final String STATUS_ENABLED = "ENABLED";

  /** 逻辑删除标志：未删除 */
  private static final int NOT_DELETED = 0;

  /** 公开配置标志：公开 */
  private static final int PUBLIC_CONFIG = 1;

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
                new LambdaQueryWrapper<Config>()
                    .eq(Config::getConfigKey, configKey)
                    .eq(Config::getDeleted, NOT_DELETED)
                    .last("LIMIT 1")))
        .map(converter::entityToVO);
  }

  @Override
  public List<ConfigVO> findEnabledByGroup(String configGroup) {
    return converter.configListToVO(configMapper.selectList(
        new LambdaQueryWrapper<Config>()
            .eq(Config::getConfigGroup, configGroup)
            .eq(Config::getStatus, STATUS_ENABLED)
            .orderByAsc(Config::getSortOrder)));
  }

  @Override
  public List<ConfigVO> findPublicEnabled() {
    return converter.configListToVO(configMapper.selectList(
        new LambdaQueryWrapper<Config>()
            .eq(Config::getIsPublic, PUBLIC_CONFIG)
            .eq(Config::getStatus, STATUS_ENABLED)
            .orderByAsc(Config::getSortOrder)));
  }

  @Override
  public boolean existsByGroupAndKey(String configGroup, String configKey) {
    Long count =
        configMapper.selectCount(
            new LambdaQueryWrapper<Config>()
                .eq(Config::getConfigGroup, configGroup)
                .eq(Config::getConfigKey, configKey));
    return count != null && count > 0;
  }

  @Override
  public PageResponse<List<ConfigVO>> findByPage(ConfigPageQuery query) {
    Page<Config> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
    if (query.getConfigGroup() != null && !query.getConfigGroup().isBlank()) {
      wrapper.eq(Config::getConfigGroup, query.getConfigGroup());
    }
    if (query.getConfigKey() != null && !query.getConfigKey().isBlank()) {
      wrapper.like(Config::getConfigKey, query.getConfigKey());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(Config::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(Config::getCreatedAt);
    IPage<Config> result = configMapper.selectPage(page, wrapper);
    List<ConfigVO> vos = converter.configListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  @Override
  public boolean insert(ConfigDTO dto) {
    Config entity = converter.dtoToEntity(dto);
    boolean success = configMapper.insert(entity) > 0;
    // MyBatis-Plus 回填 snowflake ID 到 entity，需同步回 DTO
    if (success && entity.getId() != null) {
      dto.setId(entity.getId());
    }
    return success;
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
    LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Config::getDeleted, NOT_DELETED);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq(Config::getConfigGroup, configGroup);
    }
    if (configKey != null && !configKey.isBlank()) {
      wrapper.like(Config::getConfigKey, configKey);
    }
    if (cursor != null && !cursor.isBlank()) {
      wrapper.gt(Config::getId, cursor);
    }
    wrapper.orderByAsc(Config::getId);
    wrapper.last("LIMIT " + limit);
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public boolean existsAfterCursor(String configGroup, String configKey, String cursor) {
    LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Config::getDeleted, NOT_DELETED);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq(Config::getConfigGroup, configGroup);
    }
    if (configKey != null && !configKey.isBlank()) {
      wrapper.like(Config::getConfigKey, configKey);
    }
    if (cursor != null && !cursor.isBlank()) {
      wrapper.gt(Config::getId, cursor);
    }
    return configMapper.selectCount(wrapper) > 0;
  }

  @Override
  public List<ConfigVO> findForExport(String configGroup) {
    LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Config::getDeleted, NOT_DELETED);
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq(Config::getConfigGroup, configGroup).orderByAsc(Config::getSortOrder);
    } else {
      wrapper.orderByAsc(Config::getConfigGroup, Config::getSortOrder);
    }
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public List<ConfigVO> findEnabledConfigs() {
    LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Config::getStatus, STATUS_ENABLED).eq(Config::getDeleted, NOT_DELETED);
    return converter.configListToVO(configMapper.selectList(wrapper));
  }

  @Override
  public List<ConfigVO> findByGroup(String configGroup) {
    LambdaQueryWrapper<Config> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Config::getConfigGroup, configGroup).eq(Config::getDeleted, NOT_DELETED);
    return converter.configListToVO(configMapper.selectList(wrapper));
  }


}
