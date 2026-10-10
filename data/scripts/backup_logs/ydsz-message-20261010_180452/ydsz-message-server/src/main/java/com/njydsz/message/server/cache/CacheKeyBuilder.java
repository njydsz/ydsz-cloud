package com.njydsz.message.server.cache;

import org.springframework.stereotype.Component;

import com.njydsz.common.cache.support.AbstractModuleCacheKeyBuilder;

/**
 * Message 模块缓存键构造器（P1-1 整改：继承公共基类 {@link AbstractModuleCacheKeyBuilder}）。
 *
 * <p>为 Message 模块的幂等键、频控键、去重键、路由规则、聚合锁等 Redis 缓存键提供租户感知的统一生成能力，
 * 替代原来自建的字符串拼接（{@code "ydsz:msg:idempotent:"}、{@code "dnd:"}、{@code "suppress:"} 等）。
 *
 * <p><b>统一格式：</b>{@code ydsz:{tenantId}:message:{entity}:{id}}
 *
 * @author ydsz-team
 * @since 26.09.29
 */
@Component("messageCacheKeyBuilder")
public class CacheKeyBuilder extends AbstractModuleCacheKeyBuilder {

  /** 模块标识：消息引擎 */
  private static final String MODULE = "message";

  /** 构造 Message 模块缓存键构造器。 */
  public CacheKeyBuilder() {
    super(MODULE);
  }

  // ============================== 消息幂等 key ==============================

  /**
   * 生成「消息发送幂等」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:idempotent:{idempotentKey}}
   *
   * @param idempotentKey 幂等键（业务方唯一标识）
   * @return 租户隔离的缓存键
   */
  public String idempotent(String idempotentKey) {
    return buildKey("idempotent", idempotentKey);
  }

  // ============================== 频控 key ==============================

  /**
   * 生成「接收人维度频控」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:ratelimit:receiver:{receiverId}}
   *
   * @param receiverId 接收人标识
   * @return 租户隔离的缓存键
   */
  public String rateLimitReceiver(String receiverId) {
    return buildKeyPattern("ratelimit", "receiver", receiverId);
  }

  /**
   * 生成「模板维度频控」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:ratelimit:template:{templateId}}
   *
   * @param templateId 模板 ID
   * @return 租户隔离的缓存键
   */
  public String rateLimitTemplate(String templateId) {
    return buildKeyPattern("ratelimit", "template", templateId);
  }

  /**
   * 生成「租户维度频控」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:ratelimit:tenant:{apiPath}}
   *
   * @param apiPath API 路径标识
   * @return 租户隔离的缓存键
   */
  public String rateLimitTenant(String apiPath) {
    return buildKeyPattern("ratelimit", "tenant", apiPath);
  }

  // ============================== 消息去重 key ==============================

  /**
   * 生成「消息去重」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:dedup:{dedupKey}}
   *
   * @param dedupKey 去重指纹
   * @return 租户隔离的缓存键
   */
  public String dedup(String dedupKey) {
    return buildKey("dedup", dedupKey);
  }

  // ============================== 免打扰 key ==============================

  /**
   * 生成「用户免打扰配置」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:dnd:{userId}}
   *
   * @param userId 用户 ID
   * @return 租户隔离的缓存键
   */
  public String dnd(String userId) {
    return buildKey("dnd", userId);
  }

  // ============================== 抑制 key ==============================

  /**
   * 生成「业务维度消息抑制」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:suppress:{bizType}:{bizId}:{receiver}}
   *
   * @param bizType 业务类型
   * @param bizId 业务 ID
   * @param receiver 接收人
   * @return 租户隔离的缓存键
   */
  public String suppress(String bizType, String bizId, String receiver) {
    return buildKeyPattern("suppress", bizType, bizId, receiver);
  }

  // ============================== 路由规则 key ==============================

  /**
   * 生成「路由规则」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:route:rules}
   *
   * @return 租户隔离的缓存键
   */
  public String routeRules() {
    return buildKey("route:rules", "");
  }

  // ============================== 聚合 key ==============================

  /**
   * 生成「消息聚合锁」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:aggregate:lock:{aggregateKey}}
   *
   * @param aggregateKey 聚合键
   * @return 租户隔离的缓存键
   */
  public String aggregateLock(String aggregateKey) {
    return buildKeyPattern("aggregate", "lock", aggregateKey);
  }

  /**
   * 生成「消息聚合计数器」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:aggregate:counter:{aggregateKey}}
   *
   * @param aggregateKey 聚合键
   * @return 租户隔离的缓存键
   */
  public String aggregateCounter(String aggregateKey) {
    return buildKeyPattern("aggregate", "counter", aggregateKey);
  }

  // ============================== 重试/扫描锁 key ==============================

  /**
   * 生成「重试扫描锁」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:retry:scan:lock}
   *
   * @return 租户隔离的缓存键
   */
  public String retryScanLock() {
    return buildKey("retry:scan:lock", "");
  }

  /**
   * 生成「聚合扫描锁」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:aggregate:scan:lock}
   *
   * @return 租户隔离的缓存键
   */
  public String aggregateScanLock() {
    return buildKey("aggregate:scan:lock", "");
  }

  /**
   * 生成「回执拉取锁」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:receipt:pull:lock}
   *
   * @return 租户隔离的缓存键
   */
  public String receiptPullLock() {
    return buildKey("receipt:pull:lock", "");
  }

  // ============================== 频率统计 key ==============================

  /**
   * 生成「日频统计」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:freq:daily:{target}:{date}}
   *
   * @param target 统计目标（userId / templateId）
   * @param date 日期（yyyyMMdd）
   * @return 租户隔离的缓存键
   */
  public String frequencyDaily(String target, String date) {
    return buildKeyPattern("freq", "daily", target, date);
  }

  /**
   * 生成「小时频统计」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:message:freq:hourly:{target}:{datetime}}
   *
   * @param target 统计目标
   * @param datetime 小时时间戳
   * @return 租户隔离的缓存键
   */
  public String frequencyHourly(String target, String datetime) {
    return buildKeyPattern("freq", "hourly", target, datetime);
  }
}
