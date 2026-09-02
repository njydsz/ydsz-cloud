package com.njydsz.userinfo.server.auth;

import java.time.Duration;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.repository.UserRoleRepository;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.metrics.UserInfoMetrics;

/**
 * 用户角色缓存服务。
 *
 * <p>负责用户 → 角色列表的加载与缓存（Redis，TTL 10 分钟），角色分配变更时主动失效。 从 {@link AuthServiceImpl}
 * 拆分（P0-5），聚焦「角色加载与缓存一致性」单一职责。
 *
 * <p><b>Redis Key 设计：</b>{@code userinfo:roles:{userId}} → Hash{roles: List&lt;Role&gt;}
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see UserRole 用户-角色关联实体
 * @see Role 角色实体
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleCacheService {

  /** 用户角色缓存 Redis Key 前缀：userinfo:roles:{userId} */
  private static final String USER_ROLES_KEY_PREFIX = "userinfo:roles:";

  /** 用户角色缓存 TTL（秒）：10 分钟 */
  private static final long USER_ROLES_CACHE_TTL = 600L;

  private final UserRoleRepository userRoleRepository;
  private final RoleRepository roleRepository;
  private final RedisHashOps redisHashOps;
  private final RedisStringOps redisStringOps;
  private final UserInfoMetrics userInfoMetrics;
  private final UserInfoProperties properties;

  /**
   * 按 user_role 关联表查询用户角色（带 Redis 缓存）。
   *
   * @param userId 用户 ID
   * @return 用户持有的有效角色列表，无角色时返回空列表
   */
  public List<RoleVO> loadUserRoles(String userId) {
    // 1. 尝试从 Redis 缓存读取
    String cacheKey = USER_ROLES_KEY_PREFIX + userId;
    try {
      List<RoleVO> cachedRoles = redisHashOps.hGet(cacheKey, "roles", List.class);
      if (cachedRoles != null && !cachedRoles.isEmpty()) {
        log.debug("User roles cache hit: userId={}", userId);
        userInfoMetrics.recordCacheResult("roles_cache_total", "hit");
        return cachedRoles;
      }
    } catch (Exception e) {
      log.warn("Failed to read user roles cache: userId={}, error={}", userId, e.getMessage(), e);
    }

    // 2. 缓存未命中，查询数据库
    userInfoMetrics.recordCacheResult("roles_cache_total", "miss");
    List<RoleVO> roles = loadUserRolesFromDb(userId);

    // 3. 写入 Redis 缓存
    if (!roles.isEmpty()) {
      try {
        redisHashOps.hSet(cacheKey, "roles", roles);
        redisStringOps.expire(cacheKey, Duration.ofSeconds(USER_ROLES_CACHE_TTL));
        log.debug("User roles cached: userId={}, count={}", userId, roles.size());
      } catch (Exception e) {
        log.warn("Failed to cache user roles: userId={}, error={}", userId, e.getMessage(), e);
      }
    }
    return roles;
  }

  /**
   * 失效指定用户的角色缓存（角色分配变更时调用）。
   *
   * @param userId 用户 ID，为空时忽略
   */
  public void evictUserRolesCache(String userId) {
    if (userId == null || userId.isBlank()) {
      return;
    }
    try {
      redisStringOps.del(USER_ROLES_KEY_PREFIX + userId);
      log.info("User roles cache evicted: userId={}", userId);
    } catch (Exception e) {
      log.warn("Failed to evict user roles cache: userId={}, error={}", userId, e.getMessage(), e);
    }
  }

  /**
   * 从数据库查询用户角色。
   *
   * @param userId 用户 ID
   * @return 用户持有的有效角色列表
   */
  private List<RoleVO> loadUserRolesFromDb(String userId) {
    List<String> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
    if (roleIds.isEmpty()) {
      return List.of();
    }
    return roleRepository.findByIds(roleIds);
  }
}
