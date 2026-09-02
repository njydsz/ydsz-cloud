package com.njydsz.common.lock.core;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.notify.LockReleaseNotifier;
import com.njydsz.common.lock.scheduler.LockWatchDog;
import com.njydsz.common.lock.util.BackoffPolicy;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 抽象 Redis 分布式锁基类
 *
 * <p>提供分布式锁的公共能力：
 *
 * <ul>
 *   <li>客户端标识生成与管理（线程级 UUID，本地缓存，线程池安全）
 *   <li>锁超时时间记录与管理
 *   <li>WatchDog 自动续期机制集成
 *   <li>等待重试退避策略（可配合 {@link LockReleaseNotifier} 释放通知避免空轮询）
 * </ul>
 *
 * <p><b>设计要点：</b> 客户端标识使用 {@code IdGenerator + threadId} 生成，通过本地缓存（key 为 {@code
 * threadId:lockKey}）管理， 确保线程池环境下 clientId 不会因线程复用而混乱，且不依赖 Redis 额外往返。
 *
 * <p><b>内存安全：</b>使用 Caffeine 缓存替代 ThreadLocal，通过 TTL（30 分钟）和最大容量（10000）自动清理， 彻底避免线程池复用场景下的
 * ThreadLocal 内存泄漏。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public abstract class AbstractRedisDistributedLock implements DistributedLocker {

  /** 释放锁 Lua 脚本 */
  private static final String RELEASE_LOCK_LUA_SCRIPT =
      "if redis.call('get', KEYS[1]) == ARGV[1] then "
          + "    return redis.call('del', KEYS[1]) "
          + "else "
          + "    return 0 "
          + "end";

  /** 客户端标识缓存 TTL（分钟） */
  private static final int CACHE_TTL_MINUTES = 30;

  /** 剩余时间错误码（键不存在或获取失败） */
  protected static final long REMAIN_TIME_ERROR = -2L;

  /** 退避策略实例 */
  private static final BackoffPolicy BACKOFF_POLICY = new BackoffPolicy();

  /** Redis 操作模板 */
  protected final StringRedisTemplate stringRedisTemplate;

  /** 锁续期看门狗 */
  private LockWatchDog lockWatchDog;

  /** 锁指标收集器 */
  private LockMetrics lockMetrics;

  /** 锁释放通知器（可选，未配置时退化为退避轮询） */
  private LockReleaseNotifier lockReleaseNotifier;

  /** 锁事件监听器（可选，用于感知锁生命周期事件） */
  private LockEventListener lockEventListener = LockEventListener.NO_OP;

  /** 锁等待时间策略（可选，动态调整等待超时） */
  private LockWaitTimePolicy lockWaitTimePolicy;

  /**
   * 本地缓存 clientId，线程级（key 为 {@code threadId:lockKey}）
   *
   * <p>使用 ydsz-common-cache 替代 ThreadLocal，通过 TTL 和最大容量自动清理， 彻底避免线程池复用场景下的内存泄漏。
   */
  private final Cache<String, String> clientIdCache =
      YdszCache.<String, String>newBuilder()
          .type(CacheType.STRIPED)
          .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
          .maximumSize(10_000)
          .build();

  /**
   * 锁租约时间缓存，key 为 {@code threadId:lockKey}，value 为租约时间（毫秒）
   *
   * <p>使用 ydsz-common-cache 替代 ThreadLocal，通过 TTL 和最大容量自动清理。
   */
  private final Cache<String, Long> leaseTimeCache =
      YdszCache.<String, Long>newBuilder()
          .type(CacheType.STRIPED)
          .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
          .maximumSize(10_000)
          .build();

  private final DefaultRedisScript<Long> releaseLockScript;

  /** 锁键命名空间前缀，用于多应用共享 Redis 时的隔离 */
  private final String keyNamespace;

  protected AbstractRedisDistributedLock(StringRedisTemplate stringRedisTemplate) {
    this(stringRedisTemplate, null);
  }

  protected AbstractRedisDistributedLock(
      StringRedisTemplate stringRedisTemplate, String namespace) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.keyNamespace = (namespace != null && !namespace.isEmpty()) ? namespace : null;
    this.releaseLockScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA_SCRIPT, Long.class);
  }

  /**
   * 对锁键添加应用命名空间前缀
   *
   * <p>当配置了 {@code ydsz.lock.namespace} 时，锁键自动变为： {@code ${namespace}:lock:${userKey}}，用于多应用共享
   * Redis 时的隔离。
   *
   * @param userKey 用户传入的锁键
   * @return 带命名空间前缀的锁键
   */
  protected String buildNamespacedKey(String userKey) {
    if (keyNamespace == null || keyNamespace.isEmpty()) {
      return userKey;
    }
    return keyNamespace + ":lock:" + userKey;
  }

  /**
   * 设置锁续期看门狗
   *
   * @param lockWatchDog 看门狗实例
   */
  public void setLockWatchDog(LockWatchDog lockWatchDog) {
    this.lockWatchDog = lockWatchDog;
  }

  /**
   * 设置锁指标收集器
   *
   * @param lockMetrics 指标收集器实例
   */
  public void setLockMetrics(LockMetrics lockMetrics) {
    this.lockMetrics = lockMetrics;
  }

  /**
   * 设置锁释放通知器（可选）
   *
   * <p>未配置时等待路径退化为指数退避轮询。
   *
   * @param lockReleaseNotifier 释放通知器实例
   */
  public void setLockReleaseNotifier(LockReleaseNotifier lockReleaseNotifier) {
    this.lockReleaseNotifier = lockReleaseNotifier;
  }

  /**
   * 设置锁事件监听器（可选）
   *
   * <p>配置后，锁生命周期事件（获取、释放、超时、续期失败）将通知监听器。
   *
   * @param lockEventListener 锁事件监听器实例
   */
  public void setLockEventListener(LockEventListener lockEventListener) {
    this.lockEventListener =
        lockEventListener != null ? lockEventListener : LockEventListener.NO_OP;
  }

  /**
   * 设置锁等待时间策略（可选）
   *
   * <p>配置后，每次带等待的锁获取会先通过策略动态调整等待时间， 根据历史统计数据决定最优等待时长。
   *
   * @param lockWaitTimePolicy 等待时间策略实例
   */
  public void setLockWaitTimePolicy(LockWaitTimePolicy lockWaitTimePolicy) {
    this.lockWaitTimePolicy = lockWaitTimePolicy;
  }

  /**
   * 获取锁指标收集器
   *
   * <p>供子类覆写 {@link #tryAcquireOnce} 时记录活跃锁数等指标。
   *
   * @return 指标收集器实例，未设置时返回 null
   */
  protected LockMetrics getLockMetrics() {
    return lockMetrics;
  }

  /**
   * 获取锁续期看门狗
   *
   * @return 看门狗实例，未设置时返回 null
   */
  public LockWatchDog getLockWatchDog() {
    return lockWatchDog;
  }

  /**
   * 获取或生成客户端标识
   *
   * <p>采用 {@code IdGenerator + threadId} 本地生成并缓存，不依赖 Redis 注册表：
   *
   * <ul>
   *   <li>标识按 {@code threadId:lockKey} 缓存，线程池环境下互不干扰
   *   <li>避免原注册表方案在跨 JVM 共享 Redis 时线程号碰撞导致 clientId 复用
   *   <li>省去每次加锁/解锁的 Redis 往返开销
   * </ul>
   *
   * @param lockKey 锁的键
   * @return 客户端标识
   */
  protected String getClientId(String lockKey) {
    String cacheKey = buildCacheKey(lockKey);
    String cached = clientIdCache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }
    String newClientId = IdGenerator.nextIdStr() + ":" + Thread.currentThread().threadId();
    clientIdCache.put(cacheKey, newClientId);
    return newClientId;
  }

  /**
   * 清理本地缓存的 clientId
   *
   * @param lockKey 锁的键
   */
  protected void clearClientId(String lockKey) {
    clientIdCache.invalidate(buildCacheKey(lockKey));
  }

  /**
   * 记录锁的租约时间
   *
   * @param lockKey 锁的键
   * @param leaseTimeMs 租约时间（毫秒）
   */
  protected void recordLeaseTime(String lockKey, long leaseTimeMs) {
    leaseTimeCache.put(buildCacheKey(lockKey), leaseTimeMs);
  }

  /**
   * 获取记录的租约时间
   *
   * @param lockKey 锁的键
   * @return 租约时间（毫秒），未记录返回 null
   */
  protected Long getLeaseTime(String lockKey) {
    return leaseTimeCache.getIfPresent(buildCacheKey(lockKey));
  }

  /**
   * 清理记录的租约时间
   *
   * @param lockKey 锁的键
   */
  protected void clearLeaseTime(String lockKey) {
    leaseTimeCache.invalidate(buildCacheKey(lockKey));
  }

  /**
   * 检查是否已有 clientId
   *
   * @param lockKey 锁的键
   * @return true-已存在
   */
  protected boolean hasClientId(String lockKey) {
    return clientIdCache.getIfPresent(buildCacheKey(lockKey)) != null;
  }

  /**
   * 启动 WatchDog 自动续期
   *
   * @param lockKey 锁的键
   * @param clientId 客户端标识
   * @param leaseTimeMs 租约时间（毫秒）
   */
  protected void startWatchDog(String lockKey, String clientId, long leaseTimeMs) {
    startWatchDog(lockKey, clientId, leaseTimeMs, LockType.REENTRANT);
  }

  /**
   * 启动 WatchDog 自动续期（带锁类型）
   *
   * <p>锁类型决定续期时使用的 Lua 脚本，避免看门狗盲试多个脚本造成额外 Redis 调用。
   *
   * @param lockKey 锁的键
   * @param clientId 客户端标识
   * @param leaseTimeMs 租约时间（毫秒）
   * @param lockType 锁类型
   */
  protected void startWatchDog(
      String lockKey, String clientId, long leaseTimeMs, LockType lockType) {
    if (leaseTimeMs <= 0) {
      return;
    }
    if (lockWatchDog != null) {
      lockWatchDog.startWatch(lockKey, clientId, leaseTimeMs, lockType);
    }
  }

  /**
   * 构建本地缓存键
   *
   * <p>使用 threadId 前缀确保不同线程的缓存条目互不干扰， 同时避免使用 ThreadLocal 导致的内存泄漏。
   *
   * @param lockKey 锁的键
   * @return 缓存键
   */
  private String buildCacheKey(String lockKey) {
    return Thread.currentThread().threadId() + ":" + lockKey;
  }

  /**
   * 释放锁
   *
   * <p>通过 Lua 脚本原子性释放锁。仅当锁<b>完全释放</b>（当前线程不再持有）时 才停止看门狗续期、减少活跃锁计数并广播释放通知；可重入锁在重入计数未归零时
   * 仅递减计数，锁仍由当前线程持有，不得停止续期。
   *
   * @param lockKey 锁的键
   * @param lockValue 锁的值（客户端标识）
   * @return true-释放成功，false-释放失败或锁键为空
   */
  @Override
  public boolean unlock(String lockKey, String lockValue) {
    if (lockKey == null || lockKey.isEmpty()) {
      log.warn("[ydsz-lock]解锁失败 | 锁键为空");
      return false;
    }
    long holdStartTime = System.currentTimeMillis();
    boolean released = false;
    boolean fullyReleased = false;
    try {
      released = doReleaseLock(lockKey, lockValue);
      if (released) {
        fullyReleased = isFullyReleased(lockKey, lockValue);
        if (fullyReleased) {
          if (lockWatchDog != null) {
            lockWatchDog.stopWatch(lockKey);
          }
          if (lockMetrics != null) {
            lockMetrics.decrementActiveLocks();
          }
          if (lockReleaseNotifier != null) {
            lockReleaseNotifier.notifyRelease(lockKey);
          }
          // 触发锁释放事件
          long holdTimeMs = System.currentTimeMillis() - holdStartTime;
          try {
            lockEventListener.onLockReleased(lockKey, lockValue, getLockType(), holdTimeMs);
          } catch (Exception e) {
            log.warn("[ydsz-lock]锁释放事件监听异常 | lockKey={} | error={}", lockKey, e.getMessage());
          }
        }
      }
      return released;
    } catch (Exception e) {
      log.error("[ydsz-lock]解锁异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
      return false;
    } finally {
      // 仅当锁完全释放或释放失败时才清理本地缓存，防止线程池复用场景下的泄漏；
      // 可重入锁部分释放（重入计数未归零，锁仍由当前线程持有）时必须保留 clientId 缓存，
      // 否则同一线程后续再次 unlock 会生成新 clientId，导致释放脚本校验失败、锁无法释放。
      if (fullyReleased || !released) {
        clearClientId(lockKey);
        clearLeaseTime(lockKey);
      }
    }
  }

  /**
   * 判断锁释放后是否已完全释放（当前线程不再持有）
   *
   * <p>默认实现返回 true，适用于非重入锁（释放即完全释放）。 可重入锁需覆写本方法：仅当重入计数归零（锁键被删除）时才返回 true， 避免重入深度大于 1
   * 时提前停止看门狗续期、误发释放通知。
   *
   * @param lockKey 锁的键
   * @param lockValue 锁的值（客户端标识）
   * @return true-锁已完全释放
   */
  protected boolean isFullyReleased(String lockKey, String lockValue) {
    return true;
  }

  /**
   * 检查锁是否被持有
   *
   * @param lockKey 锁的键
   * @return true-锁已被持有，false-锁未被持有或锁键为空
   */
  @Override
  public boolean isLocked(String lockKey) {
    if (lockKey == null || lockKey.isEmpty()) {
      return false;
    }
    try {
      return doIsLocked(lockKey);
    } catch (Exception e) {
      log.error("[ydsz-lock]检查锁状态异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
      return false;
    }
  }

  /**
   * 获取锁的剩余有效时间
   *
   * @param lockKey 锁的键
   * @return 剩余时间（毫秒），锁键为空或异常时返回 -2
   */
  @Override
  public long getRemainTime(String lockKey) {
    if (lockKey == null || lockKey.isEmpty()) {
      return REMAIN_TIME_ERROR;
    }
    try {
      return doGetRemainTime(lockKey);
    } catch (Exception e) {
      log.error("[ydsz-lock]获取剩余时间异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
      return REMAIN_TIME_ERROR;
    }
  }

  /**
   * 执行释放锁脚本
   *
   * @param lockKey 锁的键
   * @param clientId 客户端标识
   * @return true-释放成功
   */
  protected boolean executeReleaseScript(String lockKey, String clientId) {
    try {
      Long result =
          stringRedisTemplate.execute(
              releaseLockScript, Collections.singletonList(lockKey), clientId);
      return Long.valueOf(1L).equals(result);
    } catch (Exception e) {
      log.error("[ydsz-lock]执行释放锁脚本异常 | lockKey={} | error={}", lockKey, e.getMessage(), e);
      return false;
    }
  }

  /**
   * 单次获取锁（不做命名空间处理，使用调用方传入的完整键）
   *
   * <p>供 {@link #tryLockWithWait} 与子类非等待 {@code tryLock} 复用， 避免带命名空间键被二次前缀导致锁键漂移。
   *
   * @param lockKey 锁的键（已含命名空间前缀）
   * @param leaseTime 租约时间
   * @param timeUnit 时间单位
   * @return 锁值，获取成功返回非 null
   */
  protected String tryAcquireOnce(String lockKey, long leaseTime, TimeUnit timeUnit) {
    String clientId = getClientId(lockKey);
    String lockValue = doAcquireLock(lockKey, clientId, leaseTime, timeUnit);
    if (lockValue == null) {
      // 锁获取失败时清理本地缓存，防止泄漏（调用方不会调用 unlock）
      clearClientId(lockKey);
      clearLeaseTime(lockKey);
      return null;
    }
    if (lockMetrics != null) {
      lockMetrics.incrementActiveLocks();
    }
    return lockValue;
  }

  /**
   * 带等待重试的锁获取
   *
   * <p>等待策略：配置了 {@link LockReleaseNotifier} 时，通过 Redis pub/sub 释放通知 唤醒等待线程，避免空轮询放大 Redis
   * QPS；未配置时使用指数退避（10ms → 200ms）。
   *
   * @param lockKey 锁的键（已含命名空间前缀）
   * @param waitTime 等待时间
   * @param leaseTime 租约时间
   * @param timeUnit 时间单位
   * @return 锁值，获取成功返回非 null
   * @throws InterruptedException 线程中断异常
   */
  protected String tryLockWithWait(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit)
      throws InterruptedException {
    // 动态调整等待时间（如果配置了策略）
    long adjustedWaitTime = waitTime;
    if (lockWaitTimePolicy != null) {
      adjustedWaitTime = lockWaitTimePolicy.calculateWaitTime(lockKey, waitTime, null);
      // 确保调整后不小于 0
      if (adjustedWaitTime < 0) {
        adjustedWaitTime = 0;
      }
    }
    long waitNanos = timeUnit.toNanos(adjustedWaitTime);
    long startTime = System.nanoTime();
    long currentBackoff = BACKOFF_POLICY.getMinBackoff();
    String lockValue = null;
    while (true) {
      lockValue = tryAcquireOnce(lockKey, leaseTime, timeUnit);
      if (lockValue != null) {
        break;
      }
      // 记录锁竞争
      if (lockMetrics != null) {
        lockMetrics.recordCompetition(getLockType().name(), lockKey);
      }
      long elapsed = System.nanoTime() - startTime;
      if (elapsed >= waitNanos) {
        // 记录锁超时
        if (lockMetrics != null) {
          lockMetrics.recordLockTimeout(getLockType().name());
        }
        long waitTimeMs = TimeUnit.NANOSECONDS.toMillis(elapsed);
        // 触发锁超时事件
        try {
          lockEventListener.onLockAcquireTimeout(lockKey, getLockType(), waitTimeMs);
        } catch (Exception e) {
          log.warn("[ydsz-lock]锁超时事件监听异常 | lockKey={} | error={}", lockKey, e.getMessage());
        }
        return null;
      }
      long remainingWait = waitNanos - elapsed;
      waitBeforeRetry(lockKey, remainingWait, currentBackoff);
      currentBackoff = BACKOFF_POLICY.nextBackoff(currentBackoff);
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException();
      }
    }
    // 锁获取成功，记录等待时间
    long waitTimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
    if (lockMetrics != null) {
      lockMetrics.recordWaitDuration(waitTimeMillis, getLockType().name());
    }
    // 触发锁获取成功事件
    try {
      lockEventListener.onLockAcquired(lockKey, lockValue, getLockType(), waitTimeMillis);
    } catch (Exception e) {
      log.warn("[ydsz-lock]锁获取成功事件监听异常 | lockKey={} | error={}", lockKey, e.getMessage());
    }
    return lockValue;
  }

  /**
   * 等待后重试
   *
   * <p>优先使用释放通知阻塞等待（上限 {@link LockReleaseNotifier#getMaxAwaitMillis()}）， 未配置通知器时退化为全抖动随机睡眠（Full
   * Jitter）。
   *
   * <p><b>全抖动策略（Full Jitter）：</b>来自 AWS 架构博客推荐的指数退避 + 随机抖动算法， 可在高并发场景下有效分散同步请求，避免"惊群效应"。
   *
   * @param lockKey 锁的键
   * @param remainingWait 剩余可等待时间（纳秒）
   * @param currentBackoff 当前退避时间（毫秒）
   * @throws InterruptedException 线程中断异常
   */
  private void waitBeforeRetry(String lockKey, long remainingWait, long currentBackoff)
      throws InterruptedException {
    if (lockReleaseNotifier != null) {
      long awaitMillis =
          Math.min(
              TimeUnit.NANOSECONDS.toMillis(remainingWait),
              LockReleaseNotifier.getMaxAwaitMillis());
      if (awaitMillis > 0) {
        lockReleaseNotifier.awaitRelease(lockKey, awaitMillis);
      }
      return;
    }
    // 全抖动随机退避：在 [0, currentBackoff] 范围内随机选取等待时间
    long remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingWait);
    long jitterSleep = BACKOFF_POLICY.calculateSleepMillis(remainingMillis, currentBackoff);
    if (jitterSleep > 0) {
      log.debug(
          "[ydsz-lock] 全抖动退避等待 | lockKey={} | sleepMs={} | maxBackoff={}",
          lockKey,
          jitterSleep,
          remainingMillis);
      Thread.sleep(jitterSleep);
    }
  }

  /**
   * 返回当前锁实现对应的锁类型，用于指标采集打标
   *
   * @return 锁类型
   */
  protected abstract LockType getLockType();

  /**
   * 获取锁的底层实现
   *
   * @param lockKey 锁的键
   * @param clientId 客户端标识
   * @param leaseTime 租约时间
   * @param timeUnit 时间单位
   * @return 锁值，获取成功返回非 null
   */
  protected abstract String doAcquireLock(
      String lockKey, String clientId, long leaseTime, TimeUnit timeUnit);

  /**
   * 释放锁的底层实现
   *
   * @param lockKey 锁的键
   * @param clientId 客户端标识
   * @return true-释放成功
   */
  protected abstract boolean doReleaseLock(String lockKey, String clientId);

  /**
   * 检查锁状态的底层实现
   *
   * @param lockKey 锁的键
   * @return true-已锁定
   */
  protected abstract boolean doIsLocked(String lockKey);

  /**
   * 获取剩余时间的底层实现
   *
   * @param lockKey 锁的键
   * @return 剩余时间（毫秒），-2 表示异常
   */
  protected abstract long doGetRemainTime(String lockKey);
}
