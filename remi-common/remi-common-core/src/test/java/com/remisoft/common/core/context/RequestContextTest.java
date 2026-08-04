package com.remisoft.common.core.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RequestContext} 单元测试
 *
 * <p>覆盖内置键的 set/get、自定义属性、清理语义（clear/remove）、
 * CleanupGuard、线程池传播（TransmittableThreadLocal）、线程隔离等行为。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("RequestContext 请求上下文测试")
class RequestContextTest {

    @Test
    @DisplayName("set/get 内置键正常工作")
    void builtInKeys() {
        RequestContext.setUserId("user-1");
        RequestContext.setTenantId("tenant-1");
        RequestContext.setTraceId("trace-1");
        RequestContext.setRequestId("req-1");
        RequestContext.setLanguage("zh-CN");

        assertEquals("user-1", RequestContext.getUserId());
        assertEquals("tenant-1", RequestContext.getTenantId());
        assertEquals("trace-1", RequestContext.getTraceId());
        assertEquals("req-1", RequestContext.getRequestId());
        assertEquals("zh-CN", RequestContext.getLanguage());
        RequestContext.clear();
    }

    @Test
    @DisplayName("未设置时返回 null")
    void unsetReturnsNull() {
        RequestContext.clear();
        assertNull(RequestContext.getUserId());
        assertNull(RequestContext.getTenantId());
        assertNull(RequestContext.getTraceId());
    }

    @Test
    @DisplayName("put/get 自定义属性")
    void customAttributes() {
        RequestContext.put("orderId", "O-001");
        assertEquals("O-001", RequestContext.get("orderId"));
        RequestContext.clear();
    }

    @Test
    @DisplayName("put(null key) 抛出 NullPointerException")
    void putNullKeyThrows() {
        assertThrows(NullPointerException.class, () -> RequestContext.put((String) null, "v"));
        RequestContext.clear();
    }

    @Test
    @DisplayName("put(key, null) 移除该键")
    void putNullValueRemoves() {
        RequestContext.put("k", "v");
        RequestContext.put("k", null);
        assertNull(RequestContext.get("k"));
        RequestContext.clear();
    }

    @Test
    @DisplayName("remove 移除指定键")
    void remove() {
        RequestContext.setUserId("u1");
        RequestContext.remove(RequestContext.KEY_USER_ID);
        assertNull(RequestContext.getUserId());
        RequestContext.clear();
    }

    @Test
    @DisplayName("clear 清空全部上下文")
    void clear() {
        RequestContext.setUserId("u1");
        RequestContext.put("custom", "v");
        RequestContext.clear();
        assertNull(RequestContext.getUserId());
        assertNull(RequestContext.get("custom"));
    }

    @Test
    @DisplayName("setTenantIsolationSkipped(true) 标记跳过租户隔离")
    void tenantIsolationSkipped() {
        assertFalse(RequestContext.isTenantIsolationSkipped());
        RequestContext.setTenantIsolationSkipped(true);
        assertTrue(RequestContext.isTenantIsolationSkipped());
        RequestContext.setTenantIsolationSkipped(false);
        assertFalse(RequestContext.isTenantIsolationSkipped());
        RequestContext.clear();
    }

    @Test
    @DisplayName("newCleanupGuard 配合 try-with-resources 自动清理")
    void cleanupGuard() {
        try (RequestContext.CleanupGuard guard = RequestContext.newCleanupGuard()) {
            RequestContext.setUserId("u1");
            assertEquals("u1", RequestContext.getUserId());
        }
        assertNull(RequestContext.getUserId(), "guard must clear context on close");
    }

    @Test
    @DisplayName("newCleanupGuard 异常时也清理")
    void cleanupGuard_exception() {
        assertThrows(RuntimeException.class, () -> {
            try (RequestContext.CleanupGuard guard = RequestContext.newCleanupGuard()) {
                RequestContext.setUserId("u1");
                throw new RuntimeException("boom");
            }
        });
        assertNull(RequestContext.getUserId());
    }

