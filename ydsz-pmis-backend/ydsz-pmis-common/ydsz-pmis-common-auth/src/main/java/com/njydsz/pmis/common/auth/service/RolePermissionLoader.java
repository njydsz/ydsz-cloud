package com.njydsz.pmis.common.auth.service;

import com.njydsz.pmis.common.auth.model.RolePermissions;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import com.njydsz.pmis.common.auth.service.impl.RedisRolePermissionLoader;

/**
 * 角色权限加载器接口。
 *
 * <p>定义根据 roleCode 加载角色权限集合的行为。
 * 数据来源可为 Redis/DB/远程权限中心等。
 *
 * <p><b>实现类职责：</b>
 * <ul>
 *   <li>根据 roleCode 加载该角色的三类权限集合（菜单/按钮/接口）</li>
 *   <li>实现应考虑缓存策略，提高频繁权限校验的性能</li>
 *   <li>返回空权限而非 null，保证调用方稳定</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 默认实现：基于 Redis
 * &#64;Bean
 * public RolePermissionLoader rolePermissionLoader(RedisService redisService) {
 *     return new RedisRolePermissionLoader(redisService);
 * }
 *
 * // 自定义实现：来自数据库
 * &#64;Bean
 * public RolePermissionLoader rolePermissionLoader(RoleService roleService) {
 *     return new DbRolePermissionLoader(roleService);
 * }
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see com.njydsz.pmis.common.auth.model.RolePermissions
 * @see RedisRolePermissionLoader
 */
public interface RolePermissionLoader {

    /**
     * 加载指定角色的权限集合。
     *
     * @param roleCode 角色编码
     * @return 权限集合；不可返回 null，至少返回 {@link RolePermissions#empty()}
     */
    RolePermissions loadByRoleCode(String roleCode);

    /**
     * 批量加载多个角色的权限集合。
     *
     * <p>默认实现逐个调用 {@link #loadByRoleCode(String)}，实现类可覆盖此方法以使用
     * Redis MGET/Pipeline 等批量操作，将 N 次 Redis 往返减少为 1-2 次。
     *
     * @param roleCodes 角色编码集合
     * @return 角色编码 → 权限集合的映射；不可返回 null，未加载的角色不会包含在结果中
     */
    default Map<String, RolePermissions> loadByRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, RolePermissions> result = new HashMap<>(roleCodes.size());
        for (String roleCode : roleCodes) {
            if (roleCode != null && !roleCode.isBlank()) {
                result.put(roleCode, loadByRoleCode(roleCode));
            }
        }
        return result;
    }
}
