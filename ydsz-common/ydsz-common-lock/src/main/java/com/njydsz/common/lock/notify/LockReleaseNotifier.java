package com.njydsz.common.lock.notify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 锁释放通知器
 *
 * <p>对标 Redisson 的发布订阅唤醒机制：锁释放时向 Redis 频道广播通知， 等待锁的线程通过订阅收到通知后立即重新尝试，替代高竞争场景下的 指数退避轮询，降低无效 Redis
 * QPS 与等待延迟。
 *
 * <p><b>工作机制：</b>
 *
 * <ul>
 *   <li>启动时按 {@code ydsz:lock:release:*} 模式订阅一次，收到消息后唤醒对应锁键的本地等待者
 *   <li>{@link #awaitRelease} 注册等待者并以 {@link CompletableFuture} 阻塞等待，被唤醒或超时后返回
 *   <li>{@link #notifyRelease} 发布 Redis 消息并同时唤醒本地等待者（双保险，兼容订阅延迟）
 *   <li>等待上限 {@value #MAX_AWAIT_MILLIS} ms，即使错过通知也能在限定时间内重新探测锁状态
 * </ul>
 *
 * <p><b>线程安全：</b>等待者集合使用 {@link ConcurrentHashMap} + 写时复制列表，多线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class LockReleaseNotifier {

  /** 释放通知频道前缀，频道格式：{@code ydsz:lock:release:{lockKey}} */
  public static final String RELEASE_CHANNEL_PREFIX = "ydsz:lock:release:";

  /** 单次等待上限（毫秒），防止错过通知后长时间空等 */
  private static final long MAX_AWAIT_MILLIS = 500L;

  /** Redis 操作模板（用于发布释放通知） */
  private final StringRedisTemplate redisTemplate;

  /** 等待者集合：lockKey → 等待中的 CompletableFuture 列表 */
  private final ConcurrentHashMap<String, List<CompletableFuture<Void>>> waiters =
      new ConcurrentHashMap<>();

  /**
   * 构造锁释放通知器并注册模式订阅
   *
   * @param redisTemplate Redis 操作模板
   * @param listenerContainer Redis 消息监听容器（由 common-redis 提供）
   */
  public LockReleaseNotifier(
      StringRedisTemplate redisTemplate, RedisMessageListenerContainer listenerContainer) {
    this.redisTemplate = redisTemplate;
    registerListener(listenerContainer);
  }

  /**
   * 获取单次等待上限（毫秒）
   *
   * @return 等待上限
   */
  public static long getMaxAwaitMillis() {
    return MAX_AWAIT_MILLIS;
  }

  /**
   * 注册释放通知模式订阅
   *
   * @param listenerContainer Redis 消息监听容器
   */
  private void registerListener(RedisMessageListenerContainer listenerContainer) {
    MessageListener listener =
        (message, pattern) -> {
          try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String lockKey = channel.substring(RELEASE_CHANNEL_PREFIX.length());
            wakeWaiters(lockKey);
          } catch (Exception e) {
            log.warn("[ydsz-lock] [notify]处理释放通知异常 | error={}", e.getMessage());
          }
        };
    listenerContainer.addMessageListener(listener, new PatternTopic(RELEASE_CHANNEL_PREFIX + "*"));
    log.info("[ydsz-lock] [notify]已订阅锁释放通知模式={}*", RELEASE_CHANNEL_PREFIX);
  }

  /**
   * 等待指定锁的释放通知
   *
   * <p>阻塞至收到释放通知或超时。超时后调用方应重新尝试获取锁。
   *
   * @param lockKey 锁的键
   * @param timeoutMillis 等待超时（毫秒）
   */
  public void awaitRelease(String lockKey, long timeoutMillis) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    waiters.computeIfAbsent(lockKey, k -> new CopyOnWriteArrayList<>()).add(future);
    try {
      future.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      // 超时未收到通知，由调用方重新探测
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      log.debug("[ydsz-lock] [notify]等待释放通知异常 | lockKey={} | error={}", lockKey, e.getMessage());
    } finally {
      List<CompletableFuture<Void>> list = waiters.get(lockKey);
      if (list != null) {
        list.remove(future);
      }
    }
  }

  /**
   * 发布锁释放通知
   *
   * <p>向 Redis 频道广播释放消息，并直接唤醒本 JVM 内的等待者（兼容订阅建立延迟）。
   *
   * @param lockKey 锁的键
   */
  public void notifyRelease(String lockKey) {
    try {
      redisTemplate.convertAndSend(RELEASE_CHANNEL_PREFIX + lockKey, "1");
    } catch (Exception e) {
      log.warn("[ydsz-lock] [notify]发布释放通知失败 | lockKey={} | error={}", lockKey, e.getMessage());
    }
    wakeWaiters(lockKey);
  }

  /**
   * 唤醒指定锁键的所有本地等待者
   *
   * @param lockKey 锁的键
   */
  private void wakeWaiters(String lockKey) {
    List<CompletableFuture<Void>> list = waiters.remove(lockKey);
    if (list != null) {
      for (CompletableFuture<Void> future : list) {
        future.complete(null);
      }
    }
  }
}
