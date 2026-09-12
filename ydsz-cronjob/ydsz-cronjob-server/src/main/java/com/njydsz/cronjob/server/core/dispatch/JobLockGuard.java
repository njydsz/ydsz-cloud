package com.njydsz.cronjob.server.core.dispatch;

import java.net.InetAddress;
import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.job.JobHandler;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.JobLockManager;
import com.njydsz.cronjob.server.core.LockKeyUtil;

/**
 * 任务锁守卫（P1-1 从 DefaultTaskDispatcher 拆分）。
 *
 * <p>集中管理任务执行链路的两类分布式锁原语，职责单一、可独立单测：
 *
 * <ul>
 *   <li><b>任务锁</b>：按 jobKey（+分片索引）粒度去重，防止同一任务并发执行；使用
 *       {@link JobLockManager}（common-lock，WatchDog 续期 + 可重入）
 *   <li><b>幂等锁</b>：按 handler + params 粒度去重，防止相同参数的任务在集群中重复执行
 * </ul>
 *
 * <p>所有锁的获取与释放统一走 {@link JobLockManager}（ydsz-common-lock），不再使用 Lua 脚本。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class JobLockGuard {

  /** 本节点实例标识（hostname:pid，作为兜底 lockHolder 存入日志，仅供兼容读取 */
  public static final String INSTANCE_ID = initInstanceId();

  /** 幂等锁 key 前缀 */
  private static final String IDEMPOTENT_LOCK_PREFIX = "ydsz:job:idempotent:";

  /**
   * 幂等锁句柄（供释放使用）。
   *
   * @param key 幂等锁 key
   * @param value 锁持有者标识
   */
  public record IdempotentLockHandle(String key, String value) {}

  /**
   * 任务锁获取结果。
   *
   * @param key 锁 key
   * @param value 锁持有者标识（null 表示获取失败/锁被持有）
   */
  public record AcquiredLock(String key, String value) {}

  private final CronjobProperties cronjobProperties;
  private final JobLockManager jobLockManager;

  /**
   * 构造任务锁守卫。
   *
   * @param cronjobProperties 调度配置（TTL 规整）
   * @param jobLockManager 分布式锁管理器（common-lock，WatchDog 续期 + 可重入）
   */
  public JobLockGuard(CronjobProperties cronjobProperties, JobLockManager jobLockManager) {
    this.cronjobProperties = cronjobProperties;
    this.jobLockManager = jobLockManager;
  }

  /**
   * 获取任务锁（按 jobKey + 可选分片索引粒度）。
   *
   * @param job 任务定义
   * @param shardIndex 分片索引（null = 非分片任务）
   * @return 锁获取结果（key + value；value 为 null 表示锁被其他实例持有）
   */
  public AcquiredLock acquireJobLock(JobVO job, Integer shardIndex) {
    String lockKey =
        shardIndex == null
            ? LockKeyUtil.buildJobLockKey(job.getJobKey())
            : LockKeyUtil.buildJobLockKey(job.getJobKey(), shardIndex);
    Duration ttl = resolveLockTtl(job);
    // P0-FIX：统一走 common-lock，移除 SETNX 降级路径（消除无 WatchDog 续期、不可重入风险）
    String lockValue =
        shardIndex == null
            ? jobLockManager.tryAcquireLock(job.getJobKey(), null, ttl.toMillis())
            : jobLockManager.tryAcquireLock(job.getJobKey(), shardIndex, ttl.toMillis());
    return new AcquiredLock(lockKey, lockValue);
  }

  /**
   * 规整任务锁 TTL（收敛到 [min, max] 区间）。
   *
   * @param job 任务定义（含可选的 lockTtlMs）
   * @return 规整后的 TTL
   */
  public Duration resolveLockTtl(JobVO job) {
    Duration taskLevel = null;
    if (job.getLockTtlMs() != null && job.getLockTtlMs() > 0) {
      taskLevel = Duration.ofMillis(job.getLockTtlMs());
    }
    return cronjobProperties.normalizeTtl(taskLevel);
  }

  /**
   * 安全释放任务持有的分布式锁。
   *
   * <p>统一通过 JobLockManager（DistributedLocker）释放，仅持有者可释放。
   *
   * @param lockKey 锁 key（null 时跳过，仅用于兼容旧接口）
   * @param jobKey 任务 KEY（JobLockManager 释放需要）
   * @param shardIndex 分片索引（null = 非分片任务）
   * @param lockValue 锁持有者标识（null 时跳过释放）
   */
  public void releaseJobLock(String lockKey, String jobKey, Integer shardIndex, String lockValue) {
    if (lockKey == null || lockValue == null) {
      return;
    }
    try {
      jobLockManager.releaseLock(jobKey, shardIndex, lockValue);
    } catch (Exception e) {
      log.warn(
          "[LockGuard] 释放分布式锁失败(将等待 TTL 自动过期): key={} reason={}", lockKey, e.getMessage());
    }
  }

  /**
   * 生成幂等锁 key（基于 handler 的 idempotentKey 方法）。
   *
   * @param handler 任务处理器（null 时返回 null）
   * @param job 任务定义
   * @return 幂等锁 key
   */
  public String buildIdempotentLockKey(JobHandler handler, JobVO job) {
    if (handler == null) {
      return null;
    }
    String idempotentKey = handler.idempotentKey(job.getParamsJson());
    return IDEMPOTENT_LOCK_PREFIX + idempotentKey;
  }

  /**
   * 尝试获取幂等锁。
   *
   * @param idempotentLockKey 幂等锁 key（null 时返回空串放行）
   * @param ttl 锁 TTL
   * @return 锁持有者标识；获取失败返回 null；异常降级返回空字符串（放行）
   */
  public String tryAcquireIdempotentLock(String idempotentLockKey, Duration ttl) {
    if (idempotentLockKey == null) {
      return "";
    }
    try {
      // P0-FIX：统一走 common-lock，移除 SETNX 降级路径（消除不可重入风险）
      return jobLockManager.tryAcquireLock(idempotentLockKey, ttl.toMillis());
    } catch (Exception e) {
      log.warn("[LockGuard] 获取幂等锁异常, 降级放行: key={} reason={}", idempotentLockKey, e.getMessage());
      return "";
    }
  }

  /**
   * 释放幂等锁（空句柄或降级句柄时跳过）。
   *
   * <p>统一通过 JobLockManager（DistributedLocker）释放，仅持有者可释放。
   *
   * @param idempotentLock 幂等锁句柄
   */
  public void releaseIdempotentLock(IdempotentLockHandle idempotentLock) {
    if (idempotentLock == null || idempotentLock.key() == null || idempotentLock.key().isEmpty()) {
      return;
    }
    try {
      if (idempotentLock.value() != null && !idempotentLock.value().isEmpty()) {
        jobLockManager.releaseLock(idempotentLock.key(), idempotentLock.value());
      }
    } catch (Exception e) {
      log.warn(
          "[LockGuard] 释放幂等锁失败(将等待 TTL 自动过期): key={} reason={}",
          idempotentLock.key(),
          e.getMessage());
    }
  }

  /**
   * 安全释放锁（仅当 lockHolder 匹配时才释放）。
   *
   * <p>供 COVER 策略使用：中断旧任务线程后，按日志记录的持锁者标识释放锁。
   * 通过 JobLockManager（DistributedLocker）安全释放，仅持有者可释放。
   *
   * @param lockKey 锁 key
   * @param lockHolder 持锁者标识
   */
  public void releaseLockByValue(String lockKey, String lockHolder) {
    try {
      jobLockManager.releaseLock(lockKey, lockHolder);
    } catch (Exception e) {
      log.warn(
          "[LockGuard] 按值释放锁失败(将等待 TTL 自动过期): key={} reason={}", lockKey, e.getMessage());
    }
  }

  private static String initInstanceId() {
    String hostname = "unknown";
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (Exception e) {
      log.warn("[LockGuard] 解析主机名失败, 使用 unknown: reason={}", e.getMessage());
    }
    return hostname + ":" + ProcessHandle.current().pid();
  }
}
