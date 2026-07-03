package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginUser 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("LoginUser 测试")
class LoginUserTest {

    @Test
    @DisplayName("Builder 构造 - 应正确设置所有字段")
    void builder_shouldSetAllFields() {
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .username("zhangsan")
                .realName("张三")
                .deptId(100L)
                .deptName("技术部")
                .tenantId(1L)
                .levelCode("P7")
                .roles(Arrays.asList("admin", "user"))
                .permissions(Arrays.asList("system:user:create", "system:user:view"))
                .dataScope("ALL")
                .token("abc123")
                .loginTime(1700000000000L)
                .expireTime(1700086400000L)
                .build();

        assertEquals(1L, user.getUserId());
        assertEquals("zhangsan", user.getUsername());
        assertEquals("张三", user.getRealName());
        assertEquals(100L, user.getDeptId());
        assertEquals("技术部", user.getDeptName());
        assertEquals(1L, user.getTenantId());
        assertEquals("P7", user.getLevelCode());
        assertEquals(Arrays.asList("admin", "user"), user.getRoles());
        assertEquals(Arrays.asList("system:user:create", "system:user:view"), user.getPermissions());
        assertEquals("ALL", user.getDataScope());
        assertEquals("abc123", user.getToken());
        assertEquals(1700000000000L, user.getLoginTime());
        assertEquals(1700086400000L, user.getExpireTime());
    }

    @Test
    @DisplayName("无参构造 - 所有字段应为 null")
    void noArgsConstructor_shouldHaveNullFields() {
        LoginUser user = new LoginUser();

        assertNull(user.getUserId());
        assertNull(user.getUsername());
        assertNull(user.getRealName());
        assertNull(user.getDeptId());
        assertNull(user.getDeptName());
        assertNull(user.getTenantId());
        assertNull(user.getLevelCode());
        assertNull(user.getRoles());
        assertNull(user.getPermissions());
        assertNull(user.getDataScope());
        assertNull(user.getToken());
        assertNull(user.getLoginTime());
        assertNull(user.getExpireTime());
    }

    @Test
    @DisplayName("全参构造 - 应正确设置所有字段")
    void allArgsConstructor_shouldSetAllFields() {
        List<String> roles = Arrays.asList("admin");
        List<String> permissions = Arrays.asList("*:*:*");
        List<Long> customDeptIds = Arrays.asList(1L, 2L);

        LoginUser user = new LoginUser(
                1L, "lisi", "李四", 200L, "财务部", 1L, "P8",
                roles, permissions, "DEPT", customDeptIds, "token123",
                1700000000000L, 1700086400000L
        );

        assertEquals(1L, user.getUserId());
        assertEquals("lisi", user.getUsername());
        assertEquals("李四", user.getRealName());
        assertEquals(200L, user.getDeptId());
        assertEquals("财务部", user.getDeptName());
        assertEquals(1L, user.getTenantId());
        assertEquals("P8", user.getLevelCode());
        assertEquals(roles, user.getRoles());
        assertEquals(permissions, user.getPermissions());
        assertEquals("DEPT", user.getDataScope());
        assertEquals(customDeptIds, user.getCustomDeptIds());
        assertEquals("token123", user.getToken());
        assertEquals(1700000000000L, user.getLoginTime());
        assertEquals(1700086400000L, user.getExpireTime());
    }

    @Test
    @DisplayName("isSuperAdmin - 拥有 *:*:* 权限时应返回 true")
    void isSuperAdmin_shouldReturnTrueWhenHasWildcard() {
        LoginUser user = LoginUser.builder()
                .permissions(Arrays.asList("*:*:*"))
                .build();

        assertTrue(user.isSuperAdmin());
    }

    @Test
    @DisplayName("isSuperAdmin - 无 *:*:* 权限时应返回 false")
    void isSuperAdmin_shouldReturnFalseWithoutWildcard() {
        LoginUser user = LoginUser.builder()
                .permissions(Arrays.asList("system:user:view"))
                .build();

        assertFalse(user.isSuperAdmin());
    }

    @Test
    @DisplayName("isSuperAdmin - permissions 为 null 时应返回 false")
    void isSuperAdmin_shouldReturnFalseWhenPermissionsNull() {
        LoginUser user = LoginUser.builder().build();

        assertFalse(user.isSuperAdmin());
    }

    @Test
    @DisplayName("isSuperAdmin - permissions 为空列表时应返回 false")
    void isSuperAdmin_shouldReturnFalseWhenPermissionsEmpty() {
        LoginUser user = LoginUser.builder()
                .permissions(Collections.emptyList())
                .build();

        assertFalse(user.isSuperAdmin());
    }

    @Test
    @DisplayName("hasPermission - 超级管理员应拥有所有权限")
    void hasPermission_superAdminShouldHaveAll() {
        LoginUser user = LoginUser.builder()
                .permissions(Arrays.asList("*:*:*"))
                .build();

        assertTrue(user.hasPermission("any:random:permission"));
    }

    @Test
    @DisplayName("hasPermission - 拥有指定权限时应返回 true")
    void hasPermission_shouldReturnTrueWhenHas() {
        LoginUser user = LoginUser.builder()
                .permissions(Arrays.asList("system:user:create", "system:user:view"))
                .build();

        assertTrue(user.hasPermission("system:user:create"));
    }

    @Test
    @DisplayName("hasPermission - 不拥有指定权限时应返回 false")
    void hasPermission_shouldReturnFalseWhenNotHas() {
        LoginUser user = LoginUser.builder()
                .permissions(Arrays.asList("system:user:view"))
                .build();

        assertFalse(user.hasPermission("system:user:delete"));
    }

    @Test
    @DisplayName("hasPermission - permissions 为 null 时应返回 false")
    void hasPermission_shouldReturnFalseWhenPermissionsNull() {
        LoginUser user = LoginUser.builder().build();

        assertFalse(user.hasPermission("any:permission"));
    }

    @Test
    @DisplayName("setter/getter - customDeptIds 应正确存取")
    void customDeptIds_shouldSetAndGet() {
        LoginUser user = new LoginUser();
        List<Long> deptIds = Arrays.asList(10L, 20L, 30L);
        user.setCustomDeptIds(deptIds);

        assertEquals(deptIds, user.getCustomDeptIds());
    }
}