package com.njydsz.message.server.service.impl.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.lock.idempotent.IdempotentStrategy;
import com.njydsz.common.redis.service.RedisRateLimiter;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.entity.config.MsgPreference;
import com.njydsz.message.domain.enums.core.MessagePriorityEnum;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.config.PreferenceService;
import com.njydsz.message.server.service.core.GuardService;

/**
 * 消息防护服务实现（限流 + 去重）。
 *
 * <p>合并原 RateLimitServiceImpl 与 DedupServiceImpl，提供消息发送前的流量控制能力。
 *
 * <p>令牌桶限流委托 {@link RedisRateLimiter}（ydsz-common-redis 公共能力）；
 * 每日 / 每小时频率使用 Redis INCR + EXPIRE，上限取自用户偏好；
 * 去重使用 {@link IdempotentStrategy#acquire} 实现原子去重。
 *
 * <p>降级策略：Redis 异常时 fail-open（返回 true），仅记 WARN 日志，不阻断业务。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Service
public class GuardServiceImpl implements GuardService {

  /** 小时频率计数器 key 时间格式 */
  private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

  /** 日频率计数器 key 时间格式 */
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

  /** Redis 令牌桶限流器（可选依赖，不可用时降级放行） */
  private final RedisRateLimiter rateLimiter;

  /** Redis 基础服务（用于 INCR/EXPIRE 频率计数） */
  private final RedisStringOps redisStringOps;

  /** 幂等策略（SET NX EX 原子去重） */
  private final IdempotentStrategy idempotentStrategy;

  /** 用户偏好服务（读取 hourlyLimit/dailyLimit） */
  private final PreferenceService preferenceService;

  /** 消息模块配置属性 */
  private final MessageProperties messageProperties;

  /**
   * Redis 故障时的降级策略。
   *
   * <ul>
   *   <li>{@code true}（默认）：fail-open，Redis 异常时放行，保证可用性
   *   <li>{@code false}：fail-closed，Redis 异常时拒绝，保证安全性（生产环境推荐）
   * </ul>
   */
  @Value("${ydsz.message.rate-limit.fail-open:true}")
  private boolean failOpen;

  public GuardServiceImpl(
      ObjectProvider<RedisRateLimiter> rateLimiterProvider,
      RedisStringOps redisStringOps,
      IdempotentStrategy idempotentStrategy,
      PreferenceService preferenceService,
      MessageProperties messageProperties) {
    this.rateLimiter = rateLimiterProvider.getIfAvailable();
    this.redisStringOps = redisStringOps;
    this.idempotentStrategy = idempotentStrategy;
    this.preferenceService = preferenceService;
    this.messageProperties = messageProperties;
    if (this.rateLimiter == null) {
      log.warn("[Guard] RedisRateLimiter 不可用，令牌桶限流将降级放行");
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>委托 {@link RedisRateLimiter#tryAcquireTokenBucket} 令牌桶限流， rateLimiter 不可用或异常时降级放行（返回 true）。
   */
  @Override
  public boolean tryAcquire(String key, int permits) {
    if (key == null || key.isBlank() || permits <= 0) {
      return true;
    }
    if (rateLimiter == null) {
      return true;
    }
    try {
      return rateLimiter.tryAcquireTokenBucket(
          MessageConstants.RATE_LIMIT_KEY_PREFIX + key, permits, permits, Duration.ofSeconds(1), 1);
    } catch (Exception e) {
      if (failOpen) {
        log.warn("[Guard] tryAcquire 降级放行(fail-open): key={} err={}", key, e.getMessage(), e);
        return true;
      }
      log.warn("[Guard] tryAcquire 降级拒绝(fail-closed): key={} err={}", key, e.getMessage(), e);
      return false;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>从用户偏好读取 hourlyLimit/dailyLimit，使用 Redis INCR + EXPIRE 计数，任一维度超限即返回 false。
   */
  @Override
  public boolean checkFrequency(String userId, String channel, String bizType) {
    if (userId == null || userId.isBlank()) {
      return true;
    }
    MsgPreference pref = preferenceService.getByUser(userId, channel, bizType);
    if (pref == null || pref.getEnabled() == null) {
      return true;
    }
    if (pref.getEnabled() == 0) {
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    if (pref.getHourlyLimit() != null && pref.getHourlyLimit() > 0) {
      Long cur =
          readCounter(
              MessageConstants.FREQUENCY_HOURLY_PREFIX,
              userId,
              channel,
              bizType,
              now.format(HOUR_FMT));
      if (cur != null && cur >= pref.getHourlyLimit()) {
        log.info(
            "[Guard] 频率超限(小时): user={} channel={} cur={} limit={}",
            userId,
            channel,
            cur,
            pref.getHourlyLimit());
        return false;
      }
    }
    if (pref.getDailyLimit() != null && pref.getDailyLimit() > 0) {
      Long cur =
          readCounter(
              MessageConstants.FREQUENCY_DAILY_PREFIX,
              userId,
              channel,
              bizType,
              now.format(DAY_FMT));
      if (cur != null && cur >= pref.getDailyLimit()) {
        log.info(
            "[Guard] 频率超限(日): user={} channel={} cur={} limit={}",
            userId,
            channel,
            cur,
            pref.getDailyLimit());
        return false;
      }
    }
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>同时递增小时和日频率计数器（Redis INCR + EXPIRE）。
   */
  @Override
  public void recordFrequency(String userId, String channel, String bizType) {
    if (userId == null || userId.isBlank()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    incrCounter(
        MessageConstants.FREQUENCY_HOURLY_PREFIX,
        userId,
        channel,
        bizType,
        now.format(HOUR_FMT),
        Duration.ofHours(1).plusMinutes(5).getSeconds());
    incrCounter(
        MessageConstants.FREQUENCY_DAILY_PREFIX,
        userId,
        channel,
        bizType,
        now.format(DAY_FMT),
        Duration.ofDays(1).plusHours(1).getSeconds());
  }

  /** {@inheritDoc} */
  @Override
  public boolean checkSendLimit(
      String channel, String receiver, String templateCode, String tenantId) {
    MessageProperties.RateLimitConfig cfg = messageProperties.getRateLimit();
    if (cfg == null) {
      return true;
    }
    if (cfg.isReceiverEnabled() && receiver != null && !receiver.isBlank()) {
      if (!tryAcquire("receiver:" + receiver, cfg.getReceiverPermits())) {
        log.info(
            "[Guard] receiver 维度限流: channel={} receiver={} permits={}/s",
            channel,
            receiver,
            cfg.getReceiverPermits());
        return false;
      }
    }
    if (cfg.isTemplateEnabled() && templateCode != null && !templateCode.isBlank()) {
      if (!tryAcquire("template:" + templateCode, cfg.getTemplatePermits())) {
        log.info(
            "[Guard] template 维度限流: channel={} template={} permits={}/s",
            channel,
            templateCode,
            cfg.getTemplatePermits());
        return false;
      }
    }
    if (cfg.isTenantEnabled() && tenantId != null && !tenantId.isBlank()) {
      if (!tryAcquire("tenant:" + tenantId, cfg.getTenantPermits())) {
        log.info(
            "[Guard] tenant 维度限流: channel={} tenant={} permits={}/s",
            channel,
            tenantId,
            cfg.getTenantPermits());
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public boolean checkSendLimit(
      String channel, String receiver, String templateCode, String tenantId, String priority) {
    MessagePriorityEnum priorityEnum = MessagePriorityEnum.fromString(priority);
    if (priorityEnum.canSkipRateLimit()) {
      MessageProperties.RateLimitConfig cfg = messageProperties.getRateLimit();
      if (cfg == null || !cfg.isReceiverEnabled() || receiver == null || receiver.isBlank()) {
        return true;
      }
      return tryAcquire("receiver:" + receiver, cfg.getReceiverPermits());
    }
    return checkSendLimit(channel, receiver, templateCode, tenantId);
  }

  /** {@inheritDoc} */
  @Override
  public boolean tryDedup(String dedupKey) {
    if (dedupKey == null || dedupKey.isBlank()) {
      return true;
    }
    MessageProperties.DedupConfig cfg = messageProperties.getDedup();
    if (cfg == null || !cfg.isEnabled()) {
      return true;
    }
    int ttl = cfg.getTtlSeconds() <= 0 ? 60 : cfg.getTtlSeconds();
    String redisKey = MessageConstants.DEDUP_KEY_PREFIX + dedupKey;
    try {
      String token = idempotentStrategy.acquire(redisKey, ttl * 1000L);
      if (token != null) {
        log.debug("[Guard] 首次到达,放行: key={} ttl={}s", dedupKey, ttl);
        return true;
      }
      log.info("[Guard] 检测到重复消息,跳过发送: key={} ttl={}s", dedupKey, ttl);
      return false;
    } catch (Exception e) {
      log.warn("[Guard] 去重检查异常(fail-open): key={} err={}", dedupKey, e.getMessage(), e);
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public Duration getDedupTtl() {
    MessageProperties.DedupConfig cfg = messageProperties.getDedup();
    if (cfg == null) {
      return Duration.ofSeconds(60);
    }
    return Duration.ofSeconds(cfg.getTtlSeconds() <= 0 ? 60 : cfg.getTtlSeconds());
  }

  private Long readCounter(
      String prefix, String userId, String channel, String bizType, String suffix) {
    String key =
        prefix
            + userId
            + ":"
            + (channel == null ? SystemConstants.SYSTEM_USER_ID : channel)
            + ":"
            + (bizType == null ? SystemConstants.SYSTEM_USER_ID : bizType)
            + ":"
            + suffix;
    String val = redisStringOps.get(key, String.class);
    if (val == null || val.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(val);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private void incrCounter(
      String prefix,
      String userId,
      String channel,
      String bizType,
      String suffix,
      long ttlSeconds) {
    String key =
        prefix
            + userId
            + ":"
            + (channel == null ? SystemConstants.SYSTEM_USER_ID : channel)
            + ":"
            + (bizType == null ? SystemConstants.SYSTEM_USER_ID : bizType)
            + ":"
            + suffix;
    try {
      Long count = redisStringOps.incr(key, 1);
      if (count != null && count == 1L) {
        redisStringOps.expire(key, Duration.ofSeconds(ttlSeconds));
      }
    } catch (Exception e) {
      log.warn("[Guard] 计数失败(降级忽略): key={} err={}", key, e.getMessage(), e);
    }
  }
}
