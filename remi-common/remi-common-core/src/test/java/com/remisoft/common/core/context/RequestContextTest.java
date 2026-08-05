package com.remisoft.common.core.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RequestContext 单元测试
 *
 * <p>验证 typed accessor、防御性清理、快照恢复等核心能力。
 *
 * @author remi-team
 * @since 1.8.0
 */
class RequestContextTest {

    @AfterEach
    void cleanup() {
        RequestContext.clear();
    }

    @Nested
    @DisplayName("Typed Accessors")
    class Accessors {

        @Test
        @DisplayName("getUserId / setUserId 往返")
        void userId_roundTrip() {
            assertNull(RequestContext.getUserId());
            RequestContext.setUserId("user-123");
            assertEquals("user-123", RequestContext.getUserId());
        }

        @Test
        @DisplayName("getTenantId / setTenantId 往返")
        void tenantId_roundTrip() {
            RequestContext.setTenantId("tenant-456");
            assertEquals("tenant-456", RequestContext.getTenantId());
        }

        @Test
        @DisplayName("getTraceId / setTraceId 往返")
        void traceId_roundTrip() {
            RequestContext.setTraceId("trace-abc");
            assertEquals("trace-abc", RequestContext.getTraceId());
        }

        @Test
        @DisplayName("getRequestId / setRequestId 往返")
        void requestId_roundTrip() {
            RequestContext.setRequestId("req-xyz");
            assertEquals("req-xyz", RequestContext.getRequestId());
        }

        @Test
        @DisplayName("getLanguage / setLanguage 往返")
        void language_roundTrip() {
            RequestContext.setLanguage("zh-CN");
            assertEquals("zh-CN", RequestContext.getLanguage());
        }

        @Test
        @DisplayName("isTenantIsolationSkipped / setTenantIsolationSkipped 往返")
        void tenantIsolationSkipped_roundTrip() {
            assertFalse(RequestContext.isTenantIsolationSkipped());
            RequestContext.setTenantIsolationSkipped(true);
            assertTrue(RequestContext.isTenantIsolationSkipped());
        }

        @Test
        @DisplayName("新增字段: clientIp / requestSource / apiVersion")
        void newFields_roundTrip() {
            assertNull(RequestContext.getClientIp());
            RequestContext.setClientIp("127.0.0.1");
            assertEquals("127.0.0.1", RequestContext.getClientIp());

            RequestContext.setRequestSource("OPEN_API");
            assertEquals("OPEN_API", RequestContext.getRequestSource());

            RequestContext.setApiVersion("v2");
            assertEquals("v2", RequestContext.getApiVersion());
        }
    }

    @Nested
    @DisplayName("Defensive Cleanup")
    class Cleanup {

        @Test
        @DisplayName("clear() 后所有字段归零")
        void clear_resetsAll() {
            RequestContext.setUserId("u");
            RequestContext.setTenantId("t");
            RequestContext.setTraceId("tr");
            RequestContext.clear();

            assertNull(RequestContext.getUserId());
            assertNull(RequestContext.getTenantId());
            assertNull(RequestContext.getTraceId());
        }

        @Test
        @DisplayName("runWithCleanup 保证 finally 清理")
        void runWithCleanup_guaranteed() {
            RequestContext.runWithCleanup(() -> {
                RequestContext.setUserId("u");
                RequestContext.setTenantId("t");
            });

            // 清理已生效
            assertNull(RequestContext.getUserId());
            assertNull(RequestContext.getTenantId());
        }

        @Test
        @DisplayName("runWithCleanup 中抛异常后仍清理")
        void runWithCleanup_withException() {
            assertThrows(RuntimeException.class, () ->
                RequestContext.runWithCleanup(() -> {
                    RequestContext.setUserId("u");
                    throw new RuntimeException("boom");
                })
            );

            assertNull(RequestContext.getUserId());
        }

        @Test
        @DisplayName("supplyWithCleanup 返回值正确")
        void supplyWithCleanup() {
            int result = RequestContext.supplyWithCleanup(() -> {
                RequestContext.setUserId("u");
                return 42;
            });

            assertEquals(42, result);
            assertNull(RequestContext.getUserId());
        }
    }

    @Nested
    @DisplayName("快照与恢复")
    class SnapshotRestore {

        @Test
        @DisplayName("snapshot() 反映当前状态")
        void snapshot_reflectsCurrent() {
            RequestContext.setUserId("u");
            RequestContext.setTenantId("t");
            RequestContext.setTraceId("tr");

            Map<String, String> snapshot = RequestContext.snapshot();
            assertEquals("u", snapshot.get("userId"));
            assertEquals("t", snapshot.get("tenantId"));
            assertEquals("tr", snapshot.get("traceId"));
        }

        @Test
        @DisplayName("restore(Map) 恢复状态")
        void restore_works() {
            RequestContext.setUserId("old");
            RequestContext.clear();

            // 构建快照
            RequestContext.setUserId("new");
            RequestContext.setTenantId("new-tenant");
            Map<String, String> snapshot = RequestContext.snapshot();

            // 清理后恢复
            RequestContext.clear();
            assertNull(RequestContext.getUserId());

            RequestContext.restore(snapshot);
            assertEquals("new", RequestContext.getUserId());
            assertEquals("new-tenant", RequestContext.getTenantId());
        }

        @Test
        @DisplayName("restore(null) / restore(empty) 不抛异常")
        void restore_nullSafe() {
            assertDoesNotThrow(() -> RequestContext.restore(null));
            assertDoesNotThrow(() -> RequestContext.restore(Map.of()));
        }
    }
}
