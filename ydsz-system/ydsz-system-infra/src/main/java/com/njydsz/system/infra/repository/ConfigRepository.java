package com.njydsz.system.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.Config;
import com.njydsz.system.infra.mapper.ConfigMapper;

/**
 * 系统配置仓储。
 *
 * <p>封装 {@link ConfigMapper}，提供配置域的业务语义数据访问能力（P1-4 去透传化）：
 * 按 key / 按分组 / 公开配置等高频查询以语义方法暴露，Service 层不再直接拼接 {@link QueryWrapper}。
 * 分页等动态条件查询通过 {@link #getConfigMapper()} 兜底（MyBatis-Plus 动态查询场景）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ConfigRepository {

  /** 状态常量：启用 */
  public static final String STATUS_ENABLED = "ENABLED";

  private final ConfigMapper configMapper;

  /**
   * 按配置键查询启用的配置项（走 {@code uk_config_group_key} 索引）。
   *
   * @param configKey 配置键
   * @return 配置实体；不存在返回 {@code null}
   */
  public Config selectEnabledByKey(String configKey) {
    return configMapper.selectByConfigKey(configKey);
  }

  /**
   * 按配置键查询配置项（不区分状态，用于版本快照 / 回滚定位）。
   *
   * @param configKey 配置键
   * @return 配置实体；不存在返回 {@code null}
   */
  public Config selectByKeyIgnoreStatus(String configKey) {
    return configMapper.selectOne(
        new QueryWrapper<Config>().eq("config_key", configKey).eq("deleted", 0).last("LIMIT 1"));
  }

  /**
   * 按分组查询启用状态配置（按 sortOrder 升序）。
   *
   * @param configGroup 配置分组
   * @return 启用配置列表
   */
  public List<Config> selectEnabledByGroup(String configGroup) {
    return configMapper.selectList(
        new QueryWrapper<Config>()
            .eq("config_group", configGroup)
            .eq("status", STATUS_ENABLED)
            .orderByAsc("sort_order"));
  }

  /**
   * 查询全部公开配置（按 sortOrder 升序）。
   *
   * @return 公开配置列表
   */
  public List<Config> selectPublicEnabled() {
    return configMapper.selectList(
        new QueryWrapper<Config>()
            .eq("is_public", 1)
            .eq("status", STATUS_ENABLED)
            .orderByAsc("sort_order"));
  }

  /**
   * 校验同分组下配置键是否已存在。
   *
   * @param configGroup 配置分组
   * @param configKey 配置键
   * @return 已存在返回 {@code true}
   */
  public boolean existsByGroupAndKey(String configGroup, String configKey) {
    Long count =
        configMapper.selectCount(
            new QueryWrapper<Config>()
                .eq("config_group", configGroup)
                .eq("config_key", configKey));
    return count != null && count > 0;
  }

  /**
   * 获取原生 Mapper（用于分页等动态条件查询场景）。
   *
   * @return 系统配置 Mapper
   */
  public ConfigMapper getConfigMapper() {
    return configMapper;
  }
}
