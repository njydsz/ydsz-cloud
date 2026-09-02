package com.njydsz.cronjob.server.core.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.core.redis.CronjobRedisOps;

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
 * <p>P0-8: 使用 {@link CronjobRedisOps} 收敛 Redis 操作，统一 key 前缀与异常降级。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RunningTaskCounter {

  /** Redis Key segment：运行中任务数（完整 key = ydzs:job:running:count） */
  private static final String RUNNING_COUNT_SEGMENT = "running:count";

  /** 计数器 TTL（秒）：防异常退出后永久残留，由 {@link #renewTtl()} 定期续期保证长期运行不归零 */
  private static final long INITIAL_TTL_SECONDS = 3600;

  /** 续期间隔（毫秒），默认 10 分钟；TTL 为 1 小时，续期周期远小于 TTL，长期运行计数稳定 */
  @Value("${ydsz.cronjob.executor.running-counter-renew-ms:600000}")
  private long renewIntervalMs;

  private final CronjobRedisOps cronjobRedisOps;

  /**
   * 定时续期计数器 TTL（默认每 10 分钟）。
   *
   * <p>解决长期运行场景下计数器因 TTL 到期突然归零的问题： 集群持续运行超过 1 小时不再重启时，首次 INCR 设置的 TTL 到期会让 Gauge 归零。
   * 通过定期 EXPIRE 续期，只要集群活跃（有任务执行触发 INCR），计数持续有效；
   * 异常退出（无任何续期）时 TTL 到期自动清理，防止永久残留。EXPIRE 幂等，多节点同时续期无害。
   */
  @Scheduled(fixedDelayString = "${ydsz.cronjob.executor.running-counter-renew-ms:600000}")
  public void renewTtl() {
    try {
      cronjobRedisOps.expire(RUNNING_COUNT_SEGMENT, INITIAL_TTL_SECONDS);
    } catch (Exception e) {
      // key 不存在时 EXPIRE 返回 0 属正常（异常退出后已清理），仅记录 debug
      log.debug("[RunningTaskCounter] 续期失败(可能 key 不存在): reason={}", e.getMessage());
    }
  }

  /**
   * 递增运行中任务数（任务开始执行时调用）。
   *
   * @return 递增后的值
   */
  public long increment() {
    try {
      Long count = cronjobRedisOps.incr(RUNNING_COUNT_SEGMENT, 1);
      if (count != null && count == 1) {
        // 首次创建时设置 TTL（防止异常退出后永久残留）
        cronjobRedisOps.expire(RUNNING_COUNT_SEGMENT, INITIAL_TTL_SECONDS);
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
      long current = cronjobRedisOps.decr(RUNNING_COUNT_SEGMENT, 1);
      if (current < 0) {
        cronjobRedisOps.setLong(RUNNING_COUNT_SEGMENT, 0);
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
      return cronjobRedisOps.getLong(RUNNING_COUNT_SEGMENT);
    } catch (Exception e) {
      log.debug("[RunningTaskCounter] 读取失败: reason={}", e.getMessage());
      return 0;
    }
  }
}
