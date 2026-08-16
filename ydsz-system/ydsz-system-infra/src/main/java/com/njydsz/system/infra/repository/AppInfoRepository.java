package com.njydsz.system.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.mapper.AppInfoMapper;

/**
 * 应用信息仓储。
 *
 * <p>封装 AppInfoMapper，提供应用注册数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class AppInfoRepository {

  private final AppInfoMapper appInfoMapper;

  /**
   * 获取原生 Mapper。
   *
   * @return 应用信息 Mapper
   */
  public AppInfoMapper getAppInfoMapper() {
    return appInfoMapper;
  }
}
