package com.njydsz.pmis.common.auth.hierarchy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.pmis.common.auth.util.PermissionUtils;

/**
 * 权限继承层级管理器。
 *
 * <p>支持权限树继承：拥有父权限自动拥有子权限。
 * 例如：拥有 {@code sys:user} 自动拥有 {@code sys:user:list}、{@code sys:user:add} 等。
 *
 * <p>使用 {@link Map} 而非字符串拼接，实现 O(1) 查找。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0

 */
public final class PermissionHierarchy {

    /**
     * 父权限 → 子权限集合的映射
     */
    private static final Map<String, Set<String>> PARENT_TO_CHILDREN = new ConcurrentHashMap<>();

    /**
     * 子权限 → 父权限集合的映射（反向索引）
     */
    private static final Map<String, Set<String>> CHILD_TO_PARENTS = new ConcurrentHashMap<>();

    private PermissionHierarchy() {
    }

    /**
     * 注册权限继承关系。
     *
     * @param parent   父权限码
     * @param children 子权限码列表
     */
    public static void register(String parent, String... children) {
        if (parent == null || parent.isBlank() || children == null || children.length == 0) {
            return;
        }
        String p = parent.trim();
        Set<String> childSet = PARENT_TO_CHILDREN.computeIfAbsent(p, k -> ConcurrentHashMap.newKeySet());
        for (String child : children) {
            if (child != null && !child.isBlank()) {
                String c = child.trim();
                childSet.add(c);
                CHILD_TO_PARENTS.computeIfAbsent(c, k -> ConcurrentHashMap.newKeySet()).add(p);
            }
        }
    }

    /**
     * 判断用户是否拥有指定权限（考虑权限继承）。
     *
     * <p>如果用户直接拥有该权限，返回 true。
     * 如果用户拥有该权限的某个父级权限，也返回 true（递归检查）。
     *
     * @param granted 用户已授权的权限集合
     * @param required 需要校验的权限码
     * @param wildcardEnabled 是否启用通配符匹配
     * @return 拥有权限返回 true
     */
    public static boolean hasPermission(Set<String> granted, String required, boolean wildcardEnabled) {
        // 1. 直接匹配
        if (PermissionUtils.hasPermission(granted, required, wildcardEnabled)) {
            return true;
        }
        // 2. 检查是否拥有父级权限（O(1) Map 查找）
        Set<String> parents = CHILD_TO_PARENTS.get(required);
        if (parents != null) {
            for (String parent : parents) {
                if (PermissionUtils.hasPermission(granted, parent, wildcardEnabled)) {
                    return true;
                }
                // 递归检查父级权限的父级
                if (hasPermission(granted, parent, wildcardEnabled)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取指定权限的所有子权限（递归）。
     *
     * @param parent 父权限码
     * @return 所有子权限集合
     */
    public static Set<String> getAllChildren(String parent) {
        if (parent == null || parent.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        collectChildren(parent, result, new HashSet<>());
        return Collections.unmodifiableSet(result);
    }

    /**
     * 清空所有权限继承关系。
     */
    public static void clear() {
        PARENT_TO_CHILDREN.clear();
        CHILD_TO_PARENTS.clear();
    }

    /**
     * 获取已注册的父权限数量。
     *
     * @return 父权限数量
     */
    public static int getRegisteredParentCount() {
        return PARENT_TO_CHILDREN.size();
    }

    private static void collectChildren(String parent, Set<String> result, Set<String> visited) {
        if (visited.contains(parent)) {
            return;
        }
        visited.add(parent);
        Set<String> directChildren = PARENT_TO_CHILDREN.get(parent);
        if (directChildren != null) {
            for (String child : directChildren) {
                result.add(child);
                collectChildren(child, result, visited);
            }
        }
    }
}
