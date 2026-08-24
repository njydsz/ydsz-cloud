package com.njydsz.cronjob.server.core.dispatch;

import java.time.Duration;
import java.util.Collections;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.cronjob.server.core.JobLockManager;
import com.njydsz.cronjob.server.core.LockKeyUtil;
import com.njydsz.cronjob.server.core.executor.GlobalConcurrencyController;
import com.njydsz.cronjob.server.core.executor.RunningTaskCounter;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行锁管理辅助类。
 *
 * <p>封装分布式锁、幂等锁、全局并发控制的获取与释放逻辑， 遵循云顶编码规范，将 {@link DefaultTaskDispatcher} 中的锁管理职责独立出来，
 * 降低主类复杂度，提升代码可维护性。
 *
 * <h3>职责范围</h3>
 *
 * <ul>
 *   <li>任务级分布式锁的获取与释放
 *   <li>幂等锁的获取与释放（防止相同参数重复执行）
 *   <li>全局并发控制的获取与释放
 *   <li>线程池中运行中任务数的递增/递减
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JobExecutionLockHelper {

  /** 线程池空闲保活时间（秒） */
  private static final long KEEPALIVE_SECONDS = 60;

  /** 短暂等待时间（毫秒） */
  private static final long BRIEF_SLEEP_MILLIS = 50;

  /** 中断等待超时（毫秒）：等待线程响应中断的最长时间 */
  private static final long INTERRUPT_WAIT_TIMEOUT_MS = 1000L;

  /** 中断轮询间隔（毫秒）：检查线程是否已终止的 sleep 间隔 */
  private static final long INTERRUPT_POLL_INTERVAL_MS = 10L;

  /** P0-A4: Lua 脚本统一引用 LockKeyUtil 常量，消除内联 Lua 字符串 */
  private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = initReleaseScript();

  /** P1-5: 幂等锁 key 前缀（防止相同参数的任务重复执行） */
  private static final String IDEMPOTENT_LOCK_PREFIX = "ydsz:job:idempotent:";

  /** P0-A2: 幂等锁句柄（锁 key + 持有者标识，供释放使用） */
  private record IdempotentLockHandle(String key, String value) {}

  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectProvider<JobLockManager> jobLockManagerProvider;
  private final ObjectProvider<GlobalConcurrencyController> globalConcurrencyControllerProvider;

  /**
   * 构造锁管理辅助类。
   *
   * @param redisTemplate Redis 模板
   * @param jobLockManagerProvider 任务锁管理器提供者
   * @param globalConcurrencyControllerProvider 全局并发控制器提供者
   */
  public JobExecutionLockHelper(
      RedisTemplate<String, Object> redisTemplate,
      ObjectProvider<JobLockManager> jobLockManagerProvider,
      ObjectProvider<GlobalConcurrencyController> globalConcurrencyControllerProvider) {
    this.redisTemplate = redisTemplate;
    this.jobLockManagerProvider = jobLockManagerProvider;
    this.globalConcurrencyControllerProvider = globalConcurrencyControllerProvider;
  }

  /**
   * 获取任务锁管理器（未配置时返回 null，走兼容的 RedisTemplate 路径）。
   *
   * @return 任务锁管理器；未配置时返回 null
   */
  public JobLockManager getJobLockManager() {
    return jobLockManagerProvider.getIfAvailable();
  }

  /**
   * 尝试获取任务级分布式锁。
   *
   * @param jobKey 任务 KEY
   * @param shardIndex 分片索引（null=非分片任务）
   * @param ttl 锁 TTL
   * @return 锁持有者标识；获取失败返回 null
   */
  public String tryAcquireJobLock(String jobKey, Integer shardIndex, Duration ttl) {
    String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
    JobLockManager lockManager = getJobLockManager();
    if (lockManager != null) {
      return lockManager.tryAcquireLock(jobKey, shardIndex, ttl.toMillis());
    }
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, ExecutorUtils.getInstanceId(), ttl);
    return Boolean.TRUE.equals(acquired) ? ExecutorUtils.getInstanceId() : null;
  }

  /**
   * 释放任务级分布式锁。
   *
   * @param lockKey 锁 key（为 null 时跳过）
   * @param jobKey 任务 KEY（JobLockManager 释放需要）
   * @param shardIndex 分片索引（null=非分片任务）
   * @param lockValue 锁持有者标识
   */
  public void releaseJobLock(String lockKey, String jobKey, Integer shardIndex, String lockValue) {
    releaseLockQuietly(lockKey, jobKey, shardIndex, lockValue);
  }

  /**
   * 尝试获取幂等锁。
   *
   * @param handler 任务处理器
   * @param paramsJson 任务参数 JSON
   * @param ttl 锁 TTL
   * @return 幂等锁句柄；获取失败返回 null；降级时返回空句柄
   */
  public IdempotentLockHandle acquireIdempotentLock(
      com.njydsz.cronjob.domain.job.JobHandler handler, String paramsJson, Duration ttl) {
    try {
      if (handler == null) {
        return new IdempotentLockHandle("", "");
      }
      String idempotentKey = handler.idempotentKey(paramsJson);
      if (idempotentKey == null || idempotentKey.isBlank()) {
        return new IdempotentLockHandle("", "");
      }
      String idempotentLockKey = IDEMPOTENT_LOCK_PREFIX + idempotentKey;
      String lockValue = tryAcquireIdempotentLock(idempotentLockKey, ttl);
      if (lockValue == null) {
        log.info(
            "[Dispatcher] 幂等锁被其他实例持有, 跳过重复执行: handler={}",
            handler.getClass().getSimpleName());
        return null;
      }
      return new IdempotentLockHandle(idempotentLockKey, lockValue);
    } catch (Exception e) {
      log.warn("[Dispatcher] 幂等锁获取异常, 降级放行: reason={}", e.getMessage());
      return new IdempotentLockHandle("", "");
    }
  }

  /**
   * 释放幂等锁。
   *
   * @param idempotentLock 幂等锁句柄
   */
  public void releaseIdempotentLock(IdempotentLockHandle idempotentLock) {
    if (idempotentLock == null
        || idempotentLock.key() == null
        || idempotentLock.key().isEmpty()) {
      return;
    }
    try {
      JobLockManager lockManager = getJobLockManager();
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
              : ExecutorUtils.getInstanceId());
    } catch (Exception e) {
      log.warn(
          "[Dispatcher] 释放幂等锁失败(将等待 TTL 自动过期): key={} reason={}",
          idempotentLock.key(),
          e.getMessage());
    }
  }

  /**
   * 尝试获取全局并发配额。
   *
   * @return true 获取成功或控制器不可用（降级放行）；false 全局并发已满
   */
  public boolean tryAcquireGlobalConcurrency() {
    GlobalConcurrencyController controller = globalConcurrencyControllerProvider.getIfAvailable();
    if (controller == null) {
      return true;
    }
    return controller.tryAcquire();
  }

  /**
   * 释放全局并发配额。
   */
  public void releaseGlobalConcurrency() {
    GlobalConcurrencyController controller = globalConcurrencyControllerProvider.getIfAvailable();
    if (controller == null) {
      return;
    }
    controller.release();
  }

  /**
   * 递增运行中任务数。
   *
   * @param counter 运行中任务计数器
   */
  public void incrementRunningTaskCount(RunningTaskCounter counter) {
    if (counter != null) {
      counter.increment();
    }
  }

  /**
   * 递减运行中任务数。
   *
   * @param counter 运行中任务计数器
   */
  public void decrementRunningTaskCount(RunningTaskCounter counter) {
    if (counter != null) {
      counter.decrement();
    }
  }

  /**
   * P0-2: 统一锁释放入口。
   *
   * <p>优先使用 {@link JobLockManager}（common-lock 可重入锁，仅持有者可释放，WatchDog 自动停止）；
   * JobLockManager 不可用或释放异常时回退 Lua 脚本（value 匹配删除）。
   */
  private void releaseLockQuietly(
      String lockKey, String jobKey, Integer shardIndex, String lockValue) {
    if (lockKey == null) {
      return;
    }
    JobLockManager lockManager = getJobLockManager();
    if (lockManager != null && lockValue != null) {
      try {
        lockManager.releaseLock(jobKey, shardIndex, lockValue);
        return;
      } catch (Exception e) {
        log.warn(
            "[Dispatcher] JobLockManager 释放锁失败, 回退 Lua 脚本: key={} reason={}",
            lockKey,
            e.getMessage());
      }
    }
    try {
      redisTemplate.execute(
          RELEASE_LOCK_SCRIPT,
          Collections.singletonList(lockKey),
          lockValue != null ? lockValue : ExecutorUtils.getInstanceId());
    } catch (Exception e) {
      log.warn(
          "[Dispatcher] 释放分布式锁失败(将等待 TTL 自动过期): key={} reason={}",
          lockKey,
          e.getMessage());
    }
  }

  /**
   * P1-5: 尝试获取幂等锁。
   *
   * @param idempotentLockKey 幂等锁 key
   * @param ttl 锁 TTL
   * @return 锁持有者标识；获取失败返回 null；异常降级返回空字符串（放行）
   */
  private String tryAcquireIdempotentLock(String idempotentLockKey, Duration ttl) {
    if (idempotentLockKey == null) {
      return "";
    }
    try {
      JobLockManager lockManager = getJobLockManager();
      if (lockManager != null) {
        return lockManager.tryAcquireLock(idempotentLockKey, ttl.toMillis());
      }
      Boolean acquired =
          redisTemplate.opsForValue().setIfAbsent(idempotentLockKey, ExecutorUtils.getInstanceId(), ttl);
      return Boolean.TRUE.equals(acquired) ? ExecutorUtils.getInstanceId() : null;
    } catch (Exception e) {
      log.warn("[Dispatcher] 获取幂等锁异常, 降级放行: key={} reason={}", idempotentLockKey, e.getMessage());
      return "";
    }
  }

  private static DefaultRedisScript<Long> initReleaseScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText(LockKeyUtil.RELEASE_LOCK_SCRIPT);
    script.setResultType(Long.class);
    return script;
  }
}
