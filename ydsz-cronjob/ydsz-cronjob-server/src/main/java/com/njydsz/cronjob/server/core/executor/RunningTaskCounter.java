package com.njydsz.cronjob.server.core.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 运行中任务数计数器（Redis 维护，替代 Gauge 的 DB 查询）。
 *
 * <p>通过 Redis 原子 INCR/DECR 维护集群级运行中任务数：
 *
 * <ul>
 *   <li>任务开始执行时 {@link #increment()}
 *   <li>任务执行完成时 {@link #decrement()}
 *   <li>Gauge 回调通过 {@link #getCount()} 直接读取，消除 DB 查询
 * </ul>
 *
 * <p>与 {@link GlobalConcurrencyController} 的区别：后者是配额限制（有上限），本组件是纯计数（无上限，仅用于监控）。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RunningTaskCounter {

  /** Redis Key：运行中任务数 */
  public static final String RUNNING_COUNT_KEY = "ydsz:job:running:count";

  private static final long INITIAL_TTL_SECONDS = 3600;

  private final RedisStringOps redisStringOps;

  /**
   * 递增运行中任务数（任务开始执行时调用）。
   *
   * @return 递增后的值
   */
  public long increment() {
    try {
      Long count = redisStringOps.incr(RUNNING_COUNT_KEY, 1);
      if (count != null && count == 1) {
        // 首次创建时设置 TTL（防止异常退出后永久残留）
        redisStringOps.expire(RUNNING_COUNT_KEY, INITIAL_TTL_SECONDS);
      }
      return count != null ? count : 0;
    } catch (Exception e) {
      log.warn("[RunningTaskCounter] 递增失败: reason={}", e.getMessage());
      return 0;
    }
  }

  /**
   * 递减运行中任务数（任务执行完成时调用）。
   *
   * @return 递减后的值
   */
  public long decrement() {
    try {
      long current = redisStringOps.decr(RUNNING_COUNT_KEY, 1);
      if (current < 0) {
        redisStringOps.set(RUNNING_COUNT_KEY, "0");
        log.warn("[RunningTaskCounter] 计数器为负, 已修正为 0");
        return 0;
      }
      return current;
    } catch (Exception e) {
      log.warn("[RunningTaskCounter] 递减失败: reason={}", e.getMessage());
      return 0;
    }
  }

  /**
   * 获取当前运行中任务数（供 Gauge 回调使用）。
   *
   * @return 运行中任务数；Redis 不可用时返回 0
   */
  public long getCount() {
    try {
      String value = redisStringOps.get(RUNNING_COUNT_KEY);
      if (value == null) {
        return 0;
      }
      return Long.parseLong(value);
    } catch (Exception e) {
      log.debug("[RunningTaskCounter] 读取失败: reason={}", e.getMessage());
      return 0;
    }
  }
}
