package com.njydsz.pmis.common.auth.hierarchy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.pmis.common.auth.util.PermissionUtils;

/**
 * 权限继承层级管理器。
 *
 * <p>支持权限树继承：拥有父权限自动拥有子权限。
 * 例如：拥有 {@code sys:user} 自动拥有 {@code sys:user:list}、{@code sys:user:add} 等。
 *
 * @since 1.1.0

 */
public final class PermissionHierarchy {

    private static final Set<String> PARENT_TO_CHILDREN = ConcurrentHashMap.newKeySet();
    private static final Set<String> CHILD_TO_PARENTS = ConcurrentHashMap.newKeySet();

    private PermissionHierarchy() {
    }

    public static void register(String parent, String... children) {
        if (parent == null || parent.isBlank() || children == null || children.length == 0) {
            return;
        }
        String p = parent.trim();
        for (String child : children) {
            if (child != null && !child.isBlank()) {
                String c = child.trim();
                PARENT_TO_CHILDREN.add(p + "->" + c);
                CHILD_TO_PARENTS.add(c + "->" + p);
            }
        }
    }

    public static boolean hasPermission(Set<String> granted, String required, boolean wildcardEnabled) {
        if (PermissionUtils.hasPermission(granted, required, wildcardEnabled)) {
            return true;
        }
        for (String entry : CHILD_TO_PARENTS) {
            if (entry.startsWith(required + "->")) {
                String parent = entry.substring(required.length() + 2);
                if (PermissionUtils.hasPermission(granted, parent, wildcardEnabled)) {
                    return true;
                }
                if (hasPermission(granted, parent, wildcardEnabled)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Set<String> getAllChildren(String parent) {
        if (parent == null || parent.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        collectChildren(parent, result, new HashSet<>());
        return Collections.unmodifiableSet(result);
    }

    public static void clear() {
        PARENT_TO_CHILDREN.clear();
        CHILD_TO_PARENTS.clear();
    }

    public static int getRegisteredParentCount() {
        return (int) PARENT_TO_CHILDREN.stream().map(e -> e.split("->")[0]).distinct().count();
    }

    private static void collectChildren(String parent, Set<String> result, Set<String> visited) {
        if (visited.contains(parent)) {
            return;
        }
        visited.add(parent);
        for (String entry : PARENT_TO_CHILDREN) {
            if (entry.startsWith(parent + "->")) {
                String child = entry.substring(parent.length() + 2);
                result.add(child);
                collectChildren(child, result, visited);
            }
        }
    }
}
