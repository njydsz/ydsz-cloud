package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SnowflakeIdGenerator} 雪花算法 ID 生成器测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SnowflakeIdGenerator 雪花 ID 生成器测试")
class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("nextId() 返回正数")
    void shouldReturnPositiveId() {
        long id = SnowflakeIdGenerator.nextId();
        assertTrue(id > 0);
    }

    @Test
    @DisplayName("nextIdStr() 返回数字字符串")
    void shouldReturnNumericString() {
        String id = SnowflakeIdGenerator.nextIdStr();
        assertNotNull(id);
        assertTrue(id.matches("\\d+"));
    }

    @Test
    @DisplayName("连续生成 1000 个 ID 全部唯一")
    void shouldGenerateUniqueIds() {
        int count = 1000;
        java.util.Set<Long> ids = new java.util.HashSet<>(count);
        for (int i = 0; i < count; i++) {
            long id = SnowflakeIdGenerator.nextId();
            assertTrue(ids.add(id), "第 %d 个 ID 重复: %d".formatted(i, id));
        }
        assertEquals(count, ids.size());
    }

    @Test
    @DisplayName("ID 单调递增（同一毫秒内也递增）")
    void shouldGenerateMonotonicallyIncreasingIds() {
        long prev = SnowflakeIdGenerator.nextId();
        for (int i = 0; i < 100; i++) {
            long curr = SnowflakeIdGenerator.nextId();
            assertTrue(curr > prev, "ID 非单调递增: prev=%d, curr=%d".formatted(prev, curr));
            prev = curr;
        }
    }

    @Test
    @DisplayName("nextTraceId() 返回 16 位 hex 字符串")
    void shouldReturn16CharHexTraceId() {
        String traceId = SnowflakeIdGenerator.nextTraceId();
        assertNotNull(traceId);
        assertEquals(16, traceId.length());
        assertTrue(traceId.matches("[0-9a-f]{16}"));
    }

    @Test
    @DisplayName("nextTraceId() 连续生成不重复")
    void shouldGenerateUniqueTraceIds() {
        int count = 500;
        java.util.Set<String> ids = new java.util.HashSet<>(count);
        for (int i = 0; i < count; i++) {
            String traceId = SnowflakeIdGenerator.nextTraceId();
            assertTrue(ids.add(traceId));
        }
        assertEquals(count, ids.size());
    }

    @Test
    @DisplayName("getWorkerId() 返回非负值")
    void shouldReturnNonNegativeWorkerId() {
        long wid = SnowflakeIdGenerator.getWorkerId();
        assertTrue(wid >= 0);
    }

    @Test
    @DisplayName("多线程并发生成 ID 全部唯一")
    void shouldGenerateUniqueIdsInMultiThread() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 500;
        java.util.Set<Long> allIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            new Thread(() -> {
                try {
                    for (int i = 0; i < idsPerThread; i++) {
                        allIds.add(SnowflakeIdGenerator.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        assertEquals(threadCount * idsPerThread, allIds.size());
    }
}
