package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 雪花算法 ID 生成器单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@DisplayName("雪花算法 ID 生成器测试")
class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("nextId 应返回正数 Long")
    void nextId_shouldReturnPositiveLong() {
        long id = SnowflakeIdGenerator.nextId();
        assertTrue(id > 0, "雪花 ID 应为正数: " + id);
    }

    @Test
    @DisplayName("nextIdStr 应返回数字字符串")
    void nextIdStr_shouldReturnNumericString() {
        String id = SnowflakeIdGenerator.nextIdStr();
        assertTrue(id.matches("\\d+"), "雪花 ID 字符串应为纯数字: " + id);
        assertTrue(id.length() >= 15 && id.length() <= 19, "雪花 ID 长度应在 15-19 位之间: " + id.length());
    }

    @Test
    @DisplayName("nextTraceId 应返回 16 位 16 进制字符串")
    void nextTraceId_shouldReturn16CharHex() {
        String traceId = SnowflakeIdGenerator.nextTraceId();
        assertEquals(16, traceId.length(), "traceId 长度应为 16: " + traceId);
        assertTrue(traceId.matches("[0-9a-f]{16}"), "traceId 应为 16 进制: " + traceId);
    }

    @Test
    @DisplayName("单线程连续生成 10000 个 ID 应全部唯一")
    void nextId_singleThread_shouldBeUnique() {
        Set<Long> ids = new HashSet<>();
        int count = 10000;
        for (int i = 0; i < count; i++) {
            long id = SnowflakeIdGenerator.nextId();
            assertTrue(ids.add(id), "第 " + i + " 个 ID 重复: " + id);
        }
        assertEquals(count, ids.size(), "应生成 " + count + " 个唯一 ID");
    }

    @Test
    @DisplayName("多线程并发生成 100000 个 ID 应全部唯一")
    void nextId_concurrent_shouldBeUnique() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 10000;
        Set<Long> ids = new HashSet<>();
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < idsPerThread; i++) {
                            synchronized (ids) {
                                ids.add(SnowflakeIdGenerator.nextId());
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(30, TimeUnit.SECONDS), "并发生成应在 30s 内完成");
            int expected = threadCount * idsPerThread;
            assertEquals(expected, ids.size(),
                    "应生成 " + expected + " 个唯一 ID，实际 " + ids.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("ID 应单调递增（同一毫秒内）")
    void nextId_shouldBeMonotonic() {
        long prev = SnowflakeIdGenerator.nextId();
        for (int i = 0; i < 100; i++) {
            long curr = SnowflakeIdGenerator.nextId();
            assertTrue(curr > prev, "ID 应单调递增: prev=" + prev + ", curr=" + curr);
            prev = curr;
        }
    }

    @Test
    @DisplayName("getWorkerId 应返回非负值")
    void getWorkerId_shouldReturnNonNegative() {
        long wid = SnowflakeIdGenerator.getWorkerId();
        assertTrue(wid >= 0, "workerId 应非负: " + wid);
    }
}
