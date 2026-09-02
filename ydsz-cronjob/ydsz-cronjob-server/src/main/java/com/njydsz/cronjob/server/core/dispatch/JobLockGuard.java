package com.njydsz.cronjob.server.core.dispatch;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
 *   <li><b>任务锁</b>：按 jobKey（+分片索引）粒度去重，防止同一任务并发执行；优先使用
 *       {@link JobLockManager}（common-lock，WatchDog 续期 + 可重入），未配置时回退 Redis SETNX
 *   <li><b>幂等锁</b>：按 handler + params 粒度去重，防止相同参数的任务在集群中重复执行
 * </ul>
 *
 * <p>释放统一走 {@link JobLockManager#releaseLock}，失败或不可用时回退 Lua 脚本
 * （value 相等判断，兼容 SETNX 与 common-lock 两种获取路径）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class JobLockGuard {

  /** 本节点实例标识（hostname:pid，锁持有者兜底标识） */
  public static final String INSTANCE_ID = initInstanceId();

  /** Lua 脚本：安全释放锁（value 相等才删除，防止误删他人锁） */
  private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = initReleaseScript();

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
  private final ObjectProvider<JobLockManager> jobLockManagerProvider;
  private final RedisTemplate<String, Object> redisTemplate;

  /**
   * 构造任务锁守卫。
   *
   * @param cronjobProperties 调度配置（TTL 规整）
   * @param jobLockManagerProvider 分布式锁管理器（可选，未配置时回退 Redis SETNX）
   * @param redisTemplate Redis 客户端（SETNX 降级路径）
   */
  public JobLockGuard(
      CronjobProperties cronjobProperties,
      ObjectProvider<JobLockManager> jobLockManagerProvider,
      RedisTemplate<String, Object> redisTemplate) {
    this.cronjobProperties = cronjobProperties;
    this.jobLockManagerProvider = jobLockManagerProvider;
    this.redisTemplate = redisTemplate;
  }

  /**
   * 获取任务锁管理器（未配置时返回 null，走兼容的 RedisTemplate 路径）。
   *
   * @return 锁管理器或 null
   */
  public JobLockManager jobLockManager() {
    return jobLockManagerProvider.getIfAvailable();
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
    JobLockManager lockManager = jobLockManager();
    String lockValue;
    if (lockManager != null) {
      // common-lock：WatchDog 续期 + 可重入
      lockValue =
          shardIndex == null
              ? lockManager.tryAcquireLock(job.getJobKey(), null, ttl.toMillis())
              : lockManager.tryAcquireLock(job.getJobKey(), shardIndex, ttl.toMillis());
    } else {
      // 兼容路径：Redis SETNX
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, INSTANCE_ID, ttl);
      lockValue = Boolean.TRUE.equals(acquired) ? INSTANCE_ID : null;
    }
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
   * @param lockKey 锁 key（null 时跳过）
   * @param jobKey 任务 KEY（JobLockManager 释放需要）
   * @param shardIndex 分片索引（null = 非分片任务）
   * @param lockValue 锁持有者标识（可能为 null，兜底使用 INSTANCE_ID）
   */
  public void releaseJobLock(String lockKey, String jobKey, Integer shardIndex, String lockValue) {
    if (lockKey == null) {
      return;
    }
    JobLockManager lockManager = jobLockManager();
    if (lockManager != null && lockValue != null) {
      try {
        lockManager.releaseLock(jobKey, shardIndex, lockValue);
        return;
      } catch (Exception e) {
        log.warn(
            "[LockGuard] JobLockManager 释放锁失败, 回退 Lua 脚本: key={} reason={}",
            lockKey,
            e.getMessage());
      }
    }
    try {
      redisTemplate.execute(
          RELEASE_LOCK_SCRIPT,
          Collections.singletonList(lockKey),
          lockValue != null ? lockValue : INSTANCE_ID);
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
      JobLockManager lockManager = jobLockManager();
      if (lockManager != null) {
        return lockManager.tryAcquireLock(idempotentLockKey, ttl.toMillis());
      }
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(idempotentLockKey, INSTANCE_ID, ttl);
      return Boolean.TRUE.equals(acquired) ? INSTANCE_ID : null;
    } catch (Exception e) {
      log.warn("[LockGuard] 获取幂等锁异常, 降级放行: key={} reason={}", idempotentLockKey, e.getMessage());
      return "";
    }
  }

  /**
   * 释放幂等锁（空句柄或降级句柄时跳过）。
   *
   * @param idempotentLock 幂等锁句柄
   */
  public void releaseIdempotentLock(IdempotentLockHandle idempotentLock) {
    if (idempotentLock == null || idempotentLock.key() == null || idempotentLock.key().isEmpty()) {
      return;
    }
    try {
      JobLockManager lockManager = jobLockManager();
      if (lockManager != null
          && idempotentLock.value() != null
          && !idempotentLock.value().isEmpty()) {
        lockManager.releaseLock(idempotentLock.key(), idempotentLock.value());
        return;
      }
      redisTemplate.execute(
          RELEASE_LOCK_SCRIPT,
          Collections.singletonList(idempotentLock.key()),
          idempotentLock.value() != null && !idempotentLock.value().isEmpty()
              ? idempotentLock.value()
              : INSTANCE_ID);
    } catch (Exception e) {
      log.warn(
          "[LockGuard] 释放幂等锁失败(将等待 TTL 自动过期): key={} reason={}",
          idempotentLock.key(),
          e.getMessage());
    }
  }

  /**
   * 通过 Lua 脚本安全释放锁（仅当 lockHolder 匹配时才删除）。
   *
   * <p>供 COVER 策略使用：中断旧任务线程后，按日志记录的持锁者标识释放锁。
   *
   * @param lockKey 锁 key
   * @param lockHolder 持锁者标识
   */
  public void releaseLockByValue(String lockKey, String lockHolder) {
    try {
      redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), lockHolder);
    } catch (Exception e) {
      log.warn(
          "[LockGuard] 按值释放锁失败(将等待 TTL 自动过期): key={} reason={}", lockKey, e.getMessage());
    }
  }

  private static DefaultRedisScript<Long> initReleaseScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(LockKeyUtil.RELEASE_LOCK_SCRIPT);
    script.setResultType(Long.class);
    return script;
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
