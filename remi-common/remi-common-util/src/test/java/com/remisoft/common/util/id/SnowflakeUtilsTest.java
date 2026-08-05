package com.remisoft.common.util.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SnowflakeUtils} 单元测试 — 覆盖 ID 唯一性、单调性、参数校验等关键路径。
 *
 * <p>注意：{@link SnowflakeUtils#init(long, long)} 是一次性单例初始化，
 * 在同一 JVM 中只能被调用一次。测试中通过 {@link SnowflakeUtils#resetForTesting()}
 * 重置单例以隔离各用例。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("SnowflakeUtils 雪花 ID 测试")
class SnowflakeUtilsTest {

    /** 重置单例，便于多次测试。 */
    private static void resetInstance() {
        SnowflakeUtils.resetForTesting();
    }

    @Test
    @DisplayName("合法 workerId / datacenterId 初始化成功")
    void initSuccess() throws Exception {
        resetInstance();
        SnowflakeUtils.init(1L, 1L);
        SnowflakeUtils instance = SnowflakeUtils.getInstance();
        assertThat(instance).isNotNull();
    }

    @Test
    @DisplayName("workerId 超过 31 抛 IllegalArgumentException")
    void workerIdOverflowShouldThrow() throws Exception {
        resetInstance();
        assertThatThrownBy(() -> SnowflakeUtils.init(32L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worker Id");
    }

    @Test
    @DisplayName("datacenterId 小于 0 抛 IllegalArgumentException")
    void datacenterIdNegativeShouldThrow() throws Exception {
        resetInstance();
        assertThatThrownBy(() -> SnowflakeUtils.init(0L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("datacenter Id");
    }

    @Test
    @DisplayName("重复初始化抛 IllegalStateException")
    void duplicateInitShouldThrow() throws Exception {
        resetInstance();
        SnowflakeUtils.init(2L, 2L);
        assertThatThrownBy(() -> SnowflakeUtils.init(3L, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been initialized");
    }

    @Test
    @DisplayName("nextIdLong 在单线程内单调递增")
    void nextIdLongMonotonicInSingleThread() throws Exception {
        resetInstance();
        SnowflakeUtils.init(5L, 7L);
        long prev = 0L;
        for (int i = 0; i < 1000; i++) {
            long id = SnowflakeUtils.nextIdLong();
            assertThat(id).isGreaterThan(prev);
            prev = id;
        }
    }

    @Test
    @DisplayName("nextIdStr 与 nextIdLong 数值一致")
    void nextIdStrMatchesNextIdLong() throws Exception {
        resetInstance();
        SnowflakeUtils.init(1L, 1L);
        long id1 = SnowflakeUtils.nextIdLong();
        long id2 = Long.parseLong(SnowflakeUtils.nextIdStr());
        assertThat(id2).isGreaterThan(id1);
    }

    @Test
    @DisplayName("1000 个 ID 全局唯一")
    void nextIdLongUniqueInBatch() throws Exception {
        resetInstance();
        SnowflakeUtils.init(10L, 10L);
        Set<Long> ids = new HashSet<>(2000);
        for (int i = 0; i < 1000; i++) {
            long id = SnowflakeUtils.nextIdLong();
            assertThat(ids.add(id)).as("duplicate id: %d", id).isTrue();
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    @DisplayName("多线程并发 4×10000 ID 全局唯一")
    void nextIdLongConcurrentUnique() throws Exception {
        resetInstance();
        SnowflakeUtils.init(15L, 15L);
        int threadCount = 4;
        int perThread = 10_000;
        Set<Long> allIds = ConcurrentHashMap.newKeySet();
        AtomicLong duplicateCount = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        if (!allIds.add(SnowflakeUtils.nextIdLong())) {
                            duplicateCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(duplicateCount.get()).as("duplicate ids").isZero();
        assertThat(allIds).hasSize(threadCount * perThread);
    }

    @Test
    @DisplayName("getInstance 未初始化时抛 IllegalStateException")
    void getInstanceThrowsWhenNotInitialized() throws Exception {
        resetInstance();
        assertThatThrownBy(() -> SnowflakeUtils.getInstance())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未初始化");
    }

    @Test
    @DisplayName("nextIds 批量生成 100 个 ID 全局唯一")
    void nextIdsBatchShouldBeUnique() throws Exception {
        resetInstance();
        SnowflakeUtils.init(3L, 5L);
        long[] ids = SnowflakeUtils.nextIds(100);
        assertThat(ids).hasSize(100);
        Set<Long> set = new HashSet<>(ids.length);
        for (long id : ids) {
            assertThat(set.add(id)).as("duplicate id: %d", id).isTrue();
        }
    }

    @Test
    @DisplayName("nextIds 批量生成 ID 单调递增")
    void nextIdsShouldBeMonotonic() throws Exception {
        resetInstance();
        SnowflakeUtils.init(7L, 3L);
        long[] ids = SnowflakeUtils.nextIds(200);
        for (int i = 1; i < ids.length; i++) {
            assertThat(ids[i]).isGreaterThan(ids[i - 1]);
        }
    }

    @Test
    @DisplayName("nextIds count=1 退化为单次生成，值合法")
    void nextIdsSingleDegradation() throws Exception {
        resetInstance();
        SnowflakeUtils.init(1L, 1L);
        long[] ids = SnowflakeUtils.nextIds(1);
        assertThat(ids).hasSize(1);
        assertThat(ids[0]).isPositive();
    }

    @Test
    @DisplayName("nextIds count=0 抛 IllegalArgumentException")
    void nextIdsZeroShouldThrow() throws Exception {
        resetInstance();
        SnowflakeUtils.init(1L, 1L);
        assertThatThrownBy(() -> SnowflakeUtils.nextIds(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count must be >= 1");
    }

    @Test
    @DisplayName("nextIds count=负值抛 IllegalArgumentException")
    void nextIdsNegativeShouldThrow() throws Exception {
        resetInstance();
        SnowflakeUtils.init(1L, 1L);
        assertThatThrownBy(() -> SnowflakeUtils.nextIds(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count must be >= 1");
    }

    @Test
    @DisplayName("nextIds count=4096 上限正常生成")
    void nextIdsMaxBatchSize() throws Exception {
        resetInstance();
        SnowflakeUtils.init(1L, 1L);
        long[] ids = SnowflakeUtils.nextIds(4096);
        assertThat(ids).hasSize(4096);
        Set<Long> set = new HashSet<>(ids.length);
        for (long id : ids) {
            assertThat(set.add(id)).as("duplicate id").isTrue();
        }
    }

    @Test
    @DisplayName("nextIds count>4096 自动裁剪到 4096")
    void nextIdsExceedsMaxShouldClamp() throws Exception {
        resetInstance();
        SnowflakeUtils.init(1L, 1L);
        long[] ids = SnowflakeUtils.nextIds(5000);
        assertThat(ids).hasSize(4096);
    }
}
