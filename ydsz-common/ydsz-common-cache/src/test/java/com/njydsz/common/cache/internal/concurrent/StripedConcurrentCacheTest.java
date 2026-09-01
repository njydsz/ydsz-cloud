package com.njydsz.common.cache.internal.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;

/**
 * StripedConcurrentCache 双结构一致性并发测试。
 *
 * <p>背景（P0，JMH 16 线程基准实跑抓获）：旧实现的 map.putIfAbsent 在 evictLock 外发布节点，
 * 窗口内节点可被并发 remove/evict 摘走后再接回链表形成幽灵节点；幽灵节点指针为残留旧值，
 * 后续 removeFromList 依据它错写前驱/后继指针导致断链（evictOne 采样循环 NPE）。
 *
 * <p>本测试以小容量 + 高频写 + 混合读写删除构造高竞争场景，锁定修复后不变量：
 * 任何操作不得抛异常，且最终缓存与计数值一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class StripedConcurrentCacheTest {

  /** 并发线程数 */
  private static final int THREADS = 16;

  /** key 空间（远大于容量，保证持续淘汰竞争） */
  private static final int KEY_SPACE = 2000;

  @Test
  @DisplayName("双结构一致性：小容量高频写淘汰下无断链异常（P0 回归）")
  void mixedOperationsUnderEvictionPressureShouldNotBreakChain() throws Exception {
    Cache<Integer, Integer> cache =
        YdszCache.<Integer, Integer>newBuilder()
            .type(CacheType.STRIPED)
            .maximumSize(100)
            .build();

    AtomicInteger errors = new AtomicInteger();
    CountDownLatch stop = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(THREADS);

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      for (int t = 0; t < THREADS; t++) {
        final Random rnd = new Random(42L + t);
        pool.submit(
            () -> {
              try {
                while (!stop.await(0, TimeUnit.MILLISECONDS)) {
                  int key = rnd.nextInt(KEY_SPACE);
                  int op = rnd.nextInt(10);
                  if (op < 6) {
                    cache.getIfPresent(key);
                  } else if (op < 9) {
                    cache.put(key, key);
                  } else {
                    cache.remove(key);
                  }
                }
              } catch (Throwable e) {
                errors.incrementAndGet();
              } finally {
                done.countDown();
              }
            });
      }
      // 高竞争窗口（断链在旧实现下秒级复现）
      Thread.sleep(5_000);
      stop.countDown();
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    assertThat(errors.get()).as("混合读写删除不得抛异常（旧实现为 evictOne 断链 NPE）").isZero();
    // 容量上限不变量：压力后条目数不得超过 maximumSize
    assertThat(cache.estimatedSize()).as("容量上限不被突破").isLessThanOrEqualTo(100L);
  }
}
