package com.njydsz.common.cache.multilevel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 基于 Redis Pub/Sub 的缓存失效广播器
 *
 * <p>通过 Redis 发布/订阅频道实现跨节点 L1 缓存失效广播。当一个节点更新/删除缓存时，
 * 向 Redis 频道发布失效消息，所有订阅节点收到消息后清除本地 L1 缓存。
 *
 * <p>消息格式（JSON）：
 *
 * <pre>
 * {
 *   "senderNodeId": "node-xxx",
 *   "key": "user:10086",
 *   "clearAll": false,
 *   "seq": 42,
 *   "keys": ["k1", "k2"]
 * }
 * </pre>
 *
 * <p>可靠性增强：
 *
 * <ul>
 *   <li>序列号（seq）：每条消息包含单调递增序列号，检测消息乱序或丢失</li>
 *   <li>批量广播（keys）：单次广播多个键，减少 Redis 发布次数</li>
 *   <li>消息验证：解析后对关键字段做类型校验，避免 ClassCastException</li>
 *   <li>前缀一致性：频道名为 {@code prefix + cacheName}，避免不同 cache 串扰</li>
 * </ul>
 *
 * <p>注意：本类需要 Spring Data Redis 在 classpath 中。如果 Redis 不可用，应降级为
 * NoopCacheInvalidationBroadcaster。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RedisCacheInvalidationBroadcaster implements CacheInvalidationBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationBroadcaster.class);

  /** 默认 Redis 频道前缀 */
  private static final String DEFAULT_CHANNEL_PREFIX = "ydsz:cache:invalidation:";

  /** ClearAll 标记 */
  private static final String CLEAR_ALL_MARKER = "*";

  /** 单次批量广播最大键数 */
  private static final int MAX_BATCH_KEYS = 100;

  private final RedisTemplate<String, Object> redisTemplate;
  private final String channelPrefix;
  private final List<InvalidationHandler> handlers = new CopyOnWriteArrayList<>();

  /** 本地缓存实例映射（cacheName -> L1 Cache），用于收到广播后清除本地缓存 */
  private final Map<String, Cache<?, ?>> localCaches = new ConcurrentHashMap<>();

  /** 本节点 ID（用于避免处理自己发出的广播消息） */
  private final String nodeId;

  /** 序列号生成器（用于消息排序检测） */
  private final AtomicLong sequenceGenerator = new AtomicLong(0);

  /** 每个 cacheName 的最后收到序列号（用于检测乱序/丢失） */
  private final Map<String, Long> lastReceivedSeq = new ConcurrentHashMap<>();

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
    this.channelPrefix = channelPrefix != null ? channelPrefix : DEFAULT_CHANNEL_PREFIX;
    this.nodeId = IdGenerator.nextIdStr();

    // 注册 Redis 消息监听器
    redisMessageListenerContainer.addMessageListener(
        (message, pattern) -> {
          String body = new String(message.getBody(), StandardCharsets.UTF_8);
          String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
          handleIncomingMessage(channel, body);
        },
        new ChannelTopic(this.channelPrefix + "*"));

    log.info("RedisCacheInvalidationBroadcaster 已初始化, nodeId={}, channelPrefix={}",
        nodeId, this.channelPrefix);
  }

  /** 失效消息字段名 */
  private static final String FIELD_SENDER = "senderNodeId";
  private static final String FIELD_KEY = "key";
  private static final String FIELD_KEYS = "keys";
  private static final String FIELD_CLEAR_ALL = "clearAll";
  private static final String FIELD_SEQ = "seq";

  /** 处理收到的广播消息 */
  private void handleIncomingMessage(String channel, String body) {
    try {
      // 解析频道名获取 cacheName
      if (!channel.startsWith(channelPrefix)) {
        log.warn("收到不属于当前前缀的频道消息: channel={}", channel);
        return;
      }
      String cacheName = channel.substring(channelPrefix.length());

      // 使用 JSON 解析消息体
      Map<String, Object> msg = YdszJson.parseMap(body);
      if (msg == null) {
        log.warn("收到空缓存失效消息: channel={}", channel);
        return;
      }

      // 类型安全的字段提取（避免未经检查的强转）
      String senderNodeId = getStringField(msg, FIELD_SENDER);
      if (senderNodeId == null) {
        log.warn("缓存失效消息缺少 senderNodeId: channel={}", channel);
        return;
      }

      // 忽略自己发出的消息
      if (nodeId.equals(senderNodeId)) {
        return;
      }

      Long seq = getLongField(msg, FIELD_SEQ);
      boolean clearAll = Boolean.TRUE.equals(msg.get(FIELD_CLEAR_ALL));

      // 序列号检测（可选：用于发现乱序/丢包）
      if (seq != null) {
        Long lastSeq = lastReceivedSeq.put(cacheName, seq);
        if (lastSeq != null && seq <= lastSeq) {
          log.warn("收到乱序缓存失效消息: cache={}, seq={}, lastSeq={}", cacheName, seq, lastSeq);
        }
      }

      // 清除本地 L1 缓存
      Cache<?, ?> localCache = localCaches.get(cacheName);
      if (localCache == null) {
        log.debug("未找到本地缓存注册: cache={}", cacheName);
      } else if (clearAll) {
        localCache.clear();
        log.debug("收到广播清除全部本地缓存: cache={}, sender={}", cacheName, senderNodeId);
      } else {
        // 批量键处理
        List<String> keys = getStringListField(msg, FIELD_KEYS);
        if (keys != null && !keys.isEmpty()) {
          for (String key : keys) {
            removeKeyFromCache(localCache, key);
          }
          log.debug("收到广播批量清除本地缓存: cache={}, keys={}, sender={}",
              cacheName, keys.size(), senderNodeId);
        } else {
          // 兼容单键模式
          String key = getStringField(msg, FIELD_KEY);
          if (key != null && !CLEAR_ALL_MARKER.equals(key)) {
            removeKeyFromCache(localCache, key);
            log.debug("收到广播清除本地缓存: cache={}, key={}, sender={}", cacheName, key, senderNodeId);
          }
        }
      }

      // 通知注册的处理器
      for (InvalidationHandler handler : handlers) {
        try {
          handler.onInvalidation(cacheName, clearAll ? null : getStringField(msg, FIELD_KEY), clearAll);
        } catch (Exception e) {
          log.warn("缓存失效处理器执行异常", e);
        }
      }
    } catch (Exception e) {
      log.warn("处理缓存失效广播消息异常: body={}", body, e);
    }
  }

  /**
   * 类型安全地提取 String 字段
   *
   * @param msg 消息 Map
   * @param field 字段名
   * @return String 值；字段不存在或类型不匹配时返回 null
   */
  private String getStringField(Map<String, Object> msg, String field) {
    Object value = msg.get(field);
    return value instanceof String ? (String) value : null;
  }

  /**
   * 类型安全地提取 Long 字段（兼容 Integer/Long/String 数值）
   *
   * @param msg 消息 Map
   * @param field 字段名
   * @return Long 值；字段不存在或类型不匹配时返回 null
   */
  private Long getLongField(Map<String, Object> msg, String field) {
    Object value = msg.get(field);
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    if (value instanceof String) {
      try {
        return Long.parseLong((String) value);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * 类型安全地提取 String 列表字段
   *
   * @param msg 消息 Map
   * @param field 字段名
   * @return String 列表；字段不存在或类型不匹配时返回 null
   */
  @SuppressWarnings("unchecked")
  private List<String> getStringListField(Map<String, Object> msg, String field) {
    Object value = msg.get(field);
    if (!(value instanceof List)) {
      return null;
    }
    List<?> rawList = (List<?>) value;
    List<String> result = new ArrayList<>(rawList.size());
    for (Object item : rawList) {
      if (item instanceof String) {
        result.add((String) item);
      } else {
        // 非 String 元素，降级为 toString
        result.add(item.toString());
      }
    }
    return result;
  }

  @Override
  public void broadcastInvalidation(String cacheName, Object key) {
    if (key == null || cacheName == null) {
      return;
    }
    long seq = sequenceGenerator.incrementAndGet();
    Map<String, Object> msg = new HashMap<>(4);
    msg.put(FIELD_SENDER, nodeId);
    msg.put(FIELD_KEY, key.toString());
    msg.put(FIELD_CLEAR_ALL, false);
    msg.put(FIELD_SEQ, seq);
    publishMessage(cacheName, msg, key);
  }

  @Override
  public void broadcastInvalidationAll(String cacheName, Collection<Object> keys) {
    if (keys == null || keys.isEmpty() || cacheName == null) {
      return;
    }
    // 批量模式：将多个键合并到一条消息中，减少 Redis 发布次数
    List<String> keyStrings = new ArrayList<>(Math.min(keys.size(), MAX_BATCH_KEYS));
    for (Object key : keys) {
      if (key != null) {
        keyStrings.add(key.toString());
      }
    }
    if (keyStrings.isEmpty()) {
      return;
    }

    // 分批发送（避免单条消息过大）
    for (int i = 0; i < keyStrings.size(); i += MAX_BATCH_KEYS) {
      List<String> batch = keyStrings.subList(i, Math.min(i + MAX_BATCH_KEYS, keyStrings.size()));
      long seq = sequenceGenerator.incrementAndGet();
      Map<String, Object> msg = new HashMap<>(4);
      msg.put(FIELD_SENDER, nodeId);
      msg.put(FIELD_KEYS, batch);
      msg.put(FIELD_CLEAR_ALL, false);
      msg.put(FIELD_SEQ, seq);
      publishMessage(cacheName, msg, batch.size() + " keys");
    }
  }

  @Override
  public void broadcastClearAll(String cacheName) {
    if (cacheName == null) {
      return;
    }
    long seq = sequenceGenerator.incrementAndGet();
    Map<String, Object> msg = new HashMap<>(4);
    msg.put(FIELD_SENDER, nodeId);
    msg.put(FIELD_KEY, CLEAR_ALL_MARKER);
    msg.put(FIELD_CLEAR_ALL, true);
    msg.put(FIELD_SEQ, seq);
    publishMessage(cacheName, msg, "clearAll");
  }

  /**
   * 发布消息到 Redis 频道（内部方法，统一错误处理）
   *
   * @param cacheName 缓存名称
   * @param msg 消息 Map
   * @param logDesc 日志描述
   */
  private void publishMessage(String cacheName, Map<String, Object> msg, Object logDesc) {
    try {
      String json = YdszJson.toJson(msg);
      String channel = channelPrefix + cacheName;
      redisTemplate.convertAndSend(channel, json);
      log.debug("广播缓存失效消息已发送: cache={}, content={}", cacheName, logDesc);
    } catch (Exception e) {
      log.warn("广播缓存失效消息失败: cache={}, content={}", cacheName, logDesc, e);
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
    if (key != null) {
      removeCaptured(cache, key);
    }
  }

  /**
   * 泛型捕获辅助方法
   *
   * <p>利用编译器的通配类型捕获机制，将 {@code Cache<?, ?>} 捕获为 {@code Cache<KK, VV>}，
   * 然后在方法签名内安全地接受 Object 参数。
   */
  private <KK, VV> void removeCaptured(Cache<KK, VV> cache, Object key) {
    cache.remove((KK) key);
  }
}
