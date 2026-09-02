package com.njydsz.common.socket.offline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.resilience.WebSocketCircuitBreaker;

/**
 * 离线消息存储 Redis 默认实现。
 *
 * <p>使用 Redis List 缓存离线消息（{@code ydsz:ws:offline:{userId}}）， FIFO 顺序保留最近 {@link
 * WebSocketProperties.Offline#getMaxCache()} 条， TTL 由 {@link WebSocketProperties.Offline#getTtl()}
 * 控制。
 *
 * <p>当 Redis 缓存超过 {@code maxCache} 时，自动丢弃最旧消息（LPUSH + TRIM）， 防止 Redis 内存被离线消息打满。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class RedisOfflineMessageStore implements OfflineMessageStore {

  private final StringRedisTemplate redisTemplate;
  private final WebSocketProperties properties;
  private final WebSocketCircuitBreaker circuitBreaker;

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
    redisTemplate.opsForList().leftPush(key, json);
    redisTemplate.opsForList().trim(key, 0, properties.getOffline().getMaxCache() - 1);
    redisTemplate.expire(key, properties.getOffline().getTtl());

    log.debug("[WS-Offline] 缓存离线消息: userId={}, type={}", userId, type);
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
