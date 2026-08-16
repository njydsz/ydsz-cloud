package com.njydsz.workflow.server.engine;

import com.njydsz.common.redis.service.RedisRateLimiter;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 催办限流器
 *
 * <p>P0-1c 架构优化：委托 {@link RedisRateLimiter} 公共限流能力， 消除手写 Lua 脚本（SET NX EX）和 StringRedisTemplate
 * 直接操作。
 *
 * <p>同一催办人对同一任务/实例在冷却窗口内只允许一次催办，防止恶意刷催办。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>key 维度：催办人 userId + 任务/实例 id，避免不同催办人之间互锁
 *   <li>窗口可配置（默认 30 分钟）
 *   <li>限流失败时通过 {@link com.njydsz.common.exception.custom.SysException} + {@code RATE_LIMIT}
 *       错误码抛回前端
 *   <li>RedisRateLimiter 不可用时降级放行（ObjectProvider 可选注入）
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowUrgeLimiter {

  /** 默认冷却窗口 30 分钟（与对标用友/钉钉审批的 30 分钟冷却一致） */
  public static final long DEFAULT_COOLDOWN_SECONDS = 30 * 60L;

  private final RedisRateLimiter rateLimiter;

  public FlowUrgeLimiter(ObjectProvider<RedisRateLimiter> rateLimiterProvider) {
    this.rateLimiter = rateLimiterProvider.getIfAvailable();
    if (this.rateLimiter == null) {
      log.warn("[FlowUrgeLimiter] RedisRateLimiter 不可用，催办限流将降级放行");
    }
  }

  /**
   * 校验催办是否在冷却窗口内
   *
   * @param userId 催办人 ID
   * @param targetId 目标（任务 ID 或实例 ID）
   * @param targetType 目标类型（TASK/INSTANCE）
   * @return true=可催办；false=冷却中
   */
  public boolean tryAcquire(String userId, Long targetId, String targetType) {
    return tryAcquire(userId, targetId, targetType, DEFAULT_COOLDOWN_SECONDS);
  }

  /**
   * 校验催办是否在冷却窗口内（自定义窗口）
   *
   * @param userId 催办人 ID
   * @param targetId 目标 ID
   * @param targetType 目标类型
   * @param cooldownSeconds 冷却秒数
   * @return true=可催办；false=冷却中
   */
  public boolean tryAcquire(String userId, Long targetId, String targetType, long cooldownSeconds) {
    if (userId == null || targetId == null) {
      return true; // 缺参数不阻断主流程
    }
    if (rateLimiter == null) {
      return true; // RedisRateLimiter 不可用时降级放行
    }
    String key = buildKey(userId, targetId, targetType);
    try {
      boolean acquired =
          rateLimiter.tryAcquireFixedWindow(key, 1, Duration.ofSeconds(cooldownSeconds));
      if (!acquired) {
        log.info(
            "[FlowUrgeLimiter] 催办冷却中 userId={} targetId={} type={} key={}",
            userId,
            targetId,
            targetType,
            key);
      }
      return acquired;
    } catch (Exception e) {
      // Redis 不可用时降级放行，避免拖垮催办主流程
      log.warn("[FlowUrgeLimiter] Redis 不可用，降级放行: {}", e.getMessage());
      return true;
    }
  }

  /**
   * 主动释放催办冷却（管理员强制操作后允许立即再次催办）
   *
   * @param userId 催办人
   * @param targetId 目标
   * @param targetType 类型
   */
  public void release(String userId, Long targetId, String targetType) {
    if (userId == null || targetId == null || rateLimiter == null) {
      return;
    }
    try {
      rateLimiter.reset(buildKey(userId, targetId, targetType));
    } catch (Exception e) {
      log.warn("[FlowUrgeLimiter] 释放冷却失败: {}", e.getMessage());
    }
  }

  /**
   * 批量查询指定催办人对多个目标的冷却剩余时间
   *
   * @param userId 催办人
   * @param targetIds 目标 ID 列表
   * @param type 目标类型
   * @return 剩余秒数列表（0=可催办，>0=冷却中）
   */
  public List<Long> getCooldownSeconds(String userId, List<Long> targetIds, String type) {
    if (userId == null || targetIds == null || targetIds.isEmpty() || rateLimiter == null) {
      return Collections.emptyList();
    }
    return targetIds.stream()
        .map(
            targetId -> {
              try {
                long ttl = rateLimiter.getRemainingSeconds(buildKey(userId, targetId, type));
                return Math.max(0, ttl);
              } catch (Exception e) {
                log.warn(
                    "[FlowUrgeLimiter] 获取催办剩余 TTL 失败 targetId={}: {}", targetId, e.getMessage());
                return 0L;
              }
            })
        .toList();
  }

  private static String buildKey(String userId, Long targetId, String targetType) {
    return "flow:urge:" + targetType + ":" + targetId + ":by:" + userId;
  }
}