    @Test
    @DisplayName("builder().apply() 批量设置内置键")
    void builder_apply() {
        try (RequestContext.CleanupGuard guard = RequestContext.builder()
                .userId("u1")
                .tenantId("t1")
                .traceId("tr1")
                .requestId("r1")
                .language("en-US")
                .tenantIsolationSkipped(true)
                .apply()) {
            assertEquals("u1", RequestContext.getUserId());
            assertEquals("t1", RequestContext.getTenantId());
            assertEquals("tr1", RequestContext.getTraceId());
            assertEquals("r1", RequestContext.getRequestId());
            assertEquals("en-US", RequestContext.getLanguage());
            assertTrue(RequestContext.isTenantIsolationSkipped());
        }
        // apply 返回的 guard 在 try 块结束后自动清理
        assertNull(RequestContext.getUserId());
    }

    @Test
    @DisplayName("builder() 未设置的属性不覆盖已有上下文")
    void builder_partialNoOverride() {
        try (RequestContext.CleanupGuard guard = RequestContext.newCleanupGuard()) {
            RequestContext.setUserId("existing");
            RequestContext.builder().tenantId("t1").apply();
            assertEquals("existing", RequestContext.getUserId(), "null field must not overwrite");
            assertEquals("t1", RequestContext.getTenantId());
        }
    }

    @Test
    @DisplayName("普通线程池（非 Ttl 包装）线程复用时上下文不传播")
    void threadPool_noPropagation() throws InterruptedException {
        RequestContext.clear();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 预热：让线程池创建并复用线程（TTL 仅在 Thread 创建时继承一次值）
            CountDownLatch warmup = new CountDownLatch(1);
            pool.submit(warmup::countDown);
            assertTrue(warmup.await(5, TimeUnit.SECONDS), "warmup timed out");

            RequestContext.setUserId("main-user");
            CountDownLatch latch = new CountDownLatch(1);
            final String[] workerValue = new String[1];
            pool.submit(() -> {
                workerValue[0] = RequestContext.getUserId();
                latch.countDown();
            });
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNull(workerValue[0], "reused pool thread must NOT inherit context without TtlExecutors");
        } finally {
            pool.shutdownNow();
            RequestContext.clear();
        }
    }

    @Test
    @DisplayName("dump() 返回不可变快照且不影响原上下文")
    void dump_snapshot() {
        RequestContext.clear();
        RequestContext.setUserId("u1");
        RequestContext.setTenantId("t1");

        java.util.Map<String, Object> snapshot = RequestContext.dump();
        assertEquals(2, snapshot.size());
        assertEquals("u1", snapshot.get(RequestContext.KEY_USER_ID));
        assertEquals("t1", snapshot.get(RequestContext.KEY_TENANT_ID));
        // 快照不可变
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put("x", "y"));
        // 修改原上下文不影响快照
        RequestContext.setUserId("u2");
        assertEquals("u1", snapshot.get(RequestContext.KEY_USER_ID));
        RequestContext.clear();
    }

    @Test
    @DisplayName("dump() 上下文未初始化时返回空 Map")
    void dump_emptyWhenUninitialized() {
        RequestContext.clear();
        assertTrue(RequestContext.dump().isEmpty());
    }

    @Test
    @DisplayName("线程间上下文隔离")
    void threadIsolation() throws InterruptedException {
        RequestContext.clear();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch latch = new CountDownLatch(2);
            final boolean[] crossTalk = {false};
            Runnable task = () -> {
                // 每条线程设置自己的值后读取
                RequestContext.setUserId(Thread.currentThread().getName());
                if (!Thread.currentThread().getName().equals(RequestContext.getUserId())) {
                    crossTalk[0] = true;
                }
                RequestContext.clear();
                latch.countDown();
            };
            pool.submit(task);
            pool.submit(task);
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertFalse(crossTalk[0], "threads must not see each other's context");
        } finally {
            pool.shutdownNow();
        }
    }
}
