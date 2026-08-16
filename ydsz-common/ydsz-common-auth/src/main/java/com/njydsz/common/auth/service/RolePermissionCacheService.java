package com.njydsz.common.auth.service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;

/**
 * 角色权限缓存服务。
 *
 * <p>封装角色权限缓存的全部管理职责，提供：
 *
 * <ul>
 *   <li>缓存查询：{@link #getCachedPermissions} 获取已缓存的权限集合
 *   <li>缓存写入：{@link #cachePermissions} 将权限集合写入缓存并维护反向索引
 *   <li>缓存失效：{@link #invalidateByRoleCodes} 按角色编码精确清理， {@link #invalidateAll} 清理全部缓存
 * </ul>
 *
 * <p>内部维护 {@code roleCode → Set&lt;cacheKey&gt;} 反向索引， 确保即使缓存 Key 使用不可逆的 Hash 算法，也能按角色精确定位并清理缓存条目。
 *
 * <p>使用 ydsz-common-cache 的 {@link Cache} 接口，配合 {@link RolePermissionsExpiry} 实现基于条目内容的动态 TTL（空权限短
 * TTL 防穿透，非空权限加随机抖动防雪崩）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RolePermissionsExpiry
 */
@Slf4j
public class RolePermissionCacheService {

  /** 角色权限缓存存储。 */
  private final Cache<String, RolePermissions> cache;

  /**
   * roleCode → cacheKey 的反向索引，用于按角色清理缓存。
   *
   * <p>由于 cacheKey 使用 SHA-256 Hash，无法从 Key 反解角色， 需维护此映射以实现精确的按角色失效。
   */
  private final Map<String, Set<String>> roleToCacheKeyIndex = new ConcurrentHashMap<>();

  /**
   * 构造角色权限缓存服务。
   *
   * @param properties 认证配置属性，用于获取缓存容量上限等参数
   * @throws NullPointerException 当 {@code properties} 为 {@code null} 时抛出
   */
  public RolePermissionCacheService(AuthProperties properties) {
    Objects.requireNonNull(properties, "AuthProperties cannot be null");
    this.cache =
        YdszCache.<String, RolePermissions>newBuilder()
            .type(CacheType.STRIPED)
            .name("auth:role-permissions")
            .maximumSize(properties.getPermissionCacheMaxSize())
            .expireAfter(new RolePermissionsExpiry(properties))
            .build();
  }

  /**
   * 获取已缓存的角色权限。
   *
   * @param cacheKey 缓存键
   * @return 已缓存的 {@link RolePermissions}，未命中时返回 {@code null}
   */
  public RolePermissions getCachedPermissions(String cacheKey) {
    Objects.requireNonNull(cacheKey, "cacheKey cannot be null");
    return cache.getIfPresent(cacheKey);
  }

  /**
   * 将角色权限写入缓存，并维护 roleCode → cacheKey 的反向索引。
   *
   * <p>应在 Redis 可用时调用，避免 Redis 故障期间的空权限被缓存毒化。
   *
   * @param cacheKey 缓存键
   * @param roleCodes 该缓存条目关联的角色编码集合，用于反向索引
   * @param permissions 待缓存的角色权限
   * @throws NullPointerException 当任一参数为 {@code null} 时抛出
   */
  public void cachePermissions(
      String cacheKey, Set<String> roleCodes, RolePermissions permissions) {
    Objects.requireNonNull(cacheKey, "cacheKey cannot be null");
    Objects.requireNonNull(roleCodes, "roleCodes cannot be null");
    Objects.requireNonNull(permissions, "permissions cannot be null");

    cache.put(cacheKey, permissions);
    for (String roleCode : roleCodes) {
      roleToCacheKeyIndex
          .computeIfAbsent(roleCode, k -> ConcurrentHashMap.newKeySet())
          .add(cacheKey);
    }
  }

  /**
   * 按角色编码集合精确清理缓存。
   *
   * <p>通过 roleCode → cacheKey 反向索引定位所有关联的缓存条目并使其失效， 同时从索引中移除对应的角色映射。
   *
   * @param roleCodes 需要清理缓存的角色编码集合，不可为 {@code null}
   */
  public void invalidateByRoleCodes(Set<String> roleCodes) {
    Objects.requireNonNull(roleCodes, "roleCodes cannot be null");
    for (String roleCode : roleCodes) {
      Set<String> cacheKeys = roleToCacheKeyIndex.remove(roleCode);
      if (cacheKeys != null) {
        for (String cacheKey : cacheKeys) {
          cache.invalidate(cacheKey);
        }
      }
    }
  }

  /** 清理全部角色权限缓存及反向索引。 */
  public void invalidateAll() {
    cache.invalidateAll();
    roleToCacheKeyIndex.clear();
  }

  /** 销毁时清理缓存。 */
  @PreDestroy
  public void destroy() {
    cache.invalidateAll();
    roleToCacheKeyIndex.clear();
    log.info("[RolePermissionCacheService] 缓存已清理");
  }
}
