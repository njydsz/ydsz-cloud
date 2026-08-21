package com.njydsz.common.lock.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;

import com.njydsz.common.lock.core.DistributedLocker;

/**
 * Redis 多Key联锁实现 - 支持同时获取多个锁，原子性保证
 *
 * <p>注意：本实现直接实现 {@link DistributedLocker} 接口（不继承 {@link
 * com.njydsz.common.lock.core.AbstractRedisDistributedLock}）， 因为多锁场景下续期逻辑需要直接操作 Redis 而非委托给子锁。
 *
 * <p>用于需要同时锁定多个资源的场景（如：跨多实体的事务性操作）。 所有锁必须全部获取成功才算成功，否则回滚已获取的所有锁，避免死锁。
 *
 * <p><b>核心原理：</b>
 *
 * <ul>
 *   <li>按固定顺序依次获取每个锁，避免死锁
 *   <li>任何一把锁获取失败时，立即回滚已获取的所有锁
 *   <li>解锁时按相反顺序释放锁
 *   <li>支持对所有子锁统一续期（续期间隔与次数上限可配置）
 * </ul>
 *
 * <p><b>适用场景：</b>
 *
 * <ul>
 *   <li>需要同时修改多个关联资源的场景
 *   <li>跨多个业务实体的原子性操作
 *   <li>防止多资源操作中的部分成功/部分失败问题
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RedisMultiLock implements DistributedLocker {

  /** 子锁值拼接分隔符（使用不可打印 SOH 字符，避免与 lockValue 内容冲突） */
  static final String VALUE_DELIMITER = "\u0001";

  /** 剩余时间错误码（键不存在或获取失败） */
  private static final long REMAIN_TIME_ERROR = -2L;

  /**
   * 安全续期 Lua 脚本：仅当 key 的当前值等于持有者 value 时刷新过期时间。
   *
   * <p>返回值 1 表示续期成功，0 表示 value 不匹配（锁已被抢占），nil 表示 key 不存在。
   */
  private static final DefaultRedisScript<Long> RENEW_SCRIPT;

  static {
    RENEW_SCRIPT = new DefaultRedisScript<>();
    RENEW_SCRIPT.setScriptText(
        "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('pexpire', KEYS[1], ARGV[2]) "
            + "else return 0 end");
    RENEW_SCRIPT.setResultType(Long.class);
  }

  /** 默认最大续期次数（约 10 分钟） */
  private static final int DEFAULT_MAX_RENEW_COUNT = 30;

  /** 默认续期间隔（秒） */
  private static final long DEFAULT_RENEW_INTERVAL_SECONDS = 10;

  /** WatchDog 续期调度器（由 Spring 管理，支持优雅停机） */
  private final TaskScheduler renewalScheduler;

  /** 最大续期次数，超过后停止续期，锁自动过期 */
  private final int maxRenewCount;

  /** 续期间隔（秒） */
  private final long renewIntervalSeconds;

  /** Redis 操作模板（用于续期操作） */
  private final StringRedisTemplate stringRedisTemplate;

  /**
   * 构造多Key联锁（使用默认续期配置）
   *
   * @param stringRedisTemplate Redis 操作模板
   * @param locks 底层分布式锁列表，至少需要 2 个锁
   * @param renewalScheduler 续期调度器（由 Spring 管理）
   */
  public RedisMultiLock(
      StringRedisTemplate stringRedisTemplate,
      List<DistributedLocker> locks,
      TaskScheduler renewalScheduler) {
    this(
        stringRedisTemplate,
        locks,
        renewalScheduler,
        DEFAULT_MAX_RENEW_COUNT,
        DEFAULT_RENEW_INTERVAL_SECONDS);
  }

  /**
   * 构造多Key联锁（可配置续期参数）
   *
   * @param stringRedisTemplate Redis 操作模板
   * @param locks 底层分布式锁列表，至少需要 2 个锁
   * @param renewalScheduler 续期调度器（由 Spring 管理）
   * @param maxRenewCount 最大续期次数
   * @param renewIntervalSeconds 续期间隔（秒）
   */
  public RedisMultiLock(
      StringRedisTemplate stringRedisTemplate,
      List<DistributedLocker> locks,
      TaskScheduler renewalScheduler,
      int maxRenewCount,
      long renewIntervalSeconds) {
    if (locks == null || locks.size() < 2) {
      throw new IllegalArgumentException("RedisMultiLock 至少需要 2 个底层锁");
    }
    this.stringRedisTemplate = stringRedisTemplate;
    this.locks = Collections.unmodifiableList(new ArrayList<>(locks));
    this.renewalScheduler = renewalScheduler;
    this.maxRenewCount = maxRenewCount > 0 ? maxRenewCount : DEFAULT_MAX_RENEW_COUNT;
    this.renewIntervalSeconds =
        renewIntervalSeconds > 0 ? renewIntervalSeconds : DEFAULT_RENEW_INTERVAL_SECONDS;
  }

  /** 每个实例的续期任务映射（lockKey → ScheduledFuture） */
  private final Map<String, ScheduledFuture<?>> renewalFutures = new ConcurrentHashMap<>();

  /** 底层分布式锁列表（按获取顺序） */
  private final List<DistributedLocker> locks;

  /** 当前持有的锁值映射（lockKey → lockValue） */
  private final Map<String, String> acquiredLockValues = new ConcurrentHashMap<>();

  /** WatchDog 续期状态 */
  private final AtomicBoolean renewing = new AtomicBoolean(false);

  /** 已续期次数 */
  private final Map<String, Integer> renewCounts = new ConcurrentHashMap<>();

  /**
   * 尝试获取多Key联锁（非阻塞）
   *
   * <p>依次获取每个子锁，任一失败则回滚所有已获取的锁。
   *
   * @param lockKey 锁的键（此参数在多锁场景下仅作日志标识，实际使用各子锁的键）
   * @param leaseTime 租约时间
   * @param timeUnit 时间单位
   * @return 复合锁值（各子锁值拼接），获取成功返回非 null
   */
  @Override
  public String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
    List<String> acquired = new ArrayList<>(locks.size());
    try {
      for (int i = 0; i < locks.size(); i++) {
        DistributedLocker lock = locks.get(i);
        String subLockKey = buildSubLockKey(lockKey, i);
        String lockValue = lock.tryLock(subLockKey, leaseTime, timeUnit);
        if (lockValue == null) {
          log.debug("RedisMultiLock 获取子锁失败, key={}, index={}", subLockKey, i);
          return null;
        }
        acquired.add(lockValue);
        acquiredLockValues.put(subLockKey, lockValue);
      }

      // 全部获取成功
      String compositeValue = String.join(VALUE_DELIMITER, acquired);
      startWatchDog(lockKey, leaseTime, timeUnit);
      log.debug("RedisMultiLock 获取成功, key={}, lockCount={}", lockKey, locks.size());
      return compositeValue;
    } catch (Exception e) {
      log.error("RedisMultiLock 获取锁异常, key={}: {}", lockKey, e.getMessage(), e);
      // 异常时也回滚已获取的锁
      rollbackLocks(lockKey, acquired.size());
      return null;
    }
  }

  /**
   * 尝试获取多Key联锁（带等待时间）
   *
   * @param lockKey 锁的键
   * @param waitTime 最大等待时间
   * @param leaseTime 租约时间
   * @param timeUnit 时间单位
   * @return 复合锁值，获取成功返回非 null
   * @throws InterruptedException 等待过程中线程被中断
   */
  @Override
  public String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit)
      throws InterruptedException {
    long startTime = System.currentTimeMillis();
    long deadline = startTime + timeUnit.toMillis(waitTime);

    List<String> acquired = new ArrayList<>(locks.size());
    try {
      for (int i = 0; i < locks.size(); i++) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
          rollbackLocks(lockKey, acquired.size());
          return null;
        }

        DistributedLocker lock = locks.get(i);
        String subLockKey = buildSubLockKey(lockKey, i);
        String lockValue = lock.tryLock(subLockKey, remaining, leaseTime, timeUnit);
        if (lockValue == null) {
          log.debug("RedisMultiLock 等待获取子锁超时, key={}, index={}", subLockKey, i);
          rollbackLocks(lockKey, acquired.size());
          return null;
        }
        acquired.add(lockValue);
        acquiredLockValues.put(subLockKey, lockValue);
      }

      String compositeValue = String.join(VALUE_DELIMITER, acquired);
      startWatchDog(lockKey, leaseTime, timeUnit);
      log.debug("RedisMultiLock 获取成功, key={}, lockCount={}", lockKey, locks.size());
      return compositeValue;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      rollbackLocks(lockKey, acquired.size());
      throw e;
    }
  }

  /**
   * 释放多Key联锁
   *
   * <p>按相反顺序释放每个子锁，确保资源安全释放。
   *
   * @param lockKey 锁的键
   * @param lockValue 复合锁值
   * @return true-全部释放成功，false-部分释放失败
   */
  @Override
  public boolean unlock(String lockKey, String lockValue) {
    stopWatchDog(lockKey);
    return releaseAllLocks(lockKey);
  }

  /**
   * 检查多Key联锁是否被持有
   *
   * @param lockKey 锁的键
   * @return true-所有子锁都被持有
   */
  @Override
  public boolean isLocked(String lockKey) {
    for (int i = 0; i < locks.size(); i++) {
      DistributedLocker lock = locks.get(i);
      String subLockKey = buildSubLockKey(lockKey, i);
      if (!lock.isLocked(subLockKey)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 获取多Key联锁的剩余有效时间
   *
   * <p>取所有子锁中最小的剩余时间作为结果。
   *
   * @param lockKey 锁的键
   * @return 最小剩余时间（毫秒）
   */
  @Override
  public long getRemainTime(String lockKey) {
    long minRemain = Long.MAX_VALUE;
    for (int i = 0; i < locks.size(); i++) {
      DistributedLocker lock = locks.get(i);
      String subLockKey = buildSubLockKey(lockKey, i);
      long remain = lock.getRemainTime(subLockKey);
      if (remain > 0 && remain < minRemain) {
        minRemain = remain;
      }
    }
    return minRemain == Long.MAX_VALUE ? REMAIN_TIME_ERROR : minRemain;
  }

  /**
   * 获取底层锁的数量
   *
   * @return 锁数量
   */
  public int getLockCount() {
    return locks.size();
  }

  /**
   * 获取指定索引位置的底层锁
   *
   * @param index 索引
   * @return 底层分布式锁
   */
  public DistributedLocker getLock(int index) {
    return locks.get(index);
  }

  // ==================== 内部方法 ====================

  /**
   * 构建子锁键
   *
   * @param lockKey 主锁键
   * @param index 子锁索引
   * @return 子锁键
   */
  protected String buildSubLockKey(String lockKey, int index) {
    return lockKey + ":multi:" + index;
  }

  /**
   * 回滚已获取的锁
   *
   * @param lockKey 主锁键
   * @param acquiredCount 已获取的锁数量
   */
  private void rollbackLocks(String lockKey, int acquiredCount) {
    for (int i = acquiredCount - 1; i >= 0; i--) {
      try {
        DistributedLocker lock = locks.get(i);
        String subLockKey = buildSubLockKey(lockKey, i);
        String lockValue = acquiredLockValues.remove(subLockKey);
        if (lockValue != null) {
          lock.unlock(subLockKey, lockValue);
        }
      } catch (Exception e) {
        log.warn("RedisMultiLock 回滚子锁失败, index={}: {}", i, e.getMessage());
      }
    }
  }

  /**
   * 释放所有子锁（逆序释放）
   *
   * @param lockKey 主锁键
   * @return true-全部释放成功
   */
  private boolean releaseAllLocks(String lockKey) {
    boolean allReleased = true;
    for (int i = locks.size() - 1; i >= 0; i--) {
      try {
        DistributedLocker lock = locks.get(i);
        String subLockKey = buildSubLockKey(lockKey, i);
        String lockValue = acquiredLockValues.remove(subLockKey);
        if (lockValue != null) {
          if (!lock.unlock(subLockKey, lockValue)) {
            allReleased = false;
            log.warn("RedisMultiLock 释放子锁失败, index={}", i);
          }
        }
      } catch (Exception e) {
        allReleased = false;
        log.warn("RedisMultiLock 释放子锁异常, index={}: {}", i, e.getMessage());
      }
    }
    return allReleased;
  }

  /**
   * 启动 WatchDog 自动续期
   *
   * <p>对所有子锁统一进行续期操作，续期间隔与次数上限来自配置 （{@code ydsz.lock.multi-lock.renew-interval-seconds /
   * max-renew-count}）。
   *
   * @param lockKey 主锁键
   * @param leaseTime 租约时间
   * @param timeUnit 时间单位
   */
  private void startWatchDog(String lockKey, long leaseTime, TimeUnit timeUnit) {
    long renewIntervalMillis = renewIntervalSeconds * 1000L;
    renewing.set(true);
    renewCounts.put(lockKey, 0);

    ScheduledFuture<?> future =
        renewalScheduler.scheduleAtFixedRate(
            () -> {
              if (!renewing.get()) {
                return;
              }
              int renewedTimes = renewCounts.getOrDefault(lockKey, 0);
              if (renewedTimes >= maxRenewCount) {
                log.warn("RedisMultiLock 续期次数超过最大限制（{}次），停止续期, key={}", maxRenewCount, lockKey);
                stopWatchDog(lockKey);
                return;
              }
              try {
                boolean renewed = renewAllLocks(lockKey, leaseTime, timeUnit);
                if (renewed) {
                  renewCounts.put(lockKey, renewedTimes + 1);
                  log.debug("RedisMultiLock WatchDog 续期成功, key={}", lockKey);
                } else {
                  log.warn("RedisMultiLock WatchDog 续期失败，停止续期, key={}", lockKey);
                  stopWatchDog(lockKey);
                }
              } catch (Exception e) {
                log.error("RedisMultiLock WatchDog 续期异常, key={}", lockKey, e);
                stopWatchDog(lockKey);
              }
            },
            Duration.ofMillis(renewIntervalMillis));

    renewalFutures.put(lockKey, future);
  }

  /**
   * 停止 WatchDog 续期
   *
   * @param lockKey 主锁键
   */
  private void stopWatchDog(String lockKey) {
    if (renewing.compareAndSet(true, false)) {
      ScheduledFuture<?> future = renewalFutures.remove(lockKey);
      if (future != null) {
        future.cancel(false);
      }
      renewCounts.remove(lockKey);
      log.debug("RedisMultiLock WatchDog 已停止, key={}", lockKey);
    }
  }

  /**
   * 续期所有子锁
   *
   * @param lockKey 主锁键
   * @param leaseTime 租约时间
   * @param timeUnit 时间单位
   * @return true-全部续期成功
   */
  /**
   * 续期所有子锁。
   *
   * <p>通过 Lua 脚本原子校验锁持有者后续期（安全续期）：仅当 key 的当前值等于 本锁持有的 value 时才刷新过期时间， 避免续期到被抢占后的其他锁
   * （裸 {@code EXPIRE} 无持有者校验的隐患）。
   *
   * @param lockKey 锁 key
   * @param leaseTime 租约时长
   * @param timeUnit 时间单位
   * @return 全部续期成功返回 true；任一失败返回 false
   */
  private boolean renewAllLocks(String lockKey, long leaseTime, TimeUnit timeUnit) {
    long leaseTimeMs = timeUnit.toMillis(leaseTime);
    for (int i = 0; i < locks.size(); i++) {
      String subLockKey = buildSubLockKey(lockKey, i);
      String lockValue = acquiredLockValues.get(subLockKey);
      if (lockValue == null) {
        return false;
      }
      try {
        Long renewed = stringRedisTemplate.execute(RENEW_SCRIPT, List.of(subLockKey), lockValue, leaseTimeMs);
        if (!Long.valueOf(1L).equals(renewed)) {
          return false;
        }
      } catch (Exception e) {
        return false;
      }
    }
    return true;
  }
}
