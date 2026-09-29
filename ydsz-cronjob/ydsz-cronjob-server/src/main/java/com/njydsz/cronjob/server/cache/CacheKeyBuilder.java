package com.njydsz.cronjob.server.cache;

import org.springframework.stereotype.Component;

import com.njydsz.common.cache.support.AbstractModuleCacheKeyBuilder;

/**
 * Cronjob 模块缓存键构造器（P1-1 整改：继承公共基类 {@link AbstractModuleCacheKeyBuilder}）。
 *
 * <p>为 Cronjob 模块的分布式锁、幂等键、配额键、事件去重、异常自愈、节点黑名单等缓存键提供租户感知的统一生成能力，
 * 替代原来自建的字符串拼接（{@code "ydsz:quota:concurrent:"}、{@code "ydsz:job:lock:"} 等）。
 *
 * <p><b>统一格式：</b>{@code ydsz:{tenantId}:cronjob:{entity}:{id}}
 *
 * @author ydsz-team
 * @since 26.09.29
 */
@Component("cronjobCacheKeyBuilder")
public class CacheKeyBuilder extends AbstractModuleCacheKeyBuilder {

  /** 模块标识：定时任务引擎 */
  private static final String MODULE = "cronjob";

  /** 构造 Cronjob 模块缓存键构造器。 */
  public CacheKeyBuilder() {
    super(MODULE);
  }

  // ============================== 任务分布式锁 key ==============================

  /**
   * 生成「任务实例执行锁」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:job:lock:{jobInstanceId}}
   *
   * @param jobInstanceId 任务实例 ID
   * @return 租户隔离的缓存键
   */
  public String jobLock(String jobInstanceId) {
    return buildKeyPattern("job", "lock", jobInstanceId);
  }

  // ============================== 幂等锁 key ==============================

  /**
   * 生成「任务触发幂等锁」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:job:idempotent:{triggerKey}}
   *
   * @param triggerKey 触发幂等键
   * @return 租户隔离的缓存键
   */
  public String jobIdempotent(String triggerKey) {
    return buildKeyPattern("job", "idempotent", triggerKey);
  }

  // ============================== 事件去重 key ==============================

  /**
   * 生成「事件驱动去重」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:job:event:dedup:{eventFingerprint}}
   *
   * @param eventFingerprint 事件指纹
   * @return 租户隔离的缓存键
   */
  public String eventDedup(String eventFingerprint) {
    return buildKeyPattern("job", "event", "dedup", eventFingerprint);
  }

  // ============================== 配额 key ==============================

  /**
   * 生成「租户并发配额」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:quota:concurrent:{tenantId}}
   *
   * @param tenantId 租户 ID
   * @return 租户隔离的缓存键
   */
  public String quotaConcurrent(String tenantId) {
    return buildKeyPattern("quota", "concurrent", tenantId);
  }

  /**
   * 生成「租户日配额」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:quota:daily:{tenantId}:{date}}
   *
   * @param tenantId 租户 ID
   * @param date 日期（yyyyMMdd）
   * @return 租户隔离的缓存键
   */
  public String quotaDaily(String tenantId, String date) {
    return buildKeyPattern("quota", "daily", tenantId, date);
  }

  // ============================== 自动愈 key ==============================

  /**
   * 生成「异常自愈重试计数」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:job:heal:retry:{jobInstanceId}}
   *
   * @param jobInstanceId 任务实例 ID
   * @return 租户隔离的缓存键
   */
  public String healRetry(String jobInstanceId) {
    return buildKeyPattern("job", "heal", "retry", jobInstanceId);
  }

  // ============================== 节点黑名单 key ==============================

  /**
   * 生成「调度节点黑名单分段」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:node:blacklist:{segment}}
   *
   * @param segment 黑名单分片标识
   * @return 租户隔离的缓存键
   */
  public String nodeBlacklist(String segment) {
    return buildKeyPattern("node", "blacklist", segment);
  }

  // ============================== 任务通用操作 key ==============================

  /**
   * 生成「L1 任务缓存」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:cache:job:{jobKey}}
   *
   * @param jobKey 任务标识
   * @return 租户隔离的缓存键
   */
  public String jobCache(String jobKey) {
    return buildKeyPattern("cache", "job", jobKey);
  }

  // ============================== 重试/扫描锁 key ==============================

  /**
   * 生成「重试扫描锁」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:retry:scan:lock}
   *
   * @return 租户隔离的缓存键
   */
  public String retryScanLock() {
    return buildKey("retry:scan:lock", "");
  }

  /**
   * 生成「健康巡检锁」缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:cronjob:health:scan:lock}
   *
   * @return 租户隔离的缓存键
   */
  public String healthScanLock() {
    return buildKey("health:scan:lock", "");
  }
}
