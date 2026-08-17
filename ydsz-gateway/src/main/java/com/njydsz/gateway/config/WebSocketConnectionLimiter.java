package com.njydsz.gateway.config;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * WebSocket 连接数限制器（P2-F4）。
 *
 * <p>基于 Redis 原子操作维护全局 WebSocket 连接计数器，防止单用户 / 单 IP 建立过多 WebSocket 连接导致网关资源耗尽：
 *
 * <ul>
 *   <li>连接建立时递增计数器，超过阈值则拒绝握手</li>
 *   <li>连接关闭时递减计数器（由下游服务或心跳超时触发）</li>
 *   <li>计数器设置 TTL 作为兜底清理，避免异常断开导致计数永久占用</li>
 * </ul>
 *
 * <h3>Redis 键设计</h3>
 *
 * <pre>
 *   ydsz:ws:connections:{userId}  → 连接数计数器（TTL 自动续期）
 *   ydsz:ws:connections:ip:{ip}   → IP 维度连接数计数器
 * </pre>
 *
 * <h3>配置项</h3>
 *
 * <pre>
 * ydsz.gateway.websocket.max-connections-per-user: 5
 * ydsz.gateway.websocket.max-connections-per-ip: 20
 * ydsz.gateway.websocket.counter-ttl-seconds: 3600
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnClass(ReactiveStringRedisTemplate.class)
public class WebSocketConnectionLimiter {

  /** 用户维度连接数 Redis 键前缀 */
  private static final String KEY_PREFIX_USER = "ydsz:ws:connections:";

  /** IP 维度连接数 Redis 键前缀 */
  private static final String KEY_PREFIX_IP = "ydsz:ws:connections:ip:";

  /** Lua 脚本：检查并递增计数器（原子操作） */
  private static final String INCR_WITH_LIMIT_SCRIPT = """
      local current = redis.call('GET', KEYS[1])
      if current and tonumber(current) >= tonumber(ARGV[1]) then
        return -1
      end
      local new = redis.call('INCR', KEYS[1])
      if new == 1 then
        redis.call('EXPIRE', KEYS[1], ARGV[2])
      end
      return new
      """;

  private final ReactiveStringRedisTemplate redisTemplate;

  /** 单用户最大 WebSocket 连接数 */
  private final int maxConnectionsPerUser;

  /** 单 IP 最大 WebSocket 连接数 */
  private final int maxConnectionsPerIp;

  /** 计数器 TTL（秒） */
  private final long counterTtlSeconds;

  private final RedisScript<Long> incrScript;

  /**
   * 构造 WebSocket 连接限制器。
   *
   * @param redisTemplate Redis 响应式模板
   * @param maxConnectionsPerUser 单用户最大连接数
   * @param maxConnectionsPerIp 单 IP 最大连接数
   * @param counterTtlSeconds 计数器 TTL（秒）
   */
  public WebSocketConnectionLimiter(
      ReactiveStringRedisTemplate redisTemplate,
      @org.springframework.beans.factory.annotation.Value("${ydsz.gateway.websocket.max-connections-per-user:5}")
          int maxConnectionsPerUser,
      @org.springframework.beans.factory.annotation.Value("${ydsz.gateway.websocket.max-connections-per-ip:20}")
          int maxConnectionsPerIp,
      @org.springframework.beans.factory.annotation.Value("${ydsz.gateway.websocket.counter-ttl-seconds:3600}")
          long counterTtlSeconds) {
    this.redisTemplate = redisTemplate;
    this.maxConnectionsPerUser = maxConnectionsPerUser;
    this.maxConnectionsPerIp = maxConnectionsPerIp;
    this.counterTtlSeconds = counterTtlSeconds;
    this.incrScript = new DefaultRedisScript<>(INCR_WITH_LIMIT_SCRIPT, Long.class);
    log.info(
        "[WsConnectionLimiter] 初始化: maxPerUser={}, maxPerIp={}, ttl={}s",
        maxConnectionsPerUser, maxConnectionsPerIp, counterTtlSeconds);
  }

