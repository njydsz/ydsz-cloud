package com.njydsz.common.auth.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * {@link PermissionUtils} 单元测试。
 *
 * <p>覆盖权限匹配、通配符匹配、超管判断、CSV 解析等核心逻辑。
 *
 * @since 1.0.0

 */
class PermissionUtilsTest {

    @Test
    void testHasPermission_exactMatch() {
        Set<String> granted = Set.of("sys:user:add", "sys:user:list");
        assertTrue(PermissionUtils.hasPermission(granted, "sys:user:add"));
        assertTrue(PermissionUtils.hasPermission(granted, "sys:user:list"));
        assertFalse(PermissionUtils.hasPermission(granted, "sys:user:delete"));
    }

    @Test
    void testHasPermission_wildcard_singleStar() {
        Set<String> granted = Set.of("sys:user:*");
        assertTrue(PermissionUtils.hasPermission(granted, "sys:user:add", true));
        assertTrue(PermissionUtils.hasPermission(granted, "sys:user:delete", true));
        assertTrue(PermissionUtils.hasPermission(granted, "sys:user:list", true));
    }

    @Test
    void testHasPermission_wildcard_disabled() {
        Set<String> granted = Set.of("sys:user:*");
        assertFalse(PermissionUtils.hasPermission(granted, "sys:user:add", false));
        assertTrue(PermissionUtils.hasPermission(granted, "sys:user:*", false));
    }

    @Test
    void testHasPermission_wildcard_doubleStar() {
        Set<String> granted = Set.of("sys:**");
        assertTrue(PermissionUtils.hasPermission(granted, "sys:user:add", true));
        assertTrue(PermissionUtils.hasPermission(granted, "sys:role:delete", true));
    }

    @Test
    void testHasPermission_emptyOrNull() {
        assertFalse(PermissionUtils.hasPermission(Collections.emptySet(), "any"));
        assertFalse(PermissionUtils.hasPermission(null, "any"));
        assertFalse(PermissionUtils.hasPermission(Set.of("any"), null));
        assertFalse(PermissionUtils.hasPermission(Set.of("any"), ""));
        assertFalse(PermissionUtils.hasPermission(Set.of("any"), "  "));
    }

    @Test
    void testIsSuperAdmin_set() {
        Set<String> userRoles = Set.of("admin", "manager");
        Set<String> adminRoles = Set.of("admin", "super_admin");
        assertTrue(PermissionUtils.isSuperAdmin(userRoles, adminRoles));
    }

    @Test
    void testIsSuperAdmin_csv() {
        Set<String> userRoles = Set.of("super_admin");
        assertTrue(PermissionUtils.isSuperAdmin(userRoles, "admin,super_admin"));
        assertFalse(PermissionUtils.isSuperAdmin(userRoles, "admin"));
    }

    @Test
    void testIsSuperAdmin_empty() {
        assertFalse(PermissionUtils.isSuperAdmin(Collections.emptySet(), Set.of("admin")));
        assertFalse(PermissionUtils.isSuperAdmin(Set.of("user"), Collections.emptySet()));
    }

    @Test
    void testSplitCsv() {
        Set<String> result = PermissionUtils.splitCsv("a,b,c");
        assertEquals(3, result.size());
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    @Test
    void testSplitCsv_withSpaces() {
        Set<String> result = PermissionUtils.splitCsv("  a , b , c  ");
        assertEquals(3, result.size());
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    @Test
    void testSplitCsv_empty() {
        assertTrue(PermissionUtils.splitCsv("").isEmpty());
        assertTrue(PermissionUtils.splitCsv(null).isEmpty());
        assertTrue(PermissionUtils.splitCsv("   ").isEmpty());
    }

    @Test
    void testSplitCsv_deduplicates() {
        Set<String> result = PermissionUtils.splitCsv("a,a,b,a");
        assertEquals(2, result.size());
    }

    @Test
    void testMergeRolePermissions() {
        java.util.Map<String, Set<String>> rolePerms = new java.util.HashMap<>();
        rolePerms.put("admin", Set.of("sys:user:add", "sys:user:list"));
        rolePerms.put("manager", Set.of("sys:user:edit", "sys:user:list"));

        Set<String> merged = PermissionUtils.mergeRolePermissions(rolePerms, Set.of("admin", "manager"));
        assertEquals(3, merged.size());
        assertTrue(merged.contains("sys:user:add"));
        assertTrue(merged.contains("sys:user:list"));
        assertTrue(merged.contains("sys:user:edit"));
    }

    @Test
    void testMergeRolePermissions_null() {
        assertTrue(PermissionUtils.mergeRolePermissions(null, Set.of("admin")).isEmpty());
        assertTrue(PermissionUtils.mergeRolePermissions(new java.util.HashMap<>(), null).isEmpty());
    }

    @Test
    void testPermissionMatch_exact() {
        assertTrue(PermissionUtils.permissionMatch("sys:user:add", "sys:user:add", true));
        assertFalse(PermissionUtils.permissionMatch("sys:user:add", "sys:user:delete", true));
    }

    @Test
    void testPermissionMatch_blank() {
        assertFalse(PermissionUtils.permissionMatch("", "sys:user:add", true));
        assertFalse(PermissionUtils.permissionMatch(null, "sys:user:add", true));
        assertFalse(PermissionUtils.permissionMatch("sys:user:add", "", true));
    }
}
