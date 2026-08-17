package com.njydsz.system.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.AppInfo;
import com.njydsz.system.infra.mapper.AppInfoMapper;

/**
 * 应用信息仓储。
 *
 * <p>封装 {@link AppInfoMapper}，提供应用注册域的业务语义数据访问能力（P1-4 去透传化）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class AppInfoRepository {

  private final AppInfoMapper appInfoMapper;

  /**
   * 按应用 Key 查询启用的应用（含 appSecret，仅供密钥校验内部使用）。
   *
   * @param appKey 应用 Key
   * @return 应用实体；不存在返回 {@code null}
   */
  public AppInfo selectEnabledByAppKey(String appKey) {
    return appInfoMapper.selectEnabledByAppKey(appKey);
  }

  /**
   * 校验应用 Key 是否已存在。
   *
   * @param appKey 应用 Key
   * @return 已存在返回 {@code true}
   */
  public boolean existsByAppKey(String appKey) {
    Long count =
        appInfoMapper.selectCount(new QueryWrapper<AppInfo>().eq("app_key", appKey));
    return count != null && count > 0;
  }

  /**
   * 获取原生 Mapper（用于分页等动态条件查询场景）。
   *
   * @return 应用信息 Mapper
   */
  public AppInfoMapper getAppInfoMapper() {
    return appInfoMapper;
  }
}
