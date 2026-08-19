package com.njydsz.system.server.cache;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;

/**
 * 跨实例缓存失效消息订阅者（P1-7 改进）。
 *
 * <p>订阅 Redis Pub/Sub 频道 {@link CacheInvalidationPublisher#CHANNEL}，接收其他实例发布的缓存失效消息，清除本地缓存，实现跨实例实时一致性。
 *
 * <p><b>工作流程：</b>
 *
 * <ol>
 *   <li>订阅 {@code ydsz:system:cache-invalidation} 频道
 *   <li>收到消息（格式 {@code cacheName:key}）后，解析并清除本地缓存对应键
 *   <li>消息格式异常或缓存不存在时静默跳过（安全容错）
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationSubscriber implements MessageListener {

  private final CacheManager cacheManager;
  private final RedisConnectionFactory connectionFactory;
  private final StringRedisTemplate redisTemplate;

  private RedisMessageListenerContainer container;

  /**
   * 启动 Redis 订阅容器。
   *
   * <p>使用独立线程异步订阅，不阻塞应用启动。
   */
  @PostConstruct
  public void start() {
    try {
      container = new RedisMessageListenerContainer();
      container.setConnectionFactory(connectionFactory);
      container.setTaskExecutor(Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cache-invalidation-subscriber");
        t.setDaemon(true);
        return t;
      }));
      container.addMessageListener(this, new ChannelTopic(CacheInvalidationPublisher.CHANNEL));
      container.start();
      log.info("[CacheInvalidationSubscriber] 启动 Redis 缓存失效订阅: channel={}", CacheInvalidationPublisher.CHANNEL);
    } catch (Exception e) {
      // Redis 不可用时降级（不影响启动，仅丧失跨实例实时一致性）
      log.warn("[CacheInvalidationSubscriber] 启动 Redis 订阅失败（将降级为本地失效）: {}", e.getMessage());
    }
  }

  /**
   * 停止订阅容器。
   */
  @PreDestroy
  public void stop() {
    if (container != null && container.isRunning()) {
      container.stop();
      log.info("[CacheInvalidationSubscriber] 停止 Redis 缓存失效订阅");
    }
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String body = new String(message.getBody());
    log.debug("[CacheInvalidationSubscriber] 收到缓存失效消息: {}", body);

    // 解析消息格式: cacheName:key
    int colonIndex = body.indexOf(':');
    if (colonIndex < 1) {
      log.warn("[CacheInvalidationSubscriber] 消息格式异常，跳过: {}", body);
      return;
    }
    String cacheName = body.substring(0, colonIndex);
    String key = body.substring(colonIndex + 1);

    // 清除本地缓存
    Cache cache = cacheManager.getCache(cacheName);
    if (cache != null) {
      cache.evict(key);
      log.debug("[CacheInvalidationSubscriber] 清除本地缓存: cacheName={}, key={}", cacheName, key);
    }
  }
}
