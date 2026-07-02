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

        assertThat(SecurityContext.getTenantIdOrDefault()).isEqualTo(1L