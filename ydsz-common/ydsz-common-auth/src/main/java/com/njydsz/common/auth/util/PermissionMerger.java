package com.njydsz.common.auth.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.njydsz.common.auth.model.RolePermissions;

/**
 * 权限合并工具类
 *
 * <p>提供多个角色权限集合的合并操作，支持权限的授予与拒绝逻辑。
 * 通过 {@link PermissionSet} 内部类实现权限的加减运算。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>多角色权限合并：将多个角色权限合并为一个统一的权限集合</li>
 *   <li>权限拒绝机制：支持以 "!" 开头的角色代码，将该角色以 "role:actualRole" 标记添加到菜单权限的拒绝集合中；
 *       注意：该角色的菜单、按钮、API 权限仍会被添加到授予集合中，最终通过集合减法移除匹配的拒绝项</li>
 *   <li>权限类型分离：菜单权限、按钮权限、API 权限分别管理</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public final class PermissionMerger {

    private PermissionMerger() {
    }

    /**
     * 合并多个角色权限
     *
     * @param roles 角色权限列表
     * @param roleCodes 角色代码列表（以 "!" 开头表示拒绝该角色）
     * @return 合并后的角色权限集合
     */
    public static RolePermissions mergeRoles(List<RolePermissions> roles, List<String> roleCodes) {
        if (roles == null || roles.isEmpty()) {
            return RolePermissions.empty();
        }

        PermissionSet granted = new PermissionSet();
        PermissionSet denied = new PermissionSet();

        int index = 0;
        for (RolePermissions rp : roles) {
            if (rp == null) {
                continue;
            }
            String roleCode = index < roleCodes.size() ? roleCodes.get(index) : null;
            merge(granted, denied, rp, roleCode);
            index++;
        }

        return granted.subtract(denied).toRolePermissions();
    }

    private static void merge(PermissionSet granted, PermissionSet denied, RolePermissions rp, String roleCode) {
        if (roleCode != null && roleCode.startsWith("!")) {
            String actualRole = roleCode.substring(1);
            denied.addMenuPermission("role:" + actualRole);
        }

        granted.addMenuPermissions(rp.getMenuPermissions());
        granted.addButtonPermissions(rp.getButtonPermissions());
        granted.addApiPermissions(rp.getApiPermissions());
    }

    /**
     * 权限集合，支持授予与拒绝的加减运算。
     *
     * <p>按菜单/按钮/API 三类权限分别以 {@link HashSet} 存储；合并后通过
     * {@link #subtract} 移除拒绝项，最终由 {@link #toRolePermissions} 输出不可变结果。
     * 非线程安全，仅供单线程合并流程内部使用。
     */
    private static final class PermissionSet {
        private final Set<String> menuPerms = new HashSet<>();
        private final Set<String> buttonPerms = new HashSet<>();
        private final Set<String> apiPerms = new HashSet<>();

        void addMenuPermission(String perm) {
            if (perm != null) {
                menuPerms.add(perm.trim());
            }
        }

        void addButtonPermission(String perm) {
            if (perm != null) {
                buttonPerms.add(perm.trim());
            }
        }

        void addApiPermission(String perm) {
            if (perm != null) {
                apiPerms.add(perm.trim());
            }
        }

        void addMenuPermissions(Set<String> perms) {
            if (perms != null) {
                perms.forEach(this::addMenuPermission);
            }
        }

        void addButtonPermissions(Set<String> perms) {
            if (perms != null) {
                perms.forEach(this::addButtonPermission);
            }
        }

        void addApiPermissions(Set<String> perms) {
            if (perms != null) {
                perms.forEach(this::addApiPermission);
            }
        }

        PermissionSet subtract(PermissionSet denied) {
            PermissionSet result = new PermissionSet();
            result.menuPerms.addAll(menuPerms);
            result.buttonPerms.addAll(buttonPerms);
            result.apiPerms.addAll(apiPerms);

            result.menuPerms.removeAll(denied.menuPerms);
            result.buttonPerms.removeAll(denied.buttonPerms);
            result.apiPerms.removeAll(denied.apiPerms);
            return result;
        }

        RolePermissions toRolePermissions() {
            return new RolePermissions(
                    Collections.unmodifiableSet(menuPerms),
                    Collections.unmodifiableSet(buttonPerms),
                    Collections.unmodifiableSet(apiPerms)
            );
        }
    }
}
