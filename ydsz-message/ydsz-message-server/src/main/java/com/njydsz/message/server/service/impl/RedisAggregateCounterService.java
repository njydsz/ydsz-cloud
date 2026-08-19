package com.njydsz.message.server.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.message.domain.constant.MessageConstants;

/**
 * 基于 Redis INCR 的聚合计数器服务（替代分布式锁方案）。
 *
 * <p>聚合场景：同一用户同一业务的消息在短时间窗口内需要合并为一条摘要发送。
 * 原方案使用分布式锁 + DB 操作，高并发下锁竞争严重。
 *
 * <p>新方案使用 Redis 原子计数 + 异步落库：
 * <ol>
 *   <li>Redis INCR 原子递增计数（无锁竞争）</li>
 *   <li>首次计数（返回1）时占位新建聚合批次标记</li>
 *   <li>写入成功即返回，DB 异步批量刷新（最终一致性）</li>
 * </ol>
 *
 * <p>优势：
 * <ul>
 *   <li>无锁竞争：Redis 单线程原子操作</li>
 *   <li>高性能：单次操作 &lt; 1ms</li>
 *   <li>降级兜底：Redis 异常时回退到原分布式锁方案</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.message.aggregate",
    name = "redis-counter-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RedisAggregateCounterService {

  private final RedisStringOps redisStringOps;
  private final DistributedLocker distributedLocker;

  /** 默认聚合窗口（分钟） */
  private static final long DEFAULT_AGGREGATE_WINDOW_MINUTES = 30L;

  /** 锁降级时的等待/租约时间 */
  private static final long LOCK_WAIT_SECONDS = 3L;
  private static final long LOCK_LEASE_SECONDS = 10L;

  /**
   * 尝试原子递增聚合计数。
   *
   * <p>如果 Redis 可用，使用 INCR 原子递增；如果 Redis 不可用，返回 -1 触发降级。
   *
   * @param group 聚合组（bizType）
   * @param receiver 接收人
   * @param channel 通道
   * @param tenantId 租户 ID
   * @return 递增后的计数值（>=1 表示首次聚合窗口创建），-1 表示 Redis 不可用需降级
   */
  public long tryIncrement(String group, String receiver, String channel, String tenantId) {
    String counterKey = buildCounterKey(group, receiver);
    String batchKey = buildBatchKey(group, receiver);

    try {
      // 原子递增计数，同时设置过期时间（首次创建时）
      Long count = redisStringOps.incr(counterKey, 1);

      if (count != null && count == 1L) {
        // 首次创建，设置过期时间和批次占位标记
        redisStringOps.expire(counterKey, Duration.ofMinutes(DEFAULT_AGGREGATE_WINDOW_MINUTES + 1));
        // 记录首次占位信息（批次元数据）
        Map<String, String> batchMeta = new HashMap<>();
        batchMeta.put("channel", channel);
        batchMeta.put("tenantId", tenantId);
        batchMeta.put("firstAt", LocalDateTime.now().toString());
        redisStringOps.set(batchKey, batchMeta, Duration.ofMinutes(DEFAULT_AGGREGATE_WINDOW_MINUTES + 1));
        log.debug("[AggregateCounter] 首次创建聚合窗口: group={} receiver={}", group, receiver);
      }

      return count != null ? count : -1L;
    } catch (Exception e) {
      log.warn("[AggregateCounter] Redis INCR 异常，需降级: group={} err={}", group, e.getMessage(), e);
      return -1L;
    }
  }

  /**
   * 获取当前聚合计数（不递增）。
   *
   * @param group 聚合组
   * @param receiver 接收人
   * @return 当前计数值，Redis 不可用时返回 -1
   */
  public long getCount(String group, String receiver) {
    String counterKey = buildCounterKey(group, receiver);
    try {
      Long count = redisStringOps.get(counterKey, Long.class);
      return count != null ? count : 0L;
    } catch (Exception e) {
      log.warn("[AggregateCounter] Redis GET 异常: group={} err={}", group, e.getMessage(), e);
      return -1L;
    }
  }

  /**
   * 重置聚合计数（聚合批次发送完成后调用）。
   *
   * @param group 聚合组
   * @param receiver 接收人
   * @return 是否成功
   */
  public boolean reset(String group, String receiver) {
    String counterKey = buildCounterKey(group, receiver);
    String batchKey = buildBatchKey(group, receiver);
    try {
      redisStringOps.del(counterKey, batchKey);
      log.debug("[AggregateCounter] 重置计数: group={} receiver={}", group, receiver);
      return true;
    } catch (Exception e) {
      log.warn("[AggregateCounter] Redis DEL 异常: group={} err={}", group, e.getMessage(), e);
      return false;
    }
  }

  /**
   * 构建 Redis 计数 Key。
   *
   * @param group 聚合组
   * @param receiver 接收人
   * @return Redis Key
   */
  private String buildCounterKey(String group, String receiver) {
    return MessageConstants.AGGREGATE_COUNTER_PREFIX + group + ":" + receiver;
  }

  /**
   * 构建 Redis 批次元数据 Key。
   *
   * @param group 聚合组
   * @param receiver 接收人
   * @return Redis Key
   */
  private String buildBatchKey(String group, String receiver) {
    return MessageConstants.AGGREGATE_BATCH_PREFIX + group + ":" + receiver;
  }
}
