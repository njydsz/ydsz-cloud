package com.njydsz.common.auth.service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.cache.support.Expiry;

/**
 * 角色权限缓存过期策略。
 *
 * <p>实现基于条目内容的动态 TTL：
 *
 * <ul>
 *   <li>空权限（menu/button/api 全部为空）使用较短 TTL（{@code permissionCacheNullTtlSeconds}）， 防止缓存穿透
 *   <li>非空权限使用配置的 TTL（{@code permissionCacheTtlSeconds}）± 随机抖动， 避免大量缓存同时过期导致雪崩
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class RolePermissionsExpiry implements Expiry<String, RolePermissions> {

  private final AuthProperties properties;

  public RolePermissionsExpiry(AuthProperties properties) {
    this.properties = properties;
  }

  @Override
  public long expireAfterCreate(String key, RolePermissions value, long currentTimeNanos) {
    if (isEmpty(value)) {
      long nullTtl = properties.getPermissionCacheNullTtlSeconds();
      return TimeUnit.SECONDS.toNanos(nullTtl);
    }
    long baseTtl = properties.getPermissionCacheTtlSeconds();
    int jitterPercent = properties.getPermissionCacheTtlJitterPercent();
    long effectiveTtl = applyJitter(baseTtl, jitterPercent);
    return TimeUnit.SECONDS.toNanos(effectiveTtl);
  }

  private boolean isEmpty(RolePermissions rp) {
    if (rp == null) {
      return true;
    }
    return rp.getMenuPermissions().isEmpty()
        && rp.getButtonPermissions().isEmpty()
        && rp.getApiPermissions().isEmpty();
  }

  private long applyJitter(long baseTtlSeconds, int jitterPercent) {
    if (jitterPercent <= 0) {
      return baseTtlSeconds;
    }
    long maxJitter = baseTtlSeconds * jitterPercent / 100;
    long jitter = ThreadLocalRandom.current().nextLong(-maxJitter, maxJitter + 1);
    return baseTtlSeconds + jitter;
  }
}
