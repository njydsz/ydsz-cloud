package com.njydsz.pmis.common.permission;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * 权限本地缓存服务
 *
 * <p>使用 Caffeine 缓存用户权限列表，减少 Redis/DB 查询。
 *
 * <h3>缓存策略</h3>
 * <ul>
 *   <li>最大缓存 10000 个用户</li>
 *   <li>写入后 5 分钟过期</li>
 *   <li>支持手动失效（权限变更时）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
public class PermissionCacheService {

    /** 用户权限缓存：userId → 权限集合 */
    private final Cache<String, Set<String>> permissionCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /** 用户角色缓存：userId → 角色集合 */
    private final Cache<String, Set<String>> roleCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    /**
     * 获取用户权限列表（从缓存）
     *
     * @param userId 用户 ID
     * @return 权限集合；缓存未命中返回 null
     */
    public Set<String> getPermissions(String userId) {
        return permissionCache.getIfPresent(userId);
    }

    /**
     * 设置用户权限列表到缓存
     *
     * @param userId      用户 ID
     * @param permissions 权限集合
     */
    public void putPermissions(String userId, Set<String> permissions) {
        permissionCache.put(userId, permissions != null ? permissions : new HashSet<>());
    }

    /**
     * 获取用户角色列表（从缓存）
     *
     * @param userId 用户 ID
     * @return 角色集合；缓存未命中返回 null
     */
    public Set<String> getRoles(String userId) {
        return roleCache.getIfPresent(userId);
    }

    /**
     * 设置用户角色列表到缓存
     *
     * @param userId 用户 ID
     * @param roles  角色集合
     */
    public void putRoles(String userId, Set<String> roles) {
        roleCache.put(userId, roles != null ? roles : new HashSet<>());
    }

    /**
     * 失效用户权限缓存（权限变更时调用）
     *
     * @param userId 用户 ID
     */
    public void invalidate(String userId) {
        permissionCache.invalidate(userId);
        roleCache.invalidate(userId);
        log.info("[PermissionCache] 已失效用户权限缓存: userId={}", userId);
    }

    /**
     * 失效所有权限缓存
     */
    public void invalidateAll() {
        permissionCache.invalidateAll();
        roleCache.invalidateAll();
        log.info("[PermissionCache] 已失效全部权限缓存");
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计摘要
     */
    public String getStats() {
        return String.format("permissionCache{size=%d, hits=%d, misses=%d, hitRate=%.2f%%}",
                permissionCache.estimatedSize(),
                permissionCache.stats().hitCount(),
                permissionCache.stats().missCount(),
                permissionCache.stats().hitRate() * 100);
    }
}
