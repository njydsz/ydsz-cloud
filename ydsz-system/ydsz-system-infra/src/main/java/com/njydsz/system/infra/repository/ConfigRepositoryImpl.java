package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.infra.repository.ConfigRepository;
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
 *   <li>返回领域实体，由 Service 层负责转换为 VO
 *   <li>分页等动态条件查询封装为 {@link #findByPage} 方法
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

  @Override
  public Optional<Config> findEnabledByKey(String configKey) {
    return Optional.ofNullable(configMapper.selectByConfigKey(configKey));
  }

  @Override
  public Optional<Config> findByKeyIgnoreStatus(String configKey) {
    return Optional.ofNullable(
        configMapper.selectOne(
            new QueryWrapper<Config>().eq("config_key", configKey).eq("deleted", 0).last("LIMIT 1")));
  }

  @Override
  public List<Config> findEnabledByGroup(String configGroup) {
    return configMapper.selectList(
        new QueryWrapper<Config>()
            .eq("config_group", configGroup)
            .eq("status", STATUS_ENABLED)
            .orderByAsc("sort_order"));
  }

  @Override
  public List<Config> findPublicEnabled() {
    return configMapper.selectList(
        new QueryWrapper<Config>()
            .eq("is_public", 1)
            .eq("status", STATUS_ENABLED)
            .orderByAsc("sort_order"));
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
  public IPage<Config> findByPage(
      Page<Config> page, String configGroup, String configKey, String status) {
    QueryWrapper<Config> wrapper = new QueryWrapper<>();
    if (configGroup != null && !configGroup.isBlank()) {
      wrapper.eq("config_group", configGroup);
    }
    if (configKey != null && !configKey.isBlank()) {
      wrapper.like("config_key", configKey);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq("status", status);
    }
    wrapper.orderByDesc("created_at");
    return configMapper.selectPage(page, wrapper);
  }

  @Override
  public boolean insert(Config entity) {
    return configMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(Config entity) {
    return configMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return configMapper.deleteById(id) > 0;
  }

  @Override
  public Optional<Config> findById(String id) {
    return Optional.ofNullable(configMapper.selectById(id));
  }

  @Override
  public boolean insertBatch(List<Config> entities) {
    return configMapper.insertBatch(entities) > 0;
  }

  @Override
  public List<Config> findList(QueryWrapper<Config> wrapper) {
    return configMapper.selectList(wrapper);
  }

  @Override
  public long findCount(QueryWrapper<Config> wrapper) {
    Long count = configMapper.selectCount(wrapper);
    return count != null ? count : 0L;
  }
}
