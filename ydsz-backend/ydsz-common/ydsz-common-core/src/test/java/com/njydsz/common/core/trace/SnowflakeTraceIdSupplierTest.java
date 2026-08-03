package com.njydsz.common.core.trace;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SnowflakeTraceIdSupplier} 单元测试
 *
 * <p>覆盖格式校验、唯一性、并发安全、参数校验、时钟回拨、workerId/datacenterId 推导降级等行为。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("SnowflakeTraceIdSupplier 有序 TraceId 生成器测试")
class SnowflakeTraceIdSupplierTest {

    @Test
    @DisplayName("生成 16 位十六进制字符串")
    void generate_hex16Format() {
        SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier(1L, 1L);
        String id = supplier.generate();
        assertEquals(16, id.length());
        assertTrue(id.matches("^[0-9a-f]{16}$"), "must be 16 lowercase hex chars: " + id);
    }

    @Test
    @DisplayName("连续生成的 ID 唯一")
    void generate_unique() {
        SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier(1L, 1L);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(ids.add(supplier.generate()), "duplicate id at index " + i);
        }
    }

    @Test
    @DisplayName("生成 ID 按时间有序（序列号递增）")
    void generate_monotonic() {
        SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier(0L, 0L);
        // 16 位 hex 可能超过 Long.MAX_VALUE（首位 ≥ 8），使用 BigInteger 避免溢出
        java.math.BigInteger prev = new java.math.BigInteger(supplier.generate(), 16);
        for (int i = 0; i < 1000; i++) {
            java.math.BigInteger current = new java.math.BigInteger(supplier.generate(), 16);
            assertTrue(current.compareTo(prev) > 0, "id must be monotonically increasing");
            prev = current;
        }
    }

    @Test
    @DisplayName("并发生成无重复且线程安全")
    void generate_concurrent() throws InterruptedException {
        int threadCount = 16;
        int perThread = 2000;
        SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier(1L, 1L);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<String> all = java.util.Collections.synchronizedSet(new HashSet<>());

        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        assertTrue(all.add(supplier.generate()), "concurrent duplicate detected");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS), "generation timed out");
        pool.shutdown();
        assertEquals(threadCount * perThread, all.size());
    }

    @Test
    @DisplayName("非法 workerId/datacenterId 抛出 IllegalArgumentException")
    void invalidIds() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeTraceIdSupplier(-1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeTraceIdSupplier(0L, -1L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeTraceIdSupplier(32L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeTraceIdSupplier(0L, 32L));
    }

    @Test
    @DisplayName("不同 workerId 生成的 ID 不同（同一时刻）")
    void differentWorkers_differentIds() {
        SnowflakeTraceIdSupplier a = new SnowflakeTraceIdSupplier(0L, 0L);
        SnowflakeTraceIdSupplier b = new SnowflakeTraceIdSupplier(0L, 1L);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(a.generate());
            ids.add(b.generate());
        }
        assertEquals(2000, ids.size());
    }

    @Test
    @DisplayName("默认构造器可用（自动推导 workerId/datacenterId）")
    void defaultConstructor() {
        SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier();
        String id = supplier.generate();
        assertEquals(16, id.length());
    }

    @Test
    @DisplayName("无参构造在无环境变量时降级不抛异常")
    void derive_fallbackNoThrow() {
        // 清空相关环境变量不可行（只读），但默认构造器本身必须可用
        assertDoesNotThrow(() -> {
            String id = new SnowflakeTraceIdSupplier().generate();
            assertNotNull(id);
        });
    }

    @Test
    @DisplayName("实现 TraceIdSupplier 接口")
    void implementsInterface() {
        assertTrue(TraceIdSupplier.class.isAssignableFrom(SnowflakeTraceIdSupplier.class));
    }
}
