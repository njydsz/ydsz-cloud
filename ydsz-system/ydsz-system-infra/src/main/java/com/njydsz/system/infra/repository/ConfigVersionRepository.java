package com.njydsz.system.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.mapper.ConfigVersionMapper;

/**
 * 配置版本快照仓储。
 *
 * <p>封装 ConfigVersionMapper，提供配置版本数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ConfigVersionRepository {

  private final ConfigVersionMapper configVersionMapper;

  /**
   * 获取原生 Mapper。
   *
   * @return 配置版本 Mapper
   */
  public ConfigVersionMapper getConfigVersionMapper() {
    return configVersionMapper;
  }
}
