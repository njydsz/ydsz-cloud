package com.remisoft.common.cache.multilevel;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.remisoft.common.cache.api.Cache;
import com.remisoft.common.json.RemiJson;
import com.remisoft.common.util.id.IdGenerator;

/**
 * 基于 Redis Pub/Sub 的缓存失效广播器
 *
 * <p>通过 Redis 发布/订阅频道实现跨节点 L1 缓存失效广播。 当一个节点更新/删除缓存时，
 * 向 Redis 频道发布失效消息，所有订阅节点收到消息后清除本地 L1 缓存。
 *
 * <p>消息格式（JSON）：{"senderNodeId":"...","key":"...","clearAll":false}
 *
 * <p>注意：本类需要 Spring Data Redis 在 classpath 中。 如果 Redis 不可用，应降级为
 * NoopCacheInvalidationBroadcaster。
 *
 *
 * @author remi-team
 * @since 1.0.0
 */
public class RedisCacheInvalidationBroadcaster implements CacheInvalidationBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationBroadcaster.class);

  /** 默认 Redis 频道前缀 */
  private static final String DEFAULT_CHANNEL_PREFIX = "remi:cache:invalidation:";

  /** ClearAll 标记 */
  private static final String CLEAR_ALL_MARKER = "*";

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
    this.nodeId = IdGenerator.nextIdStr();

    // 注册 Redis 消息监听器
    redisMessageListenerContainer.addMessageListener(
        (message, pattern) -> {
          String body = new String(message.getBody(), StandardCharsets.UTF_8);
          String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
          handleIncomingMessage(channel, body);
        },
        new ChannelTopic(this.channelPrefix + "*"));

    log.info("RedisCacheInvalidationBroadcaster 已初始化, nodeId={}, channelPrefix={}", nodeId, this.channelPrefix);
  }

  /** 失效消息字段名 */
  private static final String FIELD_SENDER = "senderNodeId";
  private static final String FIELD_KEY = "key";
  private static final String FIELD_CLEAR_ALL = "clearAll";

  /** 处理收到的广播消息 */
  private void handleIncomingMessage(String channel, String body) {
    try {
      // 解析频道名获取 cacheName
      String cacheName = channel.substring(channelPrefix.length());

      // 使用 JSON 解析消息体
      Map<String, Object> msg = RemiJson.parseMap(body);
      if (msg == null) {
        log.warn("收到空缓存失效消息: channel={}", channel);
        return;
      }

      String senderNodeId = (String) msg.get(FIELD_SENDER);
      // 忽略自己发出的消息
      if (nodeId.equals(senderNodeId)) {
        return;
      }

      boolean clearAll = Boolean.TRUE.equals(msg.get(FIELD_CLEAR_ALL));
      String key = (String) msg.get(FIELD_KEY);

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
          handler.onInvalidation(
              cacheName,
              CLEAR_ALL_MARKER.equals(key) ? null : key,
              clearAll);
        } catch (Exception e) {
          log.warn("缓存失效处理器执行异常", e);
        }
      }
    } catch (Exception e) {
      log.warn("处理缓存失效广播消息异常: body={}", body, e);
    }
  }

  @Override
  public void broadcastInvalidation(String cacheName, Object key) {
    if (key == null) {
      return;
    }
    Map<String, Object> msg = new HashMap<>(3);
    msg.put(FIELD_SENDER, nodeId);
    msg.put(FIELD_KEY, key.toString());
    msg.put(FIELD_CLEAR_ALL, false);
    try {
      String json = RemiJson.toJson(msg);
      redisTemplate.convertAndSend(channelPrefix + cacheName, json);
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
    Map<String, Object> msg = new HashMap<>(3);
    msg.put(FIELD_SENDER, nodeId);
    msg.put(FIELD_KEY, CLEAR_ALL_MARKER);
    msg.put(FIELD_CLEAR_ALL, true);
    try {
      String json = RemiJson.toJson(msg);
      redisTemplate.convertAndSend(channelPrefix + cacheName, json);
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

  /** 从通配类型缓存中移除 key（跨节点广播场景，key 为 String） */
  private void removeKeyFromCache(Cache<?, ?> cache, Object key) {
    // 广播消息中的 key 始终为 String（由 toString() 生成）。
    // 由于类型擦除，Cache.remove 在运行时接受 Object。
    // 通过泛型捕获转换安全调用，运行时类型安全由缓存实现保证。
    if (key != null) {
      removeCaptured(cache, key);
    }
  }

  /**
   * 泛型捕获辅助方法
   *
   * <p>利用编译器的通配类型捕获机制，将 {@code Cache<?, ?>} 捕获为 {@code Cache<KK, VV>}， 然后在方法签名内安全地接受 Object 参数。由于类型擦除，运行时 remove
   * 接受任意类型。
   */
  private <KK, VV> void removeCaptured(Cache<KK, VV> cache, Object key) {
    cache.remove((KK) key);
  }
}
