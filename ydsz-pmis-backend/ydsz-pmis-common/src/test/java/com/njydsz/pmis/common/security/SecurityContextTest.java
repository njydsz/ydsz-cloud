package com.njydsz.pmis.common.security;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityContext 登录上下文单元测试
 */
@DisplayName("SecurityContext 线程上下文测试")
class SecurityContextTest {

    @AfterEach
    void cleanUp() {
        SecurityContext.clear();
    }

    @Test
    @DisplayName("setCurrent 后 getCurrent 应能取到用户")
    void setAndGet() {
        LoginUser user = LoginUser.builder().userId(1L).username("u").build();
        SecurityContext.setCurrent(user);
        assertThat(SecurityContext.getCurrent().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("未登录调用 getCurrent 应抛 BizException(UNAUTHORIZED)")
    void getCurrent_unauthorized() {
        assertThatThrownBy(SecurityContext::getCurrent)
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("getCurrentOrNull 未登录应返回 null")
    void getCurrentOrNull_null() {
        assertThat(SecurityContext.getCurrentOrNull()).isNull();
    }

    @Test
    @DisplayName("clear 后上下文应清空")
    void clear_works() {
        SecurityContext.setCurrent(LoginUser.builder().userId(1L).build());
        SecurityContext.clear();
        assertThat(SecurityContext.getCurrentOrNull()).isNull();
    }

    @Test
    @DisplayName("getUserId / getUsername / getDeptId 应能获取")
    void shortcutMethods() {
        SecurityContext.setCurrent(LoginUser.builder()
                .userId(10L).username("u").deptId(20L).build());
        assertThat(SecurityContext.getUserId()).isEqualTo(10L);
        assertThat(SecurityContext.getUsername()).isEqualTo("u");
        assertThat(SecurityContext.getDeptId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("requirePermission 拥有权限应通过")
    void requirePermission_pass() {
        SecurityContext.setCurrent(LoginUser.builder()
                .permissions(List.of("user:create")).build());
        SecurityContext.requirePermission("user:create");
    }

    @Test
    @DisplayName("requirePermission 无权限应抛 FORBIDDEN")
    void requirePermission_forbidden() {
        SecurityContext.setCurrent(LoginUser.builder()
                .permissions(List.of("user:list")).build());
        assertThatThrownBy(() -> SecurityContext.requirePermission("user:delete"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("requireAnyPermission 拥有任一权限应通过")
    void requireAnyPermission_pass() {
        SecurityContext.setCurrent(LoginUser.builder()
                .permissions(List.of("user:list")).build());
        SecurityContext.requireAnyPermission("user:list", "user:create");
    }

    @Test
    @DisplayName("requireAnyPermission 无任何权限应抛 FORBIDDEN")
    void requireAnyPermission_forbidden() {
        SecurityContext.setCurrent(LoginUser.builder()
                .permissions(List.of()).build());
        assertThatThrownBy(() -> SecurityContext.requireAnyPermission("a", "b"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.FORBIDDEN.getCode());
    }
}
