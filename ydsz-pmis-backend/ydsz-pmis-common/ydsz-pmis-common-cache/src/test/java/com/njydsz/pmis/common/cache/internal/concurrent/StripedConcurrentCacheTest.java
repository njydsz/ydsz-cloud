package com.njydsz.pmis.common.cache.internal.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StripedConcurrentCacheTest {

  private StripedConcurrentCache<String, Integer> cache;

  @BeforeEach
  void setUp() {
    cache = new StripedConcurrentCache<>(100);
  }

  @Nested
  @DisplayName("基础操作")
  class BasicOperations {

    @Test
    @DisplayName("put 和 getIfPresent 正常工作")
    void putAndGet() {
      cache.put("a", 1);
      assertThat(cache.getIfPresent("a")).isEqualTo(1);
    }

    @Test
    @DisplayName("覆盖已有值")
    void putOverwrite() {
      cache.put("a", 1);
      cache.put("a", 2);
      assertThat(cache.getIfPresent("a")).isEqualTo(2);
    }

    @Test
    @DisplayName("remove 正常工作")
    void remove() {
      cache.put("a", 1);
      assertThat(cache.remove("a")).isEqualTo(1);
      assertThat(cache.getIfPresent("a")).isNull();
    }

    @Test
    @DisplayName("clear 清空缓存并通知监听器")
    void clear() {
      StringBuilder log = new StringBuilder();
      cache.addListener((key, value, cause) -> log.append(key));
      cache.put("a", 1);
      cache.put("b", 2);
      cache.clear();
      assertThat(cache.estimatedSize()).isZero();
      assertThat(log.toString()).contains("a").contains("b");
    }

    @Test
    @DisplayName("容量满时触发淘汰")
    void evictWhenFull() {
      // 容量 100，4 段，每段阈值约 25，总容量应控制在 100 以内
      StripedConcurrentCache<Integer, Integer> smallCache = new StripedConcurrentCache<>(100, 4);
      for (int i = 0; i < 500; i++) {
        smallCache.put(i, i);
      }
      // 淘汰后容量不应超过 maximumSize
      assertThat(smallCache.estimatedSize()).isLessThanOrEqualTo(100);
    }
  }

  @Nested
  @DisplayName("构造函数边界")
  class ConstructorEdgeCases {

    @Test
    @DisplayName("stripes=1 不崩溃")
    void stripesOneDoesNotCrash() {
      StripedConcurrentCache<String, Integer> c = new StripedConcurrentCache<>(100, 1);
      c.put("a", 1);
      assertThat(c.getIfPresent("a")).isEqualTo(1);
    }

    @Test
    @DisplayName("stripes=2 正常工作")
    void stripesTwo() {
      StripedConcurrentCache<String, Integer> c = new StripedConcurrentCache<>(100, 2);
      c.put("a", 1);
      c.put("b", 2);
      assertThat(c.getIfPresent("a")).isEqualTo(1);
      assertThat(c.getIfPresent("b")).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("并发安全")
  class Concurrency {

    @Test
    @DisplayName("高并发读写不抛异常")
    void highConcurrency() throws InterruptedException {
      int threadCount = 16;
      Thread[] threads = new Thread[threadCount];
      for (int t = 0; t < threadCount; t++) {
        final int threadId = t;
        threads[t] =
            new Thread(
                () -> {
                  for (int i = 0; i < 5000; i++) {
                    String key = "key" + (i % 50);
                    if (threadId % 3 == 0) {
                      cache.put(key, i);
                    } else if (threadId % 3 == 1) {
                      cache.getIfPresent(key);
                    } else {
                      cache.remove(key);
                    }
                  }
                });
      }
      for (Thread t : threads) t.start();
      for (Thread t : threads) t.join();
    }
  }
}
