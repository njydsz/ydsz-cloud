package com.njydsz.workflow.server.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.support.CacheKeyBuilder;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 任务计数 Redis 缓存服务。
 *
 * <p>P1: 使用 Redis INCR/DECR 实时维护待办任务数，避免每次查询都打 DB COUNT。
 * 适用于「我的待办」角标、首页待办数等高频查询场景。
 *
 * <p><b>缓存策略（P2-3 租户隔离增强）：</b>
 *
 * <ul>
 *   <li>key 格式：{@code ydsz:{tenantId}:workflow:task:count:{userId}}（个人待办数）</li>
 *   <li>key 格式：{@code ydsz:workflow:task:count:total}（全局待办数，不区分租户）</li>
 *   <li>维护方式：任务创建时 INCR、任务完成/取消时 DECR</li>
 *   <li>TTL：永久（通过 DECR 归零后自动删除）</li>
 * </ul>
 *
 * <p><b>数据一致性：</b>
 *
 * <ul>
 *   <li>最终一致性：Redis 计数与 DB 存在短暂不一致（通常 &lt; 1s）</li>
 *   <li>兜底校验：定时任务每小时全量校对一次（从 DB 重新 COUNT 并覆盖 Redis）</li>
 *   <li>启动预热：应用启动时从 DB 加载初始值</li>
 * </ul>
 *
 * <p>使用 ydsz-common-redis 的 RedisStringOps 操作 Redis，
 * 键通过 {@link CacheKeyBuilder} 构建，自动携带租户前缀。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowTaskCountCacheService {

  /** 模块标识 */
  private static final String MODULE = "workflow";

  /** 全局待办计数 key（全局共享，不区分租户） */
  private static final String KEY_TOTAL_PENDING = "ydsz:workflow:task:count:total";

  /** Redis String 模板（键已通过 CacheKeyBuilder 携带租户前缀，直接使用即可） */
  private final RedisStringOps redisStringOps;

  /**
   * 构建指定用户的个人待办计数 key。
   *
   * @param userId 用户 ID
   * @return 租户隔离的 key，如 {@code ydsz:acme:workflow:task:count:U10086}
   */
  private String buildUserKey(String userId) {
    return CacheKeyBuilder.build(MODULE, "task:count", userId);
  }

  /**
   * 递增指定用户的待办计数。
   *
   * <p>在任务创建时调用。使用 {@code opsForValue().increment()} 保证原子性。
   *
   * @param userId 用户 ID
   * @return 递增后的计数值
   */
  public long incrementPending(String userId) {
    if (userId == null || userId.isBlank()) {
      return 0;
    }
    try {
      String userKey = buildUserKey(userId);
      long count = redisStringOps.incr(userKey, 1);
      redisStringOps.incr(KEY_TOTAL_PENDING, 1);
      return count;
    } catch (Exception e) {
      log.warn("[FlowTaskCountCache] INCR 失败 userId={}: {}", userId, e.getMessage());
      return 0;
    }
  }

  /**
   * 递减指定用户的待办计数。
   *
   * <p>在任务完成/取消/委派时调用。使用 {@code opsForValue().decrement()} 保证原子性，
   * 归零后删除 key，避免长期占用内存。
   *
   * @param userId 用户 ID
   * @return 递减后的计数值
   */
  public long decrementPending(String userId) {
    if (userId == null || userId.isBlank()) {
      return 0;
    }
    try {
      String userKey = buildUserKey(userId);
      long count = redisStringOps.decr(userKey, 1);
      redisStringOps.decr(KEY_TOTAL_PENDING, 1);
      // 归零后删除 key，避免长期占用内存
      if (count <= 0) {
        redisStringOps.del(userKey);
      }
      return Math.max(0, count);
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
      String value = redisStringOps.get(buildUserKey(userId), String.class);
      return value != null ? Long.parseLong(value) : 0;
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
      String value = redisStringOps.get(KEY_TOTAL_PENDING, String.class);
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
        redisStringOps.del(buildUserKey(userId));
      } else {
        redisStringOps.set(buildUserKey(userId), String.valueOf(count));
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
      redisStringOps.del(buildUserKey(userId));
    } catch (Exception e) {
      log.warn("[FlowTaskCountCache] DELETE 失败 userId={}: {}", userId, e.getMessage());
    }
  }
}
