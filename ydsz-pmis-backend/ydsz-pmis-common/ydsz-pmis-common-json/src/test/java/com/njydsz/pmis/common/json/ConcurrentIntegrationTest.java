package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.annotation.YdszJsonClass;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("并发集成测试")
class ConcurrentIntegrationTest {

    // ==================== 测试模型 ====================

    @YdszJsonClass
    static class ConcurrentItem {
        private long id;
        private String name;
        private double value;

        public ConcurrentItem() {}

        public ConcurrentItem(long id, String name, double value) {
            this.id = id;
            this.name = name;
            this.value = value;
        }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
    }

    private static final int THREAD_COUNT = 100;

    // ==================== 并发序列化测试 ====================

    @Test
    @DisplayName("100线程并发序列化 - 无数据损坏")
    void concurrentSerializationWith100Threads() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<String> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConcurrentItem item = new ConcurrentItem(index, "item_" + index, index * 1.5);
                    String json = YdszJson.toJson(item);
                    results.add(json);
                    if (!json.contains("item_" + index)) {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "所有线程应在30秒内完成");
        executor.shutdown();

        assertEquals(THREAD_COUNT, results.size(), "应产生与线程数相同的结果");
        assertEquals(0, errorCount.get(), "不应有任何错误");
    }

    // ==================== 并发反序列化测试 ====================

    @Test
    @DisplayName("100线程并发反序列化 - 无数据损坏")
    void concurrentDeserializationWith100Threads() throws Exception {
        String json = "{\"id\":42,\"name\":\"test_item\",\"value\":3.14}";
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConcurrentItem item = YdszJson.toObject(json, ConcurrentItem.class);
                    if (item.getId() != 42 || !"test_item".equals(item.getName())) {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "所有线程应在30秒内完成");
        executor.shutdown();

        assertEquals(0, errorCount.get(), "不应有任何数据损坏");
    }

    // ==================== 并发混合读写测试 ====================

    @Test
    @DisplayName("并发混合读写操作 - 无数据损坏")
    void concurrentMixedReadWriteOperations() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (index % 2 == 0) {
                        // 写操作：序列化
                        ConcurrentItem item = new ConcurrentItem(index, "user_" + index, index * 1.5);
                        String json = YdszJson.toJson(item);
                        if (!json.contains("user_" + index)) {
                            errorCount.incrementAndGet();
                        }
                    } else {
                        // 读操作：反序列化
                        String json = "{\"id\":" + index + ",\"name\":\"user_" + index + "\",\"value\":" + (index * 1.5) + "}";
                        ConcurrentItem item = YdszJson.toObject(json, ConcurrentItem.class);
                        if (item.getId() != index) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "所有线程应在30秒内完成");
        executor.shutdown();

        assertEquals(0, errorCount.get(), "混合读写不应产生数据损坏");
    }

    // ==================== ThreadLocal 清理测试 ====================

    @Test
    @DisplayName("线程池复用后 ThreadLocal 清理 - 无数据泄漏")
    void threadLocalCleanupAfterPoolReuse() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // 第一轮：序列化一批对象
        List<Future<String>> firstRound = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i;
            firstRound.add(executor.submit(() -> {
                ConcurrentItem item = new ConcurrentItem(index, "round1_" + index, index * 10.0);
                return YdszJson.toJson(item);
            }));
        }

        for (Future<String> future : firstRound) {
            assertNotNull(future.get(10, TimeUnit.SECONDS));
        }

        // 第二轮：复用相同线程，序列化不同数据
        List<Future<ConcurrentItem>> secondRound = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i + 100;
            String json = "{\"id\":" + index + ",\"name\":\"round2_" + index + "\",\"value\":" + (index * 20.0) + "}";
            secondRound.add(executor.submit(() -> YdszJson.toObject(json, ConcurrentItem.class)));
        }

        for (int i = 0; i < 10; i++) {
            ConcurrentItem item = secondRound.get(i).get(10, TimeUnit.SECONDS);
            int expectedId = i + 100;
            assertEquals(expectedId, item.getId(), "线程复用后不应泄漏前一轮数据");
            assertEquals("round2_" + expectedId, item.getName(), "线程复用后名称应正确");
        }

        executor.shutdown();
    }

    // ==================== 高并发数据完整性验证 ====================

    @Test
    @DisplayName("高并发下数据完整性验证 - 序列化后反序列化应一致")
    void noDataCorruptionUnderHighConcurrency() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // 序列化
                    ConcurrentItem original = new ConcurrentItem(index, "integrity_" + index, index * 2.5);
                    String json = YdszJson.toJson(original);

                    // 反序列化
                    ConcurrentItem restored = YdszJson.toObject(json, ConcurrentItem.class);

                    // 验证数据完整性
                    if (original.getId() != restored.getId()
                            || !original.getName().equals(restored.getName())) {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "所有线程应在30秒内完成");
        executor.shutdown();

        assertEquals(0, errorCount.get(), "高并发下序列化-反序列化往返不应产生数据损坏");
    }
}
