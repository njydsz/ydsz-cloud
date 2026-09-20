package com.njydsz.common.socket.offline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.resilience.WebSocketCircuitBreaker;

/**
 * 离线消息存储 Redis 默认实现。
 *
 * <p>使用 Redis List 缓存离线消息（{@code ydsz:ws:offline:{userId}} 或租户隔离 key {@code
 * ydsz:ws:offline:{tenantId}:{userId}}），FIFO 顺序保留最近 {@link
 * WebSocketProperties.Offline#getMaxCache()} 条，TTL 由 {@link WebSocketProperties.Offline#getTtl()}
 * 控制。
 *
 * <p>离线消息的原子入队（LPUSH + LTRIM + EXPIRE）使用 {@code scripts/ws-offline-cache.lua}
 * 单脚本原子化，避免多次 RTT（PERF-003）。
 *
 * <p>Redis key 格式通过 {@link WebSocketConstants#WS_OFFLINE_KEY_PREFIX} 构成，租户隔离时
 * 由调用方基于 {@code WebSocketContext} 传入限定 key。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RedisOfflineMessageStore implements OfflineMessageStore {

  /** Lua 脚本引用（延迟初始化，仅 Redis (template) 可用时加载） */
  private final DefaultRedisScript<Long> offlineCacheScript;

  private final StringRedisTemplate redisTemplate;
  private final WebSocketProperties properties;
  private final WebSocketCircuitBreaker circuitBreaker;

  /**
   * 构造 Redis 离线消息存储。
   *
   * @param redisTemplate Redis 模板
   * @param properties WebSocket 配置
   * @param circuitBreaker 熔断器
   */
  public RedisOfflineMessageStore(
      StringRedisTemplate redisTemplate,
      WebSocketProperties properties,
      WebSocketCircuitBreaker circuitBreaker) {
    this.redisTemplate = redisTemplate;
    this.properties = properties;
    this.circuitBreaker = circuitBreaker;
    this.offlineCacheScript = loadOfflineCacheScript();
  }

  /**
   * 加载离线消息 Lua 脚本。
   *
   * @return Redis 脚本对象，资源不存在时返回 null
   */
  private DefaultRedisScript<Long> loadOfflineCacheScript() {
    try {
      DefaultRedisScript<Long> script = new DefaultRedisScript<>();
      script.setResultType(Long.class);
      script.setScriptSource(
          new ResourceScriptSource(new ClassPathResource("scripts/ws-offline-cache.lua")));
      return script;
    } catch (Exception e) {
      log.warn("[WS-Offline] Lua 脚本加载失败，降级为逐命令模式: err={}", e.getMessage());
      return null;
    }
  }

  @Override
  public void cacheOffline(String userId, String type, Object payload) {
    if (userId == null) {
      return;
    }
    circuitBreaker.execute(
        () -> doCacheOffline(userId, type, payload),
        () -> log.warn("[WS-Offline] 熔断中, 跳过缓存: userId={}", userId));
  }

  private void doCacheOffline(String userId, String type, Object payload) {
    String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
    Map<String, Object> envelope =
        Map.of(
            "type",
            type == null ? "UNKNOWN" : type,
            "payload",
            payload,
            "timestamp",
            System.currentTimeMillis());
    String json = YdszJson.toJson(envelope);
    Integer maxCache = properties.getOffline().getMaxCache();
    long ttlSeconds = properties.getOffline().getTtl().getSeconds();

    // PERF-003: 优先使用 Lua 脚本原子入队（LPUSH + LTRIM + EXPIRE in 1 RTT）
    if (offlineCacheScript != null) {
      try {
        redisTemplate.execute(
            offlineCacheScript,
            List.of(key),
            json,
            String.valueOf(maxCache),
            String.valueOf(ttlSeconds));
        log.debug("[WS-Offline] 缓存离线消息(Lua 原子): userId={}, type={}", userId, type);
        return;
      } catch (Exception e) {
        log.warn("[WS-Offline] Lua 脚本执行失败，降级为逐命令: userId={}, err={}", userId, e.getMessage());
      }
    }

    // 降级：逐命令执行（3 RTT）
    redisTemplate.opsForList().leftPush(key, json);
    redisTemplate.opsForList().trim(key, 0, maxCache - 1);
    redisTemplate.expire(key, properties.getOffline().getTtl());
    log.debug("[WS-Offline] 缓存离线消息(降级): userId={}, type={}", userId, type);
  }

  @Override
  public List<String> drainOffline(String userId) {
    if (userId == null) {
      return List.of();
    }
    return circuitBreaker.execute(() -> doDrainOffline(userId), () -> List.of());
  }

  private List<String> doDrainOffline(String userId) {
    String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
    List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    redisTemplate.delete(key);
    // LPUSH 入队导致顺序反转，反转为时间正序（最旧在前）
    List<String> result = new ArrayList<>(raw);
    Collections.reverse(result);
    log.info("[WS-Offline] 拉取离线消息: userId={}, total={}", userId, result.size());
    return result;
  }

  @Override
  public long countOffline(String userId) {
    if (userId == null) {
      return 0L;
    }
    return circuitBreaker.execute(
        () -> {
          String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
          Long size = redisTemplate.opsForList().size(key);
          return size == null ? 0L : size;
        },
        () -> 0L);
  }
}