  /**
   * 尝试获取连接配额（用户维度 + IP 维度双重检查）。
   *
   * <p>先检查 IP 维度，再检查用户维度。任一维度超限则拒绝并返回 false。
   *
   * @param userId 用户 ID（未登录用户使用 IP 替代）
   * @param clientIp 客户端 IP
   * @return true=获取成功（已递增计数），false=超限被拒绝
   */
  public Mono<Boolean> tryAcquire(String userId, String clientIp) {
    return acquireIpQuota(clientIp)
        .flatMap(
            ipAllowed -> {
              if (!ipAllowed) {
                return Mono.just(false);
              }
              return acquireUserQuota(userId, clientIp);
            });
  }

  /**
   * 释放连接配额（用户维度 + IP 维度同时递减）。
   *
   * <p>在 WebSocket 连接关闭或心跳超时后调用，恢复可用配额。
   *
   * @param userId 用户 ID
   * @param clientIp 客户端 IP
   */
  public Mono<Void> release(String userId, String clientIp) {
    String userKey = buildUserKey(userId, clientIp);
    String ipKey = buildIpKey(clientIp);
    return redisTemplate
        .opsForValue()
        .decrement(userKey)
        .flatMap(u -> redisTemplate.opsForValue().decrement(ipKey))
        .onErrorResume(
            e -> {
              log.warn("[WsConnectionLimiter] 释放配额异常: userId={}, ip={}", userId, clientIp);
              return Mono.empty();
            })
        .then();
  }

  /**
   * 获取当前用户连接数。
   *
   * @param userId 用户 ID
   * @param clientIp 客户端 IP
   * @return 当前连接数（Redis 异常时返回 0）
   */
  public Mono<Long> getCurrentConnections(String userId, String clientIp) {
    return redisTemplate
        .opsForValue()
        .get(buildUserKey(userId, clientIp))
        .map(Long::parseLong)
        .defaultIfEmpty(0L)
        .onErrorReturn(0L);
  }

  private Mono<Boolean> acquireIpQuota(String clientIp) {
    if (maxConnectionsPerIp <= 0) {
      return Mono.just(true);
    }
    String key = buildIpKey(clientIp);
    return redisTemplate
        .execute(incrScript, java.util.List.of(key), String.valueOf(maxConnectionsPerIp), String.valueOf(counterTtlSeconds))
        .next()
        .map(result -> {
          if (result != null && result == -1L) {
            log.warn("[WsConnectionLimiter] IP 连接数超限: ip={}, max={}", clientIp, maxConnectionsPerIp);
            return false;
          }
          return true;
        })
        .defaultIfEmpty(true)
        .onErrorResume(
            e -> {
              log.warn("[WsConnectionLimiter] IP 配额检查异常，降级放行: ip={}", clientIp);
              return Mono.just(true);
            });
  }

  private Mono<Boolean> acquireUserQuota(String userId, String clientIp) {
    if (maxConnectionsPerUser <= 0) {
      return Mono.just(true);
    }
    String key = buildUserKey(userId, clientIp);
    return redisTemplate
        .execute(incrScript, java.util.List.of(key), String.valueOf(maxConnectionsPerUser), String.valueOf(counterTtlSeconds))
        .next()
        .map(
            result -> {
              if (result != null && result == -1L) {
                // 用户维度超限，回滚 IP 维度计数
                redisTemplate.opsForValue().decrement(buildIpKey(clientIp)).subscribe();
                log.warn("[WsConnectionLimiter] 用户连接数超限: userId={}, max={}", userId, maxConnectionsPerUser);
                return false;
              }
              return true;
            })
        .defaultIfEmpty(true)
        .onErrorResume(
            e -> {
              log.warn("[WsConnectionLimiter] 用户配额检查异常，降级放行: userId={}", userId);
              return Mono.just(true);
            });
  }

  private String buildUserKey(String userId, String clientIp) {
    String id = (userId != null && !userId.isBlank()) ? userId : "anonymous-" + clientIp;
    return KEY_PREFIX_USER + id;
  }

  private String buildIpKey(String clientIp) {
    return KEY_PREFIX_IP + clientIp;
  }
}
