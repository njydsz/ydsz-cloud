package com.njydsz.agent.server.chat;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.tenant.TenantContextHolder;

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
 * <p>基于 Redis SETNX，key = {@code ydsz:agent:idem:{requestId}}，TTL 60s。 同一 requestId 60 秒内只能成功调用一次。
 *
 * <h3>限流</h3>
 *
 * <p>基于 Redis INCR + EXPIRE 实现固定窗口计数，key = {@code ydsz:agent:rate:{tenantId}:{userId}}， 默认
 * 10 QPM（每分钟 10 次），阈值可通过配置 {@code ydsz.agent.guardrail.max-requests-per-minute} 调整。
 *
 * <p><b>已知限制（P2 说明）</b>：固定窗口在窗口边界存在突刺（瞬时可放行 2 倍流量）， 对 LLM 成本敏感场景可后续升级为滑动窗口
 * Lua 脚本实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class AgentRequestGuard {

  private static final String IDEM_KEY_PREFIX = "ydsz:agent:idem:";
  private static final String RATE_KEY_PREFIX = "ydsz:agent:rate:";
  private static final Duration IDEM_TTL = Duration.ofSeconds(60);
  private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

  private final RedisStringOps stringOps;

  /** 单用户每分钟请求上限（默认 10，可通过配置覆盖；P2 修复：原值硬编码） */
  private final int maxRequestsPerMinute;

  public AgentRequestGuard(RedisStringOps stringOps) {
    this(stringOps, 10);
  }

  /**
   * 构造 Agent 请求守卫。
   *
   * @param stringOps Redis String 操作组件
   * @param maxRequestsPerMinute 单用户每分钟请求上限
   */
  public AgentRequestGuard(RedisStringOps stringOps, int maxRequestsPerMinute) {
    this.stringOps = stringOps;
    this.maxRequestsPerMinute = maxRequestsPerMinute > 0 ? maxRequestsPerMinute : 10;
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
    checkRateLimit(effectiveUserId);
    if (requestId != null && !requestId.isBlank()) {
      checkIdempotent(requestId);
    }
  }

  /** 幂等检查：SETNX，已存在则拒绝 */
  private void checkIdempotent(String requestId) {
    String key = IDEM_KEY_PREFIX + requestId;
    Boolean acquired = stringOps.setIfAbsent(key, "1", IDEM_TTL.toSeconds());
    if (acquired == null || !acquired) {
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
   * <p>使用 Redis INCR + EXPIRE 实现固定窗口计数。 窗口内首次请求设置 TTL，后续请求递增计数。
   */
  private void checkRateLimit(String userId) {
    String key = RATE_KEY_PREFIX + buildTenantSegment() + userId;
    long count = stringOps.incr(key, 1);
    if (count == 1) {
      stringOps.expire(key, RATE_WINDOW.toSeconds());
    }
    if (count > maxRequestsPerMinute) {
      log.warn("[Agent-Guard] 限流触发: key={}, count={}", key, count);
      throw BusinessException.builder()
          .code("RATE_LIMIT_EXCEEDED")
          .message("请求过于频繁，每分钟最多 " + maxRequestsPerMinute + " 次")
          .build();
    }
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
   */
  public void releaseIdempotent(String requestId) {
    if (requestId == null || requestId.isBlank()) {
      return;
    }
    String key = IDEM_KEY_PREFIX + requestId;
    stringOps.del(key);
  }
}

