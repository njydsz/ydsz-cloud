package com.njydsz.message.server.health;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * Redis 健康状态持有者。
 *
 * <p>定期检测 Redis 连通性，供消费者在幂等判断时决定是否启用 DB 兜底查询。
 *
 * <p>当 Redis 健康时，消费者跳过 DB 二级幂等检查，避免每次消费都额外查询数据库。 仅在 Redis 故障恢复窗口期内启用 DB 幂等兜底。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
// CHECKSTYLE.OFF: RegexpSinglelineJava - @ConditionalOnClass name 属性为 Spring 条件类全名（字符串字面量）
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
// CHECKSTYLE.ON: RegexpSinglelineJava
public class RedisHealthStatus {
  /** 探针 TTL（秒） */
  private static final int PROBE_TTL_SECONDS = 30;


  private final RedisStringOps redisStringOps;

  /** Redis 健康标志：true 表示 Redis 正常运行 */
  private final AtomicBoolean redisHealthy = new AtomicBoolean(true);

  /** 探测 key 前缀，用于避免与业务 key 冲突 */
  private static final String PROBE_KEY = "__ydsz_message_redis_health__";

  @PostConstruct
  public void init() {
    checkRedisHealth();
  }

  /**
   * 定期检查 Redis 连通性（每 10 秒一次）。
   *
   * <p>使用简单的 SET + GET + DEL 组合验证 Redis 读写正常。
   */
  @Scheduled(fixedDelayString = "${ydsz.message.redis-health-check-interval-ms:10000}")
  public void checkRedisHealth() {
    try {
      redisStringOps.set(PROBE_KEY, "ok", PROBE_TTL_SECONDS);
      String value = redisStringOps.get(PROBE_KEY, String.class);
      boolean healthy = "ok".equals(value);
      boolean wasHealthy = redisHealthy.getAndSet(healthy);
      if (!wasHealthy && healthy) {
        log.info("[RedisHealthStatus] Redis 已恢复");
      } else if (wasHealthy && !healthy) {
        log.warn("[RedisHealthStatus] Redis 异常，启用 DB 幂等兜底");
      }
    } catch (Exception e) {
      boolean wasHealthy = redisHealthy.getAndSet(false);
      if (wasHealthy) {
        log.warn("[RedisHealthStatus] Redis 异常: {}, 启用 DB 幂等兜底", e.getMessage());
      }
    }
  }

  /**
   * 判断 Redis 是否健康。
   *
   * @return true 表示 Redis 正常运行，可跳过 DB 幂等兜底查询
   */
  public boolean isRedisHealthy() {
    return redisHealthy.get();
  }
}
