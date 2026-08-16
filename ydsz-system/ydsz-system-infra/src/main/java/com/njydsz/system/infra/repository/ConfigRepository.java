package com.njydsz.system.infra.repository;

import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

import com.njydsz.system.infra.mapper.ConfigMapper;

/**
 * 系统配置仓储。
 *
 * <p>封装 ConfigMapper，提供配置数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class ConfigRepository {

  private final ConfigMapper configMapper;

  /**
   * 获取原生 Mapper（用于自定义 SQL 方法）。
   *
   * @return 系统配置 Mapper
   */
  public ConfigMapper getConfigMapper() {
    return configMapper;
  }
}
