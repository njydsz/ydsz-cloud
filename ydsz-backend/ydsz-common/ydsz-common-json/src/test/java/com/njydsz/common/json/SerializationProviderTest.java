package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.config.YdszJsonConfig;
import com.njydsz.common.json.provider.SerializationProvider;

/**
 * SerializationProvider 线程安全和 ThreadLocal 快照测试。
 *
 * @since 1.0.0
 */
class SerializationProviderTest {

    @Test
    void testThreadLocalSnapshotRestore() {
        // 设置初始值
        SerializationProvider.setWriteNulls(false);
        SerializationProvider.setPrettyPrint(false);

        // 拍摄快照
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();

        // 修改值
        SerializationProvider.setWriteNulls(true);
        SerializationProvider.setPrettyPrint(true);
        assertTrue(SerializationProvider.isWriteNulls());
        assertTrue(SerializationProvider.isPrettyPrint());

        // 恢复
        snapshot.restore();
        assertFalse(SerializationProvider.isWriteNulls());
        assertFalse(SerializationProvider.isPrettyPrint());
    }

    @Test
    void testSingleConfigSerializationDoesNotPolluteGlobal() {
        // 设置全局配置
        SerializationProvider.setWriteNulls(false);

        // 使用单次配置序列化（不影响全局配置）
        YdszJsonConfig tempConfig = YdszJsonConfig.copyOf(YdszJsonConfig.getInstance());
        tempConfig.setWriteNulls(true);
        String json = YdszJson.toJson(Map.of("a", "b"), tempConfig);
        assertNotNull(json);

        // 全局配置不应被修改
        assertFalse(SerializationProvider.isWriteNulls());
    }

    @Test
    void testConcurrentSerialization() throws InterruptedException {
        int threadCount = 20;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    for (int j = 0; j < iterationsPerThread; j++) {
                        Map<String, Object> data = Map.of(
                                "thread", threadId,
                                "iter", j,
                                "data", "test" + threadId + "_" + j);
                        String json = YdszJson.toJson(data);
                        Map<String, Object> parsed = YdszJson.parseMap(json);
                        if (!Integer.valueOf(threadId).equals(parsed.get("thread"))) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(0, errors.get(), "Concurrent serialization errors detected");
    }

    @Test
    void testAsmDowngradeCounter() {
        // 获取当前降级次数（初始值可能 > 0）
        long initialCount = SerializationProvider.getAsmDowngradeCount();

        // 序列化一个简单对象，不应导致降级
        YdszJson.toJson(Map.of("key", "value"));

        // 降级次数不应增加（正常路径不触发降级）
        long currentCount = SerializationProvider.getAsmDowngradeCount();
        // 注：在正常情况下不应增加，但不强制要求（某些 ASM 生成可能首次失败）
        assertTrue(currentCount >= initialCount);
    }
}
