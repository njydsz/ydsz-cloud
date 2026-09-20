package com.njydsz.common.socket.ratelimit;

import java.time.Duration;
import java.util.Collections;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.redis.constant.RedisScriptConstants;

/**
 * WebSocket 握手 IP 速率限制器（SEC-001）。
 *
 * <p>防止攻击者通过高频 WebSocket 升级握手（HTTP Upgrade）在短时间内耗尽服务端 TCP backlog、
 * 线程池与内存。基于 Redis + Lua 滑动窗口，独立于消息限流（{@link WebSocketRateLimiter}）计数。
 *
 * <p>限流 key 格式：{@code ydsz:ws:handshake:ip:{ip}}（TTL 60 秒）。
 *
 * <p>使用方式：在 {@code WebSocketAuthInterceptor.beforeHandshake()} 入口处调用
 * {@link #allowHandshake(String)}，被限流时直接拒绝握手并返回 429。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
public class WebSocketHandshakeRateLimiter {

  /** 握手限流 Redis key 前缀 */
  private static final String HANDSHAKE_IP_PREFIX = "ydsz:ws:handshake:ip:";

  /** Lua 脚本引用（INCR + 首次创建时 EXPIRE） */
  private static final String INCR_EXPIRE_SCRIPT = RedisScriptConstants.INCR_WITH_EXPIRE_LUA;

  /** 滑动窗口：1 分钟 */
  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final StringRedisTemplate redisTemplate;
  private final int maxHandshakesPerMinute;

  /**
   * 构造握手速率限制器。
   *
   * @param redisTemplate Redis 模板，为 null 时降级为允许全部（不拦截）
   * @param maxHandshakesPerMinute 没分钟每 IP 最大握手次数，默认 20
   */
  public WebSocketHandshakeRateLimiter(StringRedisTemplate redisTemplate, int maxHandshakesPerMinute) {
    this.redisTemplate = redisTemplate;
    this.maxHandshakesPerMinute = Math.max(maxHandshakesPerMinute, 1);
  }

  /**
   * 检查是否允许该 IP 发起握手。
   *
   * @param clientIp 客户端 IP
   * @return true 表示允许，false 表示被限流（应返回 HTTP 429）
   */
  public boolean allowHandshake(String clientIp) {
    if (redisTemplate == null || clientIp == null || clientIp.isEmpty()) {
      return true;
    }
    String key = HANDSHAKE_IP_PREFIX + clientIp;
    try {
      DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_EXPIRE_SCRIPT, Long.class);
      Long count =
          redisTemplate.execute(
              script, Collections.singletonList(key), String.valueOf(WINDOW.getSeconds()));
      boolean allowed = count == null || count <= maxHandshakesPerMinute;
      if (!allowed) {
        log.warn(
            "[WS-HandshakeLimit] 握手频率超限: ip={}, count={}, limit={}",
            clientIp,
            count,
            maxHandshakesPerMinute);
      }
      return allowed;
    } catch (Exception e) {
      log.warn("[WS-HandshakeLimit] Redis 限流查询异常, 降级放行: ip={}, err={}", clientIp, e.getMessage());
      return true;
    }
  }
}
