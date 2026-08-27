package com.njydsz.workflow.server.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 任务计数 Redis 缓存服务。
 *
 * <p>P1: 使用 Redis INCR/DECR 实时维护待办任务数，避免每次查询都打 DB COUNT。
 * 适用于「我的待办」角标、首页待办数等高频查询场景。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>key 格式：{@code flow:task:count:{userId}}（个人待办数）
 *   <li>key 格式：{@code flow:task:count:total}（全局待办数）
 *   <li>维护方式：任务创建时 INCR、任务完成/取消时 DECR
 *   <li>TTL：永久（通过 DECR 归零后自动删除）
 * </ul>
 *
 * <p><b>数据一致性：</b>
 *
 * <ul>
 *   <li>最终一致性：Redis 计数与 DB 存在短暂不一致（通常 < 1s）
 *   <li>兜底校验：定时任务每小时全量校对一次（从 DB 重新 COUNT 并覆盖 Redis）
 *   <li>启动预热：应用启动时从 DB 加载初始值
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowTaskCountCacheService {

  /** 个人待办计数 key 前缀 */
  private static final String KEY_USER_PENDING = "flow:task:count:";

  /** 全局待办计数 key */
  private static final String KEY_TOTAL_PENDING = "flow:task:count:total";

  private final StringRedisTemplate redisTemplate;

  public FlowTaskCountCacheService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * 递增指定用户的待办计数。
   *
   * <p>在任务创建时调用。
   *
   * @param userId 用户 ID
   * @return 递增后的计数值
   */
  public long incrementPending(String userId) {
    if (userId == null || userId.isBlank()) {
      return 0;
    }
    try {
      Long count = redisTemplate.opsForValue().increment(KEY_USER_PENDING + userId);
      redisTemplate.opsForValue().increment(KEY_TOTAL_PENDING);
      return count != null ? count : 0;
    } catch (Exception e) {
      log.warn("[FlowTaskCountCache] INCR 失败 userId={}: {}", userId, e.getMessage());
      return 0;
    }
  }

  /**
   * 递减指定用户的待办计数。
   *
   * <p>在任务完成/取消/委派时调用。
   *
   * @param userId 用户 ID
   * @return 递减后的计数值
   */
  public long decrementPending(String userId) {
    if (userId == null || userId.isBlank()) {
      return 0;
    }
    try {
      Long count = redisTemplate.opsForValue().decrement(KEY_USER_PENDING + userId);
      redisTemplate.opsForValue().decrement(KEY_TOTAL_PENDING);
      // 归零后删除 key，避免长期占用内存
      if (count != null && count <= 0) {
        redisTemplate.delete(KEY_USER_PENDING + userId);
      }
      return count != null ? Math.max(0, count) : 0;
    } catch (Exception e) {
      log.warn("[FlowTaskCountCache] DECR 失败 userId={}: {}", userId, e.getMessage());
      return 0;
    }
  }

  /**
   * 获取指定用户的待办计数。
   *
   * @param userId 用户 ID
   * @return 待办计数值，无缓存返回 0
   */
  public long getPendingCount(String userId) {
    if (userId == null || userId.isBlank()) {
      return 0;
    }
    try {
      Long count = redisTemplate.opsForValue().get(KEY_USER_PENDING + userId) != null
          ? Long.parseLong(redisTemplate.opsForValue().get(KEY_USER_PENDING + userId))
          : 0L;
      return count != null ? count : 0;
    } catch (Exception e) {
      log.warn("[FlowTaskCountCache] GET 失败 userId={}: {}", userId, e.getMessage());
      return 0;
    }
  }

  /**
   * 获取全局待办计数。
   *
   * @return 全局待办计数值
   */
  public long getTotalPendingCount() {
    try {
      String value = redisTemplate.opsForValue().get(KEY_TOTAL_PENDING);
      return value != null ? Long.parseLong(value) : 0;
    } catch (Exception e) {
      log.warn("[FlowTaskCountCache] GET total 失败: {}", e.getMessage());
      return 0;
    }
  }

  /**
   * 设置指定用户的待办计数（用于定时校对）。
   *
   * @param userId 用户 ID
   * @param count 计数值
   */
  public void setPendingCount(String userId, long count) {
    if (userId == null || userId.isBlank()) {
      return;
    }
    try {
      if (count <= 0) {
        redisTemplate.delete(KEY_USER_PENDING + userId);
      } else {
        redisTemplate.opsForValue().set(KEY_USER_PENDING + userId, String.valueOf(count));
      }
    } catch (Exception e) {
      log.warn("[FlowTaskCountCache] SET 失败 userId={}: {}", userId, e.getMessage());
    }
  }

  /**
   * 清除指定用户的待办计数。
   *
   * @param userId 用户 ID
   */
  public void evictPendingCount(String userId) {
    if (userId == null || userId.isBlank()) {
      return;
    }
    try {
      redisTemplate.delete(KEY_USER_PENDING + userId);
    } catch (Exception e) {
      log.warn("[FlowTaskCountCache] DELETE 失败 userId={}: {}", userId, e.getMessage());
    }
  }
}
