package com.njydsz.common.auth.service;

import java.util.Map;
import java.util.Set;

import com.njydsz.common.auth.model.DataScopeInfo;
import com.njydsz.common.cache.api.Cache;

/**
 * 行级数据权限解析器接口。
 *
 * <p>负责根据当前调用链上下文解析"当前用户在当前请求中的数据权限范围"。
 *
 * <p>提供两种实现：
 *
 * <ul>
 *   <li>{@link com.njydsz.common.auth.service.impl.RedisRoleDataPermissionResolver} — Redis 模式（默认，性能优先）
 *   <li>{@link com.njydsz.common.auth.service.impl.LocalRoleDataPermissionResolver} — 本地缓存兜底模式（Redis 不可用时的降级策略）
 * </ul>
 *
 * <p><b>实现类注意事项：</b>
 *
 * <ul>
 *   <li>解析结果应考虑多角色合并场景
 *   <li>应对解析结果做本地 TTL 缓存，降低数据源访问频率
 *   <li>解析失败时应返回空对象而非 null，保证调用方稳定
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see DataScopeInfo
 * @see com.njydsz.common.auth.aspect.AuthRowPermissionAspect
 */
public interface DataPermissionResolver {

  /**
   * 解析当前用户的数据权限范围。
   *
   * @return 数据权限范围信息，无权限时返回空的 {@link DataScopeInfo}
   */
  DataScopeInfo resolve();

  /**
   * 根据用户信息 Map 解析数据权限范围。
   *
   * @param userInfo 用户信息 Map
   * @return 数据权限范围信息，无角色时返回空的 {@link DataScopeInfo}
   */
  DataScopeInfo resolveByUserInfo(Map<String, Object> userInfo);

  /**
   * 根据角色编码集合解析数据权限范围。
   *
   * @param roleCodes 角色编码集合
   * @return 合并后的数据权限范围信息
   */
  DataScopeInfo resolveByRoles(Set<String> roleCodes);

  /**
   * 使指定角色的数据权限缓存失效。
   *
   * @param roleCode 角色编码
   */
  void invalidate(String roleCode);

  /** 使所有数据权限缓存失效。 */
  void invalidateAll();

  /**
   * 获取数据权限本地缓存实例。
   *
   * @return 本地缓存实例
   */
  Cache<String, DataScopeInfo> getCache();
}
