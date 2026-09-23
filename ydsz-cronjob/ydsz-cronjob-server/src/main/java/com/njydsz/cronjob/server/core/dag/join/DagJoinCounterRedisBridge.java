package com.njydsz.cronjob.server.core.dag.join;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.config.LeaderConfig;
import com.njydsz.cronjob.server.core.redis.CronjobRedisOps;

/**
 * P0-1: DAG 并行网关 Join 计数器 Redis 持久化桥接。
 *
 * <p>将{@link com.njydsz.cronjob.server.core.dag.DagInstanceExecutor}中
 * 原内存 {@code ConcurrentHashMap<String,Integer> parallelJoinCounter}
 * 重构为基于 Redis 的中心化计数器。
 *
 * <p><b>改造动机（P0 级缺陷）：</b>
 *
 * <ul>
 *   <li>原实现将 Join 入边完成计数存在 JVM 堆内存，多实例部署下各节点计数器独立</li>
 *   <li>Leader 切换后 Follower 接手时内存计数器丢失，Join 条件永远不满足，DAG 实例永久卡死</li>
 *   <li>改用 Redis INCR 原子操作后多实例共享同一计数状态，Leader 切换安全</li>
 * </ul>
 *
 * <p><b>Key 设计：</b>{@code ydzs:job:dag:join:{dagInstanceId}:{jobKey}}。
 * TTL 默认 24h（覆盖绝大多数 DAG 执行窗口），DAG 实例终结时主动 DEL 清理。
 *
 * <p><b>降级策略：</b>Redis 异常时按"零计数"语义兜底并打 warn 日志，避免阻塞主流
 * 程。上层 {@code DagInstanceExecutor} 通过判断 {@code completedCount >= incoming.size()}
 * 处理降级（重试由下一次节点完成事件驱动继续计数）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagJoinCounterRedisBridge {

  /** Join 计数器 key segment 前缀 */
  private static final String JOIN_COUNTER_SEGMENT = "dag:join";

  /** Join 计数器默认 TTL（秒），24 小时 */
  private static final long JOIN_COUNTER_TTL_SECONDS = 86_400L;

  private final CronjobRedisOps cronjobRedisOps;

  /**
   * 原子递增 DAG 并行网关 Join 节点的入边完成计数。
   *
   * <p>Redis INCR 天然返回递增后的值，一次调用实现原 {@code merge(key, 1, Integer::sum)} 语义。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey        Join 节点 jobKey（PARALLEL_GATEWAY 节点）
   * @return 递增后的计数值；Redis 异常返回 -1，由调用方按"计数为 0"兜底处理
   */
  public long incrementJoinCount(String dagInstanceId, String jobKey) {
    String counterKey = buildCounterKey(dagInstanceId, jobKey);
    try {
      Long afterIncr = cronjobRedisOps.incr(counterKey, 1L);
      if (afterIncr == null) {
        // Redis ops 已捕获异常并 logged；此处降级返回 -1 让调用方感知
        return -1L;
      }
      // 首次创建时设置 TTL；已存在时 EXPIRE 重置窗口（极端长 DAG 不会提前过期）
      if (afterIncr == 1L) {
        cronjobRedisOps.expire(counterKey, JOIN_COUNTER_TTL_SECONDS);
      }
      return afterIncr;
    } catch (Exception e) {
      log.warn(
          "[DagJoinCounter] INCR 异常降级(返回 -1): dagInstanceId={} jobKey={} reason={}",
          dagInstanceId,
          jobKey,
          e.getMessage());
      return -1L;
    }
  }

  /**
   * 递减计数（可选能力：节点重试/Rollback 场景调用）。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey        Join 节点 jobKey
   * @return 递减后的计数值；Redis 异常返回 -1
   */
  public long decrementJoinCount(String dagInstanceId, String jobKey) {
    String counterKey = buildCounterKey(dagInstanceId, jobKey);
    try {
      long afterDecr = cronjobRedisOps.decr(counterKey, 1L);
      return afterDecr;
    } catch (Exception e) {
      log.warn(
          "[DagJoinCounter] DECR 异常降级(返回 -1): dagInstanceId={} jobKey={} reason={}",
          dagInstanceId,
          jobKey,
          e.getMessage());
      return -1L;
    }
  }

  /**
   * 读取当前 Join 计数值（供诊断/监控面板使用）。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey        Join 节点 jobKey
   * @return 当前计数值；Redis 异常或 key 不存在返回 0
   */
  public long getJoinCount(String dagInstanceId, String jobKey) {
    String counterKey = buildCounterKey(dagInstanceId, jobKey);
    return cronjobRedisOps.getLong(counterKey);
  }

  /**
   * 清理 DAG 实例关联的所有 Join 计数器。
   *
   * <p>DAG 实例终结（SUCCESS / FAILED / PARTIAL_SUCCESS / CANCELED）后由
   * {@link com.njydsz.cronjob.server.core.dag.DagInstanceExecutor}
   * 调用主动清理，避免 Redis key 长期占用。
   *
   * <p>注：无法枚举该实例的所有 Join key（无索引），依赖 TTL 兜底自动过期。
   * 本方法预留接口供未来引入精细化清理。
   *
   * @param dagInstanceId DAG 实例 ID
   */
  public void clearJoinCounter(String dagInstanceId, String jobKey) {
    String counterKey = buildCounterKey(dagInstanceId, jobKey);
    cronjobRedisOps.delete(counterKey);
    log.debug("[DagJoinCounter] 清理 Join 计数器: dagInstanceId={} jobKey={}", dagInstanceId, jobKey);
  }

  /**
   * 构建 Join 计数器的 Redis key segment。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey        Join 节点 jobKey
   * @return Redis key segment（不含模块前缀，由 {@link CronjobRedisOps} 统一加前缀）
   */
  private String buildCounterKey(String dagInstanceId, String jobKey) {
    return JOIN_COUNTER_SEGMENT + ":" + dagInstanceId + ":" + jobKey;
  }
}
