package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TenantContext 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("TenantContext 测试")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getTenantId - 未设置时应返回默认值 \"1\"")
    void getTenantId_shouldReturnDefaultWhenNotSet() {
        assertEquals("1", TenantContext.getTenantId());
    }

    @Test
    @DisplayName("setTenantId/getTenantId - 应正确存取租户 ID")
    void setAndGetTenantId_shouldWork() {
        TenantContext.setTenantId("100");
        assertEquals("100", TenantContext.getTenantId());
    }

    @Test
    @DisplayName("setTenantId/getTenantId - 设置为 null 后应返回默认值")
    void setTenantId_null_shouldFallbackToDefault() {
        TenantContext.setTenantId(null);
        assertEquals(1L, TenantContext.getTenantId());
    }

    @Test
    @DisplayName("clear - 清除后应恢复默认值")
    void clear_shouldResetToDefault() {
        TenantContext.setTenantId("999");
        TenantContext.clear();

        assertEquals("1", TenantContext.getTenantId());
    }

    @Test
    @DisplayName("DEFAULT_TENANT_ID 常量 - 应等于 \"1\"")
    void defaultTenantId_shouldBe1() {
        assertEquals("1", TenantContext.DEFAULT_TENANT_ID);
    }
}