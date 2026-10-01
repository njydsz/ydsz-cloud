package com.njydsz.agent.server.chat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.redis.service.RedisRateLimiter;
import com.njydsz.common.redis.service.ops.RedisStringOps;


/**
 * Agent 请求守卫：幂等去重 + 限流
 *
 * <p>在 LLM 调用前进行前置检查，防止：
 *
 * <ul>
 *   <li>重复请求（前端双击/网络重试）导致重复扣费
 *   <li>恶意刷接口导致 LLM API Key 配额耗尽
 * </ul>
 *
 * <h3>幂等去重</h3>
 *
 * <p>基于 {@link DistributedLocker#tryLock}，key = {@code ydsz:agent:idem:{requestId}}，TTL 60s。 同一 requestId 60 秒内只能成功调用一次。
 * 通过 common-lock 的 WatchDog 续期机制，可应对长任务场景（需配合外部释放）。
 *
 * <h3>限流</h3>
 *
 * <p>基于 RedisRateLimiter + Lua 脚本实现固定窗口计数（原子化 INCR+EXPIRE）， key = {@code ydsz:agent:rate:{tenantId}:{userId}}，默认
 * 10 QPM（每分钟 10 次），阈值可通过配置 {@code ydsz.agent.guardrail.max-requests-per-minute} 调整。
 *
 * <p><b>已知限制（P2 说明）</b>：固定窗口在窗口边界存在突刺（瞬时可放行 2 倍流量）， 对 LLM 成本敏感场景可后续升级为滑动窗口
 * Lua 脚本实现。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@RequiredArgsConstructor
public class AgentRequestGuard {

  private static final String IDEM_KEY_PREFIX = "ydsz:agent:idem:";
  private static final String RATE_KEY_PREFIX = "ydsz:agent:rate:";

  /** 默认幂等锁 TTL（60s） */
  private static final Duration DEFAULT_IDEMP_TTL = Duration.ofSeconds(60);

  /** 默认限流时间窗口（1m） */
  private static final Duration DEFAULT_RATE_WINDOW = Duration.ofMinutes(1);

  /** 默认单用户每分钟请求上限 */
  private static final int DEFAULT_MAX_REQUESTS_PER_MINUTE = 10;

  private final RedisStringOps stringOps;
  /** 分布式锁实例（幂等去重，使用 common-lock WatchDog 续期 + 可重入能力） */
  private final DistributedLocker distributedLocker;

  /**
   * 分布式限流组件（Redis + Lua 原子 INCR+EXPIRE）。
   *
   * <p>P0-FIX：替代手写 INCR+EXPIRE 分步调用，确保计数与 TTL 设置的原子性。 原实现中若 EXPIRE 调用失败，key 永不过期，导致限流阈值被永久锁死、服务不可用。
   */
  private final RedisRateLimiter redisRateLimiter;

  /** 幂等锁 TTL（默认 60s，可通过配置 ydsz.agent.guardrail.idempotent-ttl 覆盖） */
  private final Duration idempotentTtl;

  /** 限流时间窗口（默认 1m，可通过配置 ydsz.agent.guardrail.rate-window 覆盖） */
  private final Duration rateWindow;

  /** 单用户每分钟请求上限（默认 10，可通过配置覆盖） */
  private final int maxRequestsPerMinute;

  /**
   * @RequiredArgsConstructor 生成的 6 参数主构造（顺序与字段声明顺序一致）：
   * stringOps → distributedLocker → redisRateLimiter → idempotentTtl → rateWindow → maxRequestsPerMinute
   */

  /**
   * 便利构造（限流阈值、幂等 TTL、限流窗口均使用默认值）。
   *
   * @param stringOps Redis String 操作组件
   * @param distributedLocker 分布式锁实例（幂等去重）
   * @param redisRateLimiter 分布式限流器（原子 INCR+EXPIRE）
   */
  public AgentRequestGuard(
      RedisStringOps stringOps,
      DistributedLocker distributedLocker,
      RedisRateLimiter redisRateLimiter) {
    this(
        stringOps,
        distributedLocker,
        redisRateLimiter,
        DEFAULT_IDEMP_TTL,
        DEFAULT_RATE_WINDOW,
        DEFAULT_MAX_REQUESTS_PER_MINUTE);
  }

  /**
   * 便利构造（自定义限流阈值）。
   *
   * @param stringOps Redis String 操作组件
   * @param distributedLocker 分布式锁实例（幂等去重）
   * @param redisRateLimiter 分布式限流器（原子 INCR+EXPIRE）
   * @param maxRequestsPerMinute 单用户每分钟请求上限
   */
  public AgentRequestGuard(
      RedisStringOps stringOps,
      DistributedLocker distributedLocker,
      RedisRateLimiter redisRateLimiter,
      int maxRequestsPerMinute) {
    this(
        stringOps,
        distributedLocker,
        redisRateLimiter,
        DEFAULT_IDEMP_TTL,
        DEFAULT_RATE_WINDOW,
        Math.max(1, maxRequestsPerMinute));
  }

  /**
   * 检查请求是否允许执行（幂等 + 限流）
   *
   * @param requestId 请求幂等键（null 则跳过幂等检查）
   * @param userId 用户 ID（null 则用 "anonymous"）
   * @throws BusinessException 重复请求
   * @throws BusinessException 请求超限
   */
  public void check(String requestId, String userId) {
    String effectiveUserId = userId != null ? userId : "anonymous";
    String resource = RATE_KEY_PREFIX + buildTenantSegment() + effectiveUserId;
    if (!checkRateLimit(resource, maxRequestsPerMinute, rateWindow)) {
      log.warn("[Agent-Guard] 限流触发: resource={}, threshold={}", resource, maxRequestsPerMinute);
      throw BusinessException.builder()
          .code("RATE_LIMIT_EXCEEDED")
          .message("请求过于频繁，每分钟最多 " + maxRequestsPerMinute + " 次")
          .build();
    }
    if (requestId != null && !requestId.isBlank()) {
      checkIdempotent(requestId);
    }
  }

  /** 幂等检查：使用 DistributedLocker，已存在则拒绝 */
  private void checkIdempotent(String requestId) {
    String key = IDEM_KEY_PREFIX + requestId;
    // P0-FIX：使用 common-lock 分布式锁替代裸 SETNX（统一走 common-lock）
    String lockValue = distributedLocker.tryLock(key, idempotentTtl.toSeconds(), TimeUnit.SECONDS);
    if (lockValue == null) {
      log.warn("[Agent-Guard] 重复请求被拒绝: requestId={}", requestId);
      throw BusinessException.builder()
          .code("REQUEST_DUPLICATE")
          .message("重复请求，请勿在 60 秒内重复提交")
          .build();
    }
  }

  /**
   * 限流检查：固定窗口计数（按租户 + 用户维度隔离）。
   *
   * <p><b>P0-FIX 迁移说明：</b>原实现为手写 {@code INCR} + {@code EXPIRE} 分步调用，若 EXPIRE 失败则 key 永不过期， 阈值被永久锁死。现已统一迁移至 {@link RedisRateLimiter#tryAcquireFixedWindow}(
   * Lua 脚本 KEYS 单命令原子执行 INCR+EXPIRE)。
   *
   * @param resource 限流维度标识（已拼接租户 + 用户信息）
   * @param threshold 窗口内最大请求数
   * @param window 时间窗口长度
   * @return true=允许，false=拒绝
   */
  public boolean checkRateLimit(String resource, int threshold, Duration window) {
    return redisRateLimiter.tryAcquireFixedWindow(resource, threshold, window);
  }

  /**
   * 构建限流 key 的租户段（多租户隔离，避免跨租户互相挤占额度）。
   *
   * @return 租户段字符串（如 {@code tenantId:}）；无租户上下文时返回空串
   */
  private String buildTenantSegment() {
    if (TenantContextHolder.isPresent()
        && !TenantContextHolder.isSkipIsolation()
        && !TenantContextHolder.isSuperAdmin()
        && TenantContextHolder.getTenantId() != null) {
      return TenantContextHolder.getTenantId() + ":";
    }
    return "";
  }

  /**
   * 释放幂等锁（业务异常时调用，允许重试）。
   *
   * @param requestId 幂等请求 ID
   * @param lockValue 释放操作需要的锁标识
   */
  public void releaseIdempotent(String requestId, String lockValue) {
    if (requestId == null || requestId.isBlank()) {
      return;
    }
    String key = IDEM_KEY_PREFIX + requestId;
    distributedLocker.unlock(key, lockValue);
  }
}

