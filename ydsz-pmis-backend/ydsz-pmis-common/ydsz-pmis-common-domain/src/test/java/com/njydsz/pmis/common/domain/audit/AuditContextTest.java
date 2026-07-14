package com.njydsz.pmis.common.domain.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.core.context.RequestContext;

/**
 * AuditContext 审计上下文单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AuditContext 审计上下文测试")
class AuditContextTest {

    @AfterEach
    void tearDown() {
        AuditContext.clear();
        RequestContext.clear();
    }

    @Test
    @DisplayName("无 RequestContext 时 currentUser 应返回 system")
    void shouldReturnSystemUserWhenNoContext() {
        assertEquals(AuditContext.SYSTEM_USER, AuditContext.currentUser());
    }

    @Test
    @DisplayName("有 RequestContext 时 currentUser 应返回上下文中的用户")
    void shouldReturnUserFromRequestContext() {
        RequestContext.setUserId("user123");
        assertEquals("user123", AuditContext.currentUser());
    }

    @Test
    @DisplayName("手动设置的用户应优先于 RequestContext")
    void shouldPreferManualUserOverRequestContext() {
        RequestContext.setUserId("fromContext");
        AuditContext.setUser("manualUser");
        assertEquals("manualUser", AuditContext.currentUser());
    }

    @Test
    @DisplayName("set 应同时设置用户和租户")
    void shouldSetUserAndTenant() {
        AuditContext.set("user1", "tenant1");
        assertEquals("user1", AuditContext.currentUser());
        assertEquals("tenant1", AuditContext.currentTenant());
        assertTrue(AuditContext.hasManualContext());
    }

    @Test
    @DisplayName("clear 应清除手动设置的上下文")
    void shouldClearManualContext() {
        AuditContext.set("user1", "tenant1");
        AuditContext.clear();
        assertEquals(AuditContext.SYSTEM_USER, AuditContext.currentUser());
        assertTrue(!AuditContext.hasManualContext());
    }

    @Test
    @DisplayName("now 应返回非 null 的 LocalDateTime")
    void shouldReturnNonNullNow() {
        assertNotNull(AuditContext.now());
    }

    private static void assertNotNull(Object obj) {
        assertTrue(obj != null, "Expected non-null value");
    }
}
