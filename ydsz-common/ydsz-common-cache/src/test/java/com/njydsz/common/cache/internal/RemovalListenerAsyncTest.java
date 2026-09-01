package com.njydsz.common.cache.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.listener.RemovalListener;
import com.njydsz.common.cache.api.Cache;

/**
 * 删除监听器异步化测试（对标 Caffeine RemovalListeners.async）。
 *
 * <p>锁定的不变量：
 *
 * <ul>
 *   <li>监听器回调在专用执行器线程上执行，不占用缓存操作线程（旧实现在写锁内同步回调）
 *   <li>回调异常不影响缓存操作
 *   <li>回调最终被触发（异步不丢事件）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class RemovalListenerAsyncTest {

  @Test
  @DisplayName("监听器异步回调：remove 触发的回调在非调用线程上执行")
  void removalListenerShouldRunOnExecutorThread() throws Exception {
    Cache<String, String> cache = YdszCache.<String, String>newBuilder().build();
    AtomicReference<String> callbackThreadName = new AtomicReference<>();
    CountDownLatch callbackDone = new CountDownLatch(1);
    final Thread callerThread = Thread.currentThread();

    cache.addListener(
        (RemovalListener<String, String>)
            (key, value, cause) -> {
              callbackThreadName.set(Thread.currentThread().getName());
              callbackDone.countDown();
            });

    cache.put("doomed", "value");
    cache.remove("doomed");

    assertThat(callbackDone.await(5, TimeUnit.SECONDS))
        .as("异步回调应在超时前触发")
        .isTrue();
    assertThat(callbackThreadName.get())
        .as("回调应运行在异步执行器线程（ydsz-cache-listenerPool-*），而非调用线程")
        .isNotNull()
        .contains("listenerPool")
        .isNotEqualTo(callerThread.getName());
  }

  @Test
  @DisplayName("监听器回调异常不影响缓存操作")
  void listenerExceptionShouldNotBreakCacheOperations() throws Exception {
    Cache<String, String> cache = YdszCache.<String, String>newBuilder().build();
    CountDownLatch callbackDone = new CountDownLatch(1);

    cache.addListener(
        (RemovalListener<String, String>)
            (key, value, cause) -> {
              callbackDone.countDown();
              throw new RuntimeException("listener boom");
            });

    cache.put("k1", "v1");
    cache.remove("k1");
    assertThat(callbackDone.await(5, TimeUnit.SECONDS)).isTrue();

    // 异常后缓存继续正常工作
    cache.put("k2", "v2");
    assertThat(cache.getIfPresent("k2")).isEqualTo("v2");
  }

  @Test
  @DisplayName("容量淘汰事件同样异步派发且不丢失")
  void evictionEventShouldBeDeliveredAsynchronously() throws Exception {
    Cache<String, String> cache =
        YdszCache.<String, String>newBuilder().maximumSize(10).build();
    CountDownLatch evicted = new CountDownLatch(1);

    cache.addListener(
        (RemovalListener<String, String>)
            (key, value, cause) -> {
              if ("evicted-key".equals(key)) {
                evicted.countDown();
              }
            });

    // 写满并制造一次容量淘汰
    cache.put("evicted-key", "v");
    for (int i = 0; i < 20; i++) {
      cache.put("fill-" + i, "v" + i);
    }

    assertThat(evicted.await(5, TimeUnit.SECONDS))
        .as("容量淘汰的监听器事件应异步送达")
        .isTrue();
  }
}
