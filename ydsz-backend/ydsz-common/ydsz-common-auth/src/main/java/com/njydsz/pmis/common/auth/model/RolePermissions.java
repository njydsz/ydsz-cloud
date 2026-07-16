package com.njydsz.common.auth.model;

import java.util.Collections;
import java.util.Set;

import com.njydsz.common.auth.service.RbacPermissionEvaluator;
import com.njydsz.common.auth.service.RolePermissionLoader;

/**
 * 角色权限聚合类。
 *
 * <p>封装单个角色的三类权限集合：
 * <ul>
 *   <li>菜单权限（menuPermissions）：用于控制用户可见的菜单入口</li>
 *   <li>按钮权限（buttonPermissions）：用于控制用户可执行的操作按钮</li>
 *   <li>接口权限（apiPermissions）：用于控制接口级别的访问权限</li>
 * </ul>
 *
 * <p>该类是不可变的（Immutable），所有字段在构造后不可修改，
 * 适合在多角色权限合并场景下安全使用。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>RolePermissionLoader 根据 roleCode 加载单个角色的权限</li>
 *   <li>RbacPermissionEvaluator 合并多角色权限时创建新的 RolePermissions</li>
 * </ul>
 *
 * @since 1.0.0
 * 
 * @see RolePermissionLoader
 * @see RbacPermissionEvaluator
 */
public final class RolePermissions {

    private final Set<String> menuPermissions;
    private final Set<String> buttonPermissions;
    private final Set<String> apiPermissions;

    /**
     * 构造角色权限聚合类。
     *
     * @param menuPermissions 菜单权限集合
     * @param buttonPermissions 按钮权限集合
     * @param apiPermissions 接口权限集合
     */
    public RolePermissions(Set<String> menuPermissions, Set<String> buttonPermissions, Set<String> apiPermissions) {
        this.menuPermissions = menuPermissions != null ? Collections.unmodifiableSet(menuPermissions) : Collections.emptySet();
        this.buttonPermissions = buttonPermissions != null ? Collections.unmodifiableSet(buttonPermissions) : Collections.emptySet();
        this.apiPermissions = apiPermissions != null ? Collections.unmodifiableSet(apiPermissions) : Collections.emptySet();
    }

    /**
     * 获取菜单权限集合。
     *
     * @return 不可变的菜单权限集合
     */
    public Set<String> getMenuPermissions() {
        return menuPermissions;
    }

    /**
     * 获取按钮权限集合。
     *
     * @return 不可变的按钮权限集合
     */
    public Set<String> getButtonPermissions() {
        return buttonPermissions;
    }

    /**
     * 获取接口权限集合。
     *
     * @return 不可变的接口权限集合
     */
    public Set<String> getApiPermissions() {
        return apiPermissions;
    }

    /**
     * 创建一个空的角色权限实例。
     *
     * @return 空的角色权限
     */
    public static RolePermissions empty() {
        return new RolePermissions(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    }

    /**
     * 判断是否拥有指定的菜单权限。
     *
     * @param permission 菜单权限码
     * @return 拥有该权限时返回 {@code true}
     */
    public boolean hasMenuPermission(String permission) {
        if (permission == null || menuPermissions.isEmpty()) {
            return false;
        }
        return menuPermissions.contains(permission.trim());
    }

    /**
     * 判断是否拥有指定的按钮权限。
     *
     * @param permission 按钮权限码
     * @return 拥有该权限时返回 {@code true}
     */
    public boolean hasButtonPermission(String permission) {
        if (permission == null || buttonPermissions.isEmpty()) {
            return false;
        }
        return buttonPermissions.contains(permission.trim());
    }

    /**
     * 判断是否拥有指定的接口权限。
     *
     * @param permission 接口权限码
     * @return 拥有该权限时返回 {@code true}
     */
    public boolean hasApiPermission(String permission) {
        if (permission == null || apiPermissions.isEmpty()) {
            return false;
        }
        return apiPermissions.contains(permission.trim());
    }
}
