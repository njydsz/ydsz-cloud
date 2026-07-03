package com.njydsz.pmis.common.security;

import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityContext 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("SecurityContext 测试")
class SecurityContextTest {

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    private LoginUser createTestUser() {
        return LoginUser.builder()
                .userId(1L)
                .username("testuser")
                .realName("测试用户")
                .deptId(100L)
                .tenantId(1L)
                .build();
    }

    @Test
    @DisplayName("setCurrent/getCurrent - 应正确存取登录用户")
    void setAndGetCurrent_shouldWork() {
        LoginUser user = createTestUser();
        SecurityContext.setCurrent(user);

        LoginUser retrieved = SecurityContext.getCurrent();
        assertEquals(user.getUserId(), retrieved.getUserId());
        assertEquals(user.getUsername(), retrieved.getUsername());
    }

    @Test
    @DisplayName("getCurrent - 未设置时应抛出 BizException")
    void getCurrent_shouldThrowWhenNotSet() {
        assertThrows(BizException.class, SecurityContext::getCurrent);
    }

    @Test
    @DisplayName("getCurrentOrNull - 未设置时应返回 null")
    void getCurrentOrNull_shouldReturnNullWhenNotSet() {
        assertNull(SecurityContext.getCurrentOrNull());
    }

    @Test
    @DisplayName("getCurrentOrNull - 设置后应返回正确用户")
    void getCurrentOrNull_shouldReturnUserWhenSet() {
        LoginUser user = createTestUser();
        SecurityContext.setCurrent(user);

        LoginUser retrieved = SecurityContext.getCurrentOrNull();
        assertNotNull(retrieved);
        assertEquals(user.getUserId(), retrieved.getUserId());
    }

    @Test
    @DisplayName("clear - 清除后 getCurrent 应抛出异常")
    void clear_shouldRemoveContext() {
        SecurityContext.setCurrent(createTestUser());
        SecurityContext.clear();

        assertThrows(BizException.class, SecurityContext::getCurrent);
        assertNull(SecurityContext.getCurrentOrNull());
    }

    @Test
    @DisplayName("getUserId - 应返回当前用户 ID")
    void getUserId_shouldReturnCurrentUserId() {
        LoginUser user = createTestUser();
        SecurityContext.setCurrent(user);

        assertEquals(1L, SecurityContext.getUserId());
    }

    @Test
    @DisplayName("getUsername - 应返回当前用户名")
    void getUsername_shouldReturnCurrentUsername() {
        LoginUser user = createTestUser();
        SecurityContext.setCurrent(user);

        assertEquals("testuser", SecurityContext.getUsername());
    }

    @Test
    @DisplayName("getDeptId - 应返回当前部门 ID")
    void getDeptId_shouldReturnCurrentDeptId() {
        LoginUser user = createTestUser();
        SecurityContext.setCurrent(user);

        assertEquals(100L, SecurityContext.getDeptId());
    }

    @Test
    @DisplayName("getTenantIdOrDefault - 未登录时应返回默认值 1L")
    void getTenantIdOrDefault_shouldReturnDefaultWhenNotLoggedIn() {
        assertEquals(1L, SecurityContext.getTenantIdOrDefault());
    }

    @Test
    @DisplayName("getTenantIdOrDefault - 已登录时应返回用户 tenantId")
    void getTenantIdOrDefault_shouldReturnUserTenantId() {
        LoginUser user = LoginUser.builder().userId(1L).tenantId(999L).build();
        SecurityContext.setCurrent(user);

        assertEquals(999L, SecurityContext.getTenantIdOrDefault());
    }

    @Test
    @DisplayName("getTenantIdOrDefault(自定义默认值) - 未登录时应返回自定义默认值")
    void getTenantIdOrDefault_withCustomDefault() {
        assertEquals(100L, SecurityContext.getTenantIdOrDefault(100L));
    }

    @Test
    @DisplayName("requirePermission - 拥有权限时不抛异常")
    void requirePermission_shouldNotThrowWhenHasPermission() {
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .permissions(java.util.List.of("system:user:create"))
                .build();
        SecurityContext.setCurrent(user);

        assertDoesNotThrow(() -> SecurityContext.requirePermission("system:user:create"));
    }

    @Test
    @DisplayName("requirePermission - 无权限时应抛出 BizException")
    void requirePermission_shouldThrowWhenNoPermission() {
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .permissions(java.util.List.of("system:user:view"))
                .build();
        SecurityContext.setCurrent(user);

        assertThrows(BizException.class, () -> SecurityContext.requirePermission("system:user:create"));
    }

    @Test
    @DisplayName("requireAnyPermission - 拥有任一权限时不抛异常")
    void requireAnyPermission_shouldNotThrowWhenHasAnyPermission() {
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .permissions(java.util.List.of("system:user:view"))
                .build();
        SecurityContext.setCurrent(user);

        assertDoesNotThrow(() -> SecurityContext.requireAnyPermission("system:user:create", "system:user:view"));
    }

    @Test
    @DisplayName("requireAnyPermission - 全部不拥有时应抛出 BizException")
    void requireAnyPermission_shouldThrowWhenNoPermission() {
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .permissions(java.util.List.of("system:user:view"))
                .build();
        SecurityContext.setCurrent(user);

        assertThrows(BizException.class, () -> SecurityContext.requireAnyPermission("system:user:create", "system:user:delete"));
    }
}