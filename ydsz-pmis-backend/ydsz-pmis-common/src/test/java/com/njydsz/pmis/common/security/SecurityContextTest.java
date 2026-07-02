package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityContext 单元测试
 *
 * <p>P2-16: 重点验证多租户上下文 getTenantIdOrDefault 方法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SecurityContext 单元测试")
class SecurityContextTest {

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    @DisplayName("P2-16: 无登录上下文时 getTenantIdOrDefault 返回默认值 1L")
    void getTenantIdOrDefault_noContext_returnsDefault1() {
        assertThat(SecurityContext.getTenantIdOrDefault()).isEqualTo(1L);
        assertThat(SecurityContext.getTenantIdOrDefault(99L)).isEqualTo(99L);
    }

    @Test
    @DisplayName("P2-16: 登录用户 tenantId 为空时返回默认值")
    void getTenantIdOrDefault_userWithNullTenantId_returnsDefault() {
        LoginUser user = LoginUser.builder()
                .userId(1L)
                .username("alice")
                .tenantId(null)
                .build();
        SecurityContext.setCurrent(user);

        assertThat(SecurityContext.getTenantIdOrDefault()).isEqualTo(1L);
        assertThat(SecurityContext.getTenantIdOrDefault(99L)).isEqualTo(99L);
    }

    @Test
    @DisplayName("P2-16: 登录用户 tenantId 非空时返回用户租户 ID")
    void getTenantIdOrDefault_userWithTenantId_returnsUserTenantId() {
        LoginUser user = LoginUser.builder()
                .userId(2L)
                .username("bob")
                .tenantId(888L)
                .build();
        SecurityContext.setCurrent(user);

        assertThat(SecurityContext.getTenantIdOrDefault()).isEqualTo(888L);
        // 即使传入默认值，也应返回用户上下文中的 tenantId
        assertThat(SecurityContext.getTenantIdOrDefault(1L)).isEqualTo(888L);
    }

    @Test
    @DisplayName("P2-16: getTenantIdOrDefault(null) 返回 1L 兜底")
    void getTenantIdOrDefault_nullDefault_returns1() {
        // 无上下文，传入 null 默认值
        assertThat(SecurityContext.getTenantIdOrDefault(null)).isEqualTo(1L);
    }

    @Test
    @DisplayName("P2-16: clear() 后上下文清空，再调用返回默认值")
    void getTenantIdOrDefault_afterClear_returnsDefault() {
        LoginUser user = LoginUser.builder()
                .userId(3L)
                .username("charlie")
                .tenantId(777L)
                .build();
        SecurityContext.setCurrent(user);
        assertThat(SecurityContext.getTenantIdOrDefault()).isEqualTo(777L);

        SecurityContext.clear();
        assertThat(SecurityContext.getTenantIdOrDefault()).isEqualTo(1L);
    }
}
