package com.njydsz.common.lock.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.scheduler.LockWatchDog;

/**
 * Redis 多Key联锁实现 - 支持同时锁定多个资源，原子性保证
 *
 * <p>注意：本实现直接实现 {@link DistributedLocker} 接口（不继承 {@link
 * com.njydsz.common.lock.core.AbstractRedisDistributedLock}）， 因为多锁场景下续期逻辑需要独立管理各子锁的续期状态。
 *
 * <p>用于需要同时锁定多个资源的场景（如：跨多实体的事务性操作）。 所有锁必须全部获取成功才算成功，否则回滚已获取的所有锁，避免死锁。
 *
 * <p><b>核心原理：</b>
 *
 * <ul>
 *   <li>按固定顺序依次获取每个锁，避免死锁
 *   <li>任何一把锁获取失败时，立即回滚已获取的所有锁
 *   <li>解锁时按相反顺序释放锁
 *   <li>续期委托给 {@link LockWatchDog} 统一管理（P2-A2 改进：消除自建 TaskScheduler 续期线程）
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
 * @since 26.09.01
 */
@Slf4j
public class RedisMultiLock implements DistributedLocker {

  /** 子锁值拼接分隔符（使用不可打印 SOH 字符，避免与 lockValue 内容冲突） */
  static final String VALUE_DELIMITER = "";

  /** 剩余时间错误码（键不存在或获取失败） */
  private static final long REMAIN_TIME_ERROR = -2L;

  /** Redis 操作模板（用于续期校验） */
  private final StringRedisTemplate stringRedisTemplate;

  /** WatchDog 续期管理器（由 Spring 管理，统一处理所有锁类型的续期） */
  private final LockWatchDog lockWatchDog;

  /** 底层分布式锁列表（按获取顺序） */
  private final List<DistributedLocker> locks;

  /** 当前持有的锁值映射（compositeKey → 子锁值） */
  private final Map<String, String> acquiredLockValues = new ConcurrentHashMap<>();

  /**
   * 构造多Key联锁（使用统一 WatchDog 续期，P2-A2 改进）。
   *
   * @param stringRedisTemplate Redis 操作模板
   * @param locks 底层分布式锁列表，至少需要 2 个锁
   * @param lockWatchDog 续期看门狗（由框架统一管理）
   */
  public RedisMultiLock(
      StringRedisTemplate stringRedisTemplate,
      List<DistributedLocker> locks,
      LockWatchDog lockWatchDog) {
    if (locks == null || locks.size() < 2) {
      throw new IllegalArgumentException("RedisMultiLock 至少需要 2 个底层锁");
    }
    this.stringRedisTemplate = stringRedisTemplate;
    this.locks = Collections.unmodifiableList(new ArrayList<>(locks));
    this.lockWatchDog = lockWatchDog;
  }

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
          log.debug("[ydsz-lock] [multi] 获取子锁失败 key={} index={}", subLockKey, i);
          return null;
        }
        acquired.add(lockValue);
        acquiredLockValues.put(subLockKey, lockValue);
      }

      // 全部获取成功，注册到统一 WatchDog 续期
      String compositeValue = String.join(VALUE_DELIMITER, acquired);
      startWatchDogRenewal(lockKey, leaseTime, timeUnit);
      log.debug("[ydsz-lock] [multi] 获取成功 key={} lockCount={}", lockKey, locks.size());
      return compositeValue;
    } catch (Exception e) {
      log.error("[ydsz-lock] [multi] 获取锁异常 key={} cause={}", lockKey, e.getMessage(), e);
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
          log.debug("[ydsz-lock] [multi] 等待子锁超时 key={} index={}", subLockKey, i);
          rollbackLocks(lockKey, acquired.size());
          return null;
        }
        acquired.add(lockValue);
        acquiredLockValues.put(subLockKey, lockValue);
      }

      String compositeValue = String.join(VALUE_DELIMITER, acquired);
      startWatchDogRenewal(lockKey, leaseTime, timeUnit);
      log.debug("[ydsz-lock] [multi] 获取成功 key={} lockCount={}", lockKey, locks.size());
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
   * <p>停止 WatchDog 续期后按相反顺序释放每个子锁，确保资源安全释放。
   *
   * @param lockKey 锁的键
   * @param lockValue 复合锁值
   * @return true-全部释放成功，false-部分释放失败
   */
  @Override
  public boolean unlock(String lockKey, String lockValue) {
    stopWatchDogRenewal(lockKey);
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
        log.warn("[ydsz-lock] [multi] 回滚子锁失败 index={} cause={}", i, e.getMessage());
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
            log.warn("[ydsz-lock] [multi] 释放子锁失败 index={}", i);
          }
        }
      } catch (Exception e) {
        allReleased = false;
        log.warn("[ydsz-lock] [multi] 释放子锁异常 index={} cause={}", i, e.getMessage());
      }
    }
    return allReleased;
  }

  /**
   * 启动统一 WatchDog 续期（P2-A2 改进：委托给 LockWatchDog 而非自建 TaskScheduler）。
   *
   * <p>遍历所有已获取的子锁，将每个子锁键注册到 {@link LockWatchDog}。 WatchDog 按租约时间的 1/3 间隔自动续期每个子锁。
   *
   * <p>如果 WatchDog 不可用（未配置），则跳过续期注册，依赖锁的 TTL 自动过期释放。
   *
   * @param lockKey 主锁键
   * @param leaseTime 租约时间
   * @param timeUnit 时间单位
   */
  private void startWatchDogRenewal(String lockKey, long leaseTime, TimeUnit timeUnit) {
    if (lockWatchDog == null) {
      log.info(
          "[ydsz-lock] [multi] WatchDog 不可用，多锁续期将依赖 TTL 自动释放 key={}", lockKey);
      return;
    }
    long leaseTimeMs = timeUnit.toMillis(leaseTime);
    for (int i = 0; i < locks.size(); i++) {
      String subLockKey = buildSubLockKey(lockKey, i);
      String lockValue = acquiredLockValues.get(subLockKey);
      if (lockValue != null) {
        lockWatchDog.startWatch(subLockKey, lockValue, leaseTimeMs, LockType.REENTRANT);
        log.debug("[ydsz-lock] [multi] 子锁注册续期 key={} index={}", subLockKey, i);
      }
    }
  }

  /**
   * 停止统一 WatchDog 续期（P2-A2 改进）。
   *
   * @param lockKey 主锁键
   */
  private void stopWatchDogRenewal(String lockKey) {
    if (lockWatchDog == null) {
      return;
    }
    for (int i = 0; i < locks.size(); i++) {
      String subLockKey = buildSubLockKey(lockKey, i);
      lockWatchDog.stopWatch(subLockKey);
    }
    log.debug("[ydsz-lock] [multi] 子锁续期已停止 key={}", lockKey);
  }
}
