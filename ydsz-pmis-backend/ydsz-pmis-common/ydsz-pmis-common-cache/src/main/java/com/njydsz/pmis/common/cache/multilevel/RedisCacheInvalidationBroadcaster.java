package com.njydsz.pmis.common.cache.multilevel;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.njydsz.pmis.common.cache.api.Cache;

/**
 * 基于 Redis Pub/Sub 的缓存失效广播器
 *
 * <p>通过 Redis 发布/订阅频道实现跨节点 L1 缓存失效广播。 当一个节点更新/删除缓存时，
 * 向 Redis 频道发布失效消息，所有订阅节点收到消息后清除本地 L1 缓存。
 *
 * <p>消息格式：cacheName|key|clearAll
 *
 * <ul>
 *   <li>cacheName：缓存名称
 *   <li>key：失效的 key（ClearAll 时为 "*"）
 *   <li>clearAll：true/false
 * </ul>
 *
 * <p>注意：本类需要 Spring Data Redis 在 classpath 中。 如果 Redis 不可用，应降级为
 * NoopCacheInvalidationBroadcaster。
 *
 * @author Marvin Lee
 * @version 4.1.0
 */
public class RedisCacheInvalidationBroadcaster implements CacheInvalidationBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationBroadcaster.class);

  /** 默认 Redis 频道前缀 */
  private static final String DEFAULT_CHANNEL_PREFIX = "ydsz:cache:invalidation:";

  /** ClearAll 标记 */
  private static final String CLEAR_ALL_MARKER = "*";

  /** 分隔符 */
  private static final String SEPARATOR = "|";

  private final RedisTemplate<String, Object> redisTemplate;
  private final String channelPrefix;
  private final List<InvalidationHandler> handlers = new CopyOnWriteArrayList<>();

  /** 本地缓存实例映射（cacheName -> L1 Cache），用于收到广播后清除本地缓存 */
  private final Map<String, Cache<?, ?>> localCaches = new ConcurrentHashMap<>();

  /** 本节点 ID（用于避免处理自己发出的广播消息） */
  private final String nodeId;

  /**
   * 创建 Redis 缓存失效广播器
   *
   * @param redisTemplate Redis 模板
   * @param redisMessageListenerContainer Redis 消息监听容器
   * @param channelPrefix 频道前缀
   */
  public RedisCacheInvalidationBroadcaster(
      RedisTemplate<String, Object> redisTemplate,
      RedisMessageListenerContainer redisMessageListenerContainer,
      String channelPrefix) {
    this.redisTemplate = redisTemplate;
    this.channelPrefix =
        channelPrefix != null ? channelPrefix : DEFAULT_CHANNEL_PREFIX;
    this.nodeId = java.util.UUID.randomUUID().toString();

    // 注册 Redis 消息监听器
    redisMessageListenerContainer.addMessageListener(
        (message, pattern) -> {
          String body = new String(message.getBody());
          String channel = new String(message.getChannel());
          handleIncomingMessage(channel, body);
        },
        new ChannelTopic(this.channelPrefix + "*"));

    log.info("RedisCacheInvalidationBroadcaster 已初始化, nodeId={}, channelPrefix={}", nodeId, this.channelPrefix);
  }

  /** 处理收到的广播消息 */
  private void handleIncomingMessage(String channel, String body) {
    try {
      // 解析频道名获取 cacheName
      String cacheName = channel.substring(channelPrefix.length());

      // 解析消息体
      String[] parts = body.split(SEPARATOR, 4);
      if (parts.length < 3) {
        log.warn("收到格式错误的缓存失效消息: {}", body);
        return;
      }

      String senderNodeId = parts[0];
      // 忽略自己发出的消息
      if (nodeId.equals(senderNodeId)) {
        return;
      }

      boolean clearAll = Boolean.parseBoolean(parts[2]);
      String key = parts[1];

      // 清除本地 L1 缓存
      Cache<?, ?> localCache = localCaches.get(cacheName);
      if (localCache != null) {
        if (clearAll) {
          localCache.clear();
          log.debug("收到广播清除全部本地缓存: cache={}", cacheName);
        } else if (!CLEAR_ALL_MARKER.equals(key)) {
          removeKeyFromCache(localCache, key);
          log.debug("收到广播清除本地缓存: cache={}, key={}", cacheName, key);
        }
      }

      // 通知注册的处理器
      for (InvalidationHandler handler : handlers) {
        try {
          handler.onInvalidation(cacheName, CLEAR_ALL_MARKER.equals(key) ? null : key, clearAll);
        } catch (Exception e) {
          log.warn("缓存失效处理器执行异常", e);
        }
      }
    } catch (Exception e) {
      log.warn("处理缓存失效广播消息异常: {}", body, e);
    }
  }

  @Override
  public void broadcastInvalidation(String cacheName, Object key) {
    if (key == null) {
      return;
    }
    String message = nodeId + SEPARATOR + key.toString() + SEPARATOR + "false";
    try {
      redisTemplate.convertAndSend(channelPrefix + cacheName, message);
    } catch (Exception e) {
      log.warn("广播缓存失效消息失败: cache={}, key={}", cacheName, key, e);
    }
  }

  @Override
  public void broadcastInvalidationAll(String cacheName, Collection<Object> keys) {
    if (keys == null || keys.isEmpty()) {
      return;
    }
    for (Object key : keys) {
      broadcastInvalidation(cacheName, key);
    }
  }

  @Override
  public void broadcastClearAll(String cacheName) {
    String message = nodeId + SEPARATOR + CLEAR_ALL_MARKER + SEPARATOR + "true";
    try {
      redisTemplate.convertAndSend(channelPrefix + cacheName, message);
    } catch (Exception e) {
      log.warn("广播全量清除缓存消息失败: cache={}", cacheName, e);
    }
  }

  @Override
  public void registerHandler(InvalidationHandler handler) {
    if (handler != null) {
      handlers.add(handler);
    }
  }

  /**
   * 注册本地缓存实例
   *
   * @param cacheName 缓存名称
   * @param cache 本地 L1 缓存实例
   */
  public void registerLocalCache(String cacheName, Cache<?, ?> cache) {
    localCaches.put(cacheName, cache);
  }

  /** 注销本地缓存实例 */
  public void unregisterLocalCache(String cacheName) {
    localCaches.remove(cacheName);
  }

  /** 类型安全地从通配类型缓存中移除 key */
  private <K, V> void removeKeyFromCache(Cache<K, V> cache, Object key) {
    K typedKey = castKey(key);
    cache.remove(typedKey);
  }

  /** 将 Object key 安全转型为缓存所需的 key 类型 */
  private <K> K castKey(Object key) {
    Class<?> keyClass = key.getClass();
    try {
      return (K) keyClass.cast(key);
    } catch (ClassCastException e) {
      return (K) key;
    }
  }
}
