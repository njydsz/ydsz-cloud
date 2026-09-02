package com.njydsz.common.lock;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;

import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.util.BackoffPolicy;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 基于 Redis + Lua 脚本的分布式信号量 使用 Lua 脚本保证 acquire/release 的原子操作，解决并发安全问题
 *
 * <p>并发安全保证：
 *
 * <ul>
 *   <li>acquire：Lua 原子检查信号量计数 > 0 + 递减
 *   <li>release：Lua 原子检查信号量计数 < permits + 递增
 *   <li>初始化：Lua 原子 NX set 初始 permits，防止重复初始化
 * </ul>
 *
 * <p><b>等待策略：</b> 使用指数退避策略（10ms → 200ms）替代固定 50ms 轮询，减少无效 Redis 调用。
 *
 * <p><b>超时自动释放：</b>
 *
 * <ul>
 *   <li>acquireWithTimeout：获取信号量后启动定时任务，超时后自动 release
 *   <li>业务代码正常 release 时，取消定时任务，避免误释放
 * </ul>
 *
 * <p>自 26.09.01 起实现 {@link DistributedLocker} 接口， 可纳入 {@link
 * com.njydsz.common.lock.strategy.LockStrategy} 统一管理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RedisSemaphore implements DistributedLocker {

  /** 剩余时间错误码（键不存在或获取失败） */
  private static final long REMAIN_TIME_ERROR = -2L;

  /** Redis String 操作组件 */
  private final RedisStringOps redisStringOps;

  /** Redis 模板，用于执行 Lua 脚本 */
  private final RedisTemplate<String, Object> redisTemplate;

  /** 信号量 Redis Key */
  private final String key;

  /** 许可数量 */
  private final int permits;

  /** 信号量过期时间（毫秒） */
  private final long expireMillis;

  private final TaskScheduler timeoutScheduler;

  /** 每次获取信号量对应的超时自动释放任务 Key: acquireId（UUID），Value: ScheduledFuture */
  private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutTasks =
      new ConcurrentHashMap<>();

  /** 初始化标志（使用 AtomicBoolean 保证线程安全的懒初始化） */
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  /** 初始化信号量 Lua 脚本：原子性 NX 设置初始许可数量和过期时间 */
  private static final String INIT_PERMITS_SCRIPT =
      "local current = redis.call('get', KEYS[1]) "
          + "if current == false then "
          + "redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX') "
          + "return 1 "
          + "else return 0 end";

  /** 获取信号量 Lua 脚本：原子性检查许可数大于 0 时递减 */
  private static final String ACQUIRE_SCRIPT =
      "local current = redis.call('get', KEYS[1]) "
          + "if current == false then return -1 end "
          + "local c = tonumber(current) "
          + "if c > 0 then "
          + "redis.call('decr', KEYS[1]) "
          + "return c - 1 "
          + "else return -1 end";

  /** 释放信号量 Lua 脚本：原子性检查许可数小于上限时递增 */
  private static final String RELEASE_SCRIPT =
      "local current = redis.call('get', KEYS[1]) "
          + "if current == false then return -1 end "
          + "local c = tonumber(current) "
          + "if c < tonumber(ARGV[1]) then "
          + "redis.call('incr', KEYS[1]) "
          + "return c + 1 "
          + "else return -1 end";

  /** 状态查询 Lua 脚本：键不存在返回 -1，否则返回当前许可数 */
  private static final String STATUS_SCRIPT =
      "local current = redis.call('get', KEYS[1]) "
          + "if current == false then return -1 end "
          + "return tonumber(current)";

  /** 初始化信号量脚本封装（预编译，避免热路径重复构建） */
  private static final DefaultRedisScript<Long> INIT_SCRIPT =
      new DefaultRedisScript<>(INIT_PERMITS_SCRIPT, Long.class);

  /** 获取信号量脚本封装（预编译） */
  private static final DefaultRedisScript<Long> ACQUIRE_LOCK_SCRIPT =
      new DefaultRedisScript<>(ACQUIRE_SCRIPT, Long.class);

  /** 释放信号量脚本封装（预编译） */
  private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT =
      new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

  /** 状态查询脚本封装（预编译） */
  private static final DefaultRedisScript<Long> STATUS_LOCK_SCRIPT =
      new DefaultRedisScript<>(STATUS_SCRIPT, Long.class);

  /**
   * 构造 RedisSemaphore（无命名空间，需要注入调度线程池，便于 Spring 管理和配置化）
   *
   * @param redisStringOps Redis String 操作组件
   * @param redisTemplate Redis 模板，用于执行 Lua 脚本
   * @param key 信号量键
   * @param permits 许可数量
   * @param expireMillis 过期时间（毫秒）
   * @param timeoutScheduler 超时调度线程池
   */
  public RedisSemaphore(
      RedisStringOps redisStringOps,
      RedisTemplate<String, Object> redisTemplate,
      String key,
      int permits,
      long expireMillis,
      TaskScheduler timeoutScheduler) {
    this(redisStringOps, redisTemplate, key, permits, expireMillis, timeoutScheduler, null);
  }

  /**
   * 构造 RedisSemaphore（带命名空间）
   *
   * @param redisStringOps Redis String 操作组件
   * @param redisTemplate Redis 模板，用于执行 Lua 脚本
   * @param key 信号量键
   * @param permits 许可数量
   * @param expireMillis 过期时间（毫秒）
   * @param timeoutScheduler 超时调度线程池
   * @param namespace 锁键命名空间前缀，用于多应用共享 Redis 时的隔离
   */
  public RedisSemaphore(
      RedisStringOps redisStringOps,
      RedisTemplate<String, Object> redisTemplate,
      String key,
      int permits,
      long expireMillis,
      TaskScheduler timeoutScheduler,
      String namespace) {
    this.redisStringOps = redisStringOps;
    this.redisTemplate = redisTemplate;
    String prefix = (namespace != null && !namespace.isEmpty()) ? namespace + ":lock:" : "";
    this.key = prefix + "semaphore:" + key;
    this.permits = permits;
    this.expireMillis = expireMillis;
    this.timeoutScheduler = timeoutScheduler;
  }

  /** 退避策略实例 */
  private static final BackoffPolicy BACKOFF_POLICY = new BackoffPolicy();

  /**
   * 初始化信号量许可数量，仅在 key 不存在时设置
   *
   * <p>使用懒初始化模式，避免在 Spring 启动时执行 Redis 操作导致连接问题
   */
  private void initPermits() {
    if (!initialized.compareAndSet(false, true)) {
      return;
    }
    try {
      redisTemplate.execute(
          INIT_SCRIPT,
          Collections.singletonList(key),
          String.valueOf(permits),
          String.valueOf(expireMillis));
    } catch (Exception e) {
      log.warn("信号量初始化失败: {}", key, e);
      initialized.set(false);
    }
  }

  /** 确保信号量已初始化（在每次操作前调用） */
  private void ensureInitialized() {
    if (!initialized.get()) {
      initPermits();
    }
  }

  /**
   * 尝试获取信号量（不等待）
   *
   * @return true-获取成功，false-获取失败
   */
  public boolean tryAcquire() {
    return tryAcquire(0, TimeUnit.MILLISECONDS);
  }

  /**
   * 尝试获取信号量（带等待时间）
   *
   * <p>使用指数退避策略（10ms → 200ms）替代固定 50ms 轮询间隔。
   *
   * @param timeout 最大等待时间
   * @param unit 时间单位
   * @return true-获取成功，false-获取失败或超时
   */
  public boolean tryAcquire(long timeout, TimeUnit unit) {
    ensureInitialized();
    long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
    boolean reinitialized = false;
    long currentBackoff = BACKOFF_POLICY.getMinBackoff();
    while (true) {
      try {
        Long result =
            redisTemplate.execute(
                ACQUIRE_LOCK_SCRIPT, Collections.singletonList(key), String.valueOf(permits));
        if (result != null && result >= 0) {
          return true;
        }
        if (result != null && result == -1L && !reinitialized) {
          reinitialized = true;
          if (initialized.compareAndSet(true, false)) {
            initPermits();
            continue;
          }
        }
      } catch (Exception e) {
        log.warn("信号量获取异常: {}", key, e);
      }
      if (System.currentTimeMillis() >= deadline) {
        return false;
      }
      currentBackoff = BACKOFF_POLICY.sleepAndNextBackoff(deadline, currentBackoff);
    }
  }

  /**
   * 获取信号量（带超时自动释放）
   *
   * @param timeout 超时时间
   * @param unit 时间单位
   * @return 获取成功返回 acquireId，获取失败返回 null
   */
  public String acquireWithTimeout(long timeout, TimeUnit unit) {
    if (tryAcquire(0, TimeUnit.MILLISECONDS)) {
      String acquireId = IdGenerator.nextIdStr();
      long timeoutMillis = unit.toMillis(timeout);
      ScheduledFuture<?> future =
          timeoutScheduler.schedule(
              () -> {
                timeoutTasks.remove(acquireId);
                releaseInternal();
                log.warn("信号量超时自动释放: key={}, acquireId={}", key, acquireId);
              },
              Instant.now().plusMillis(timeoutMillis));
      timeoutTasks.put(acquireId, future);
      return acquireId;
    }
    return null;
  }

  /**
   * 获取信号量（带等待时间和超时自动释放）
   *
   * @param waitTimeout 等待获取信号量的超时时间
   * @param waitUnit 等待时间单位
   * @param autoReleaseTimeout 获取成功后的自动释放超时时间
   * @param releaseUnit 自动释放时间单位
   * @return 获取成功返回 acquireId，获取失败返回 null
   */
  public String acquireWithTimeout(
      long waitTimeout, TimeUnit waitUnit, long autoReleaseTimeout, TimeUnit releaseUnit) {
    if (tryAcquire(waitTimeout, waitUnit)) {
      String acquireId = IdGenerator.nextIdStr();
      long timeoutMillis = releaseUnit.toMillis(autoReleaseTimeout);
      ScheduledFuture<?> future =
          timeoutScheduler.schedule(
              () -> {
                timeoutTasks.remove(acquireId);
                releaseInternal();
                log.warn("信号量超时自动释放: key={}, acquireId={}", key, acquireId);
              },
              Instant.now().plusMillis(timeoutMillis));
      timeoutTasks.put(acquireId, future);
      return acquireId;
    }
    return null;
  }

  /** 释放信号量（不关联超时任务，超时自动释放任务继续生效） */
  public void release() {
    releaseInternal();
  }

  /**
   * 释放信号量（带 acquireId，取消超时自动释放定时任务）
   *
   * @param acquireId 获取信号量时返回的 acquireId
   */
  public void release(String acquireId) {
    if (acquireId != null) {
      ScheduledFuture<?> future = timeoutTasks.remove(acquireId);
      if (future != null) {
        future.cancel(false);
        log.debug("信号量超时自动释放任务已取消: key={}, acquireId={}", key, acquireId);
      }
    }
    releaseInternal();
  }

  /** 内部释放信号量实现 */
  private void releaseInternal() {
    try {
      Long result =
          redisTemplate.execute(
              RELEASE_LOCK_SCRIPT, Collections.singletonList(key), String.valueOf(permits));
      if (result != null && result == -1L) {
        log.warn("信号量释放失败，已超过最大许可数: {}", key);
      }
    } catch (Exception e) {
      log.error("信号量释放异常: {}", key, e);
    }
  }

  // ======================== DistributedLocker 接口实现 ========================

  @Override
  public String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
    if (tryAcquire()) {
      String acquireId = IdGenerator.nextIdStr();
      long timeoutMillis = timeUnit.toMillis(leaseTime);
      if (timeoutMillis > 0 && timeoutScheduler != null) {
        ScheduledFuture<?> future =
            timeoutScheduler.schedule(
                () -> {
                  timeoutTasks.remove(acquireId);
                  releaseInternal();
                  log.warn("信号量超时自动释放: key={}, acquireId={}", key, acquireId);
                },
                Instant.now().plusMillis(timeoutMillis));
        timeoutTasks.put(acquireId, future);
      }
      return acquireId;
    }
    return null;
  }

  @Override
  public String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit)
      throws InterruptedException {
    if (tryAcquire(waitTime, timeUnit)) {
      String acquireId = IdGenerator.nextIdStr();
      long timeoutMillis = timeUnit.toMillis(leaseTime);
      if (timeoutMillis > 0 && timeoutScheduler != null) {
        ScheduledFuture<?> future =
            timeoutScheduler.schedule(
                () -> {
                  timeoutTasks.remove(acquireId);
                  releaseInternal();
                  log.warn("信号量超时自动释放: key={}, acquireId={}", key, acquireId);
                },
                Instant.now().plusMillis(timeoutMillis));
        timeoutTasks.put(acquireId, future);
      }
      return acquireId;
    }
    return null;
  }

  @Override
  public boolean unlock(String lockKey, String lockValue) {
    release(lockValue);
    return true;
  }

  /**
   * 检查信号量是否被占满
   *
   * <p>实现 {@link DistributedLocker#isLocked(String)}：返回 true 表示当前许可已耗尽
   * （资源被完全占用）；信号量键不存在（未初始化或已过期）时返回 false。
   *
   * @param lockKey 锁的键（当前实现忽略，使用构造时传入的 key）
   * @return true-许可耗尽（被占满）
   */
  @Override
  public boolean isLocked(String lockKey) {
    try {
      Long current = redisTemplate.execute(STATUS_LOCK_SCRIPT, Collections.singletonList(key));
      // 仅许可耗尽（0）视为占满；-1（未初始化）与正数（仍有许可）均视为未占满
      return Long.valueOf(0L).equals(current);
    } catch (Exception e) {
      log.error("信号量检查状态异常: {}", key, e);
      return false;
    }
  }

  @Override
  public long getRemainTime(String lockKey) {
    try {
      long seconds = redisStringOps.getExpire(key);
      return seconds > 0 ? TimeUnit.SECONDS.toMillis(seconds) : seconds;
    } catch (Exception e) {
      log.error("信号量获取剩余时间异常: {}", key, e);
      return REMAIN_TIME_ERROR;
    }
  }
}
