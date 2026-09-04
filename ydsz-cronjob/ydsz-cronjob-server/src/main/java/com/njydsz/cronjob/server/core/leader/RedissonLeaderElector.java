package com.njydsz.cronjob.server.core.leader;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.impl.RedisReentrantLock;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 基于 DistributedLocker（ydsz-common-lock 可重入锁）的 Leader 选举实现。
 *
 * <p>使用 {@link DistributedLocker} 实现分布式 Leader 选举：
 *
 * <ul>
 *   <li>抢锁：{@code tryLock(key, leaseTime, MILLISECONDS)} 非阻塞获取；leaseTime > 0 启用内部 WatchDog 自动续期
 *   <li>续期：WatchDog 自动续期锁租约（默认 30s），无需手动干预；holder key 由定时任务刷新
 *   <li>释放：优雅下线时 {@code @PreDestroy} 主动释放
 * </ul>
 *
 * <h3>多节点协作</h3>
 *
 * <ol>
 *   <li>所有节点启动时尝试 {@link #tryAcquire}，仅一节点成功
 *   <li>Leader 节点由 WatchDog 自动续期锁（默认 lease 30s，看门狗按配置间隔续期）
 *   <li>Leader 崩溃后锁在 lease 内自动释放，Follower 下次 {@link #tryAcquire} 抢占
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.redisson.api.RedissonClient")
@ConditionalOnProperty(prefix = "ydsz.cronjob.leader", name = "enabled", havingValue = "true")
public class RedissonLeaderElector implements LeaderElector {

  /** Leader 锁 key 前缀 */
  private static final String LOCK_KEY_PREFIX = "ydsz:job:leader:";

  /** P0-3: Leader 持有者标识 key 前缀（value=nodeId，供 getCurrentLeader 读取） */
  private static final String HOLDER_KEY_PREFIX = "ydsz:job:leader:holder:";

  /** P1-F4: Leader 任期号（epoch/fencing token）key 前缀：每次抢占成功时 INCR 单调递增 */
  private static final String EPOCH_KEY_PREFIX = "ydsz:job:leader:epoch:";

  /** 纳秒到毫秒的换算系数 */
  private static final long NANOS_PER_MILLIS = 1_000_000L;

  private final RedissonClient redissonClient;
  private final CronjobProperties cronjobProperties;

  /**
   * 锁策略工厂，用于获取可重入分布式锁实例。
   *
   * <p>通过 {@link LockStrategy#getLock(LockType)} 获取 {@link RedisReentrantLock}，
   * 实例内部集成 WatchDog 自动续期能力，等价于原 Redisson RLock 的 leaseTime=-1 行为。
   */
  private final LockStrategy lockStrategy;

  /** P0-2: 指标收集器（可选注入，避免未启用 Metrics 时异常） */
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

  /**
   * 当前节点持有的 Leader 锁标识：role → lockValue。
   *
   * <p>lockValue 由 {@link DistributedLocker#tryLock} 返回，
   * 后续 {@link DistributedLocker#unlock} 需要此值校验锁持有者身份。
   */
  private final Map<String, String> heldLockValues = new ConcurrentHashMap<>();

  /** 可重入分布式锁（由 LockStrategy 工厂创建的缓存实例，全局唯一） */
  private DistributedLocker distributedLocker;

  /** P1-F4: 当前节点持有的 Leader 任期号（role -> epoch，随抢占/重新抢占更新） */
  private final Map<String, Long> heldEpochs = new ConcurrentHashMap<>();

  /** P0-3: 当前节点 ID（hostname:port），用于 getCurrentLeader 返回真实节点标识 */
  private String nodeId;

  /** P0-5: 服务端口 */
  @Value("${server.port:0}")
  private int serverPort;

  /**
   * 初始化当前节点 ID（hostname:port）
   *
   * <p>在 @PostConstruct 中调用，确保 serverPort 已通过 @Value 注入。 用于 getCurrentLeader 返回真实节点标识。
   */
  @PostConstruct
  private void initNodeId() {
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      this.nodeId = hostname + ":" + serverPort;
    } catch (Exception e) {
      this.nodeId = ManagementFactory.getRuntimeMXBean().getName();
    }
    // 初始化分布式锁实例（lockStrategy 此时已通过构造器注入完成）
    this.distributedLocker = lockStrategy.getLock(LockType.REENTRANT);
    log.info("[LeaderElector] 节点 ID 初始化: nodeId={}, distributedLocker={}", nodeId, distributedLocker.getClass().getSimpleName());
  }

  /**
   * 尝试抢占指定角色的 Leader 锁
   *
   * <p>使用 {@link DistributedLocker#tryLock(String, long, TimeUnit)} 非阻塞获取，
   * 仅当当前无 Leader 时成功。leaseTime 由 {@code cronjobProperties.getLeader().getLeaseSeconds()} 指定，
   * {@link RedisReentrantLock} 内部 WatchDog 将按配置间隔自动续期，避免显式租约到期导致 Leader 身份漂移。
   *
   * <p>成功后写入 holder key（TTL=lease）供 {@link #getCurrentLeader} 读取真实节点。
   *
   * @param role Leader 角色（如 job-scheduler）
   * @param lease 租约时长（同时作为锁 leaseTime 和 holder key 的 TTL）
   * @return true 抢占成功；false 已有其他节点持有
   */
  @Override
  public boolean tryAcquire(String role, Duration lease) {
    String key = LOCK_KEY_PREFIX + role;
    long startNanos = System.nanoTime();
    // 非阻塞获取锁：waitTime=0，leaseTime=lease.toMillis() 启用内部 WatchDog 自动续期
    String lockValue = distributedLocker.tryLock(key, lease.toMillis(), TimeUnit.MILLISECONDS);
    if (lockValue != null) {
      heldLockValues.put(role, lockValue);
      // P1-F4: 抢占成功即递增任期号（fencing token），后续派发前比对，防双主双写
      long epoch = redissonClient.getAtomicLong(EPOCH_KEY_PREFIX + role).incrementAndGet();
      heldEpochs.put(role, epoch);
      // P0-3: 写入 Leader 持有者标识，供 getCurrentLeader 返回真实节点
      String holderKey = HOLDER_KEY_PREFIX + role;
      redissonClient.<String>getBucket(holderKey).set(nodeId, lease);
      log.info(
          "[LeaderElector] 抢占 Leader 成功: role={} lease={}ms nodeId={} epoch={}",
          role,
          lease.toMillis(),
          nodeId,
          epoch);
      // P0-2: 记录选举成功指标
      reportElectionMetrics(role, startNanos, epoch, true);
      return true;
    } else {
      // P0-2: 记录选举失败指标
      reportElectionMetrics(role, startNanos, -1, false);
      return false;
    }
  }

  /**
   * P0-2: 上报 Leader 选举指标。
   *
   * @param role Leader 角色
   * @param startNanos 选举开始时间（纳秒）
   * @param epoch 选举后的 epoch（失败时为 -1）
   * @param success 是否选举成功
   */
  private void reportElectionMetrics(String role, long startNanos, long epoch, boolean success) {
    CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
    if (metrics == null) {
      return;
    }
    long elapsedMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLIS;
    metrics.incLeaderElection(role, success ? "SUCCESS" : "FAILED");
    if (success) {
      metrics.recordLeaderElectionDuration(role, elapsedMs);
      metrics.setLeaderEpoch(role, epoch);
    }
  }

  /**
   * 续期 Leader 租约。
   *
   * <p>锁本体由 RedisReentrantLock 内部 WatchDog 自动续期，无需手动干预；
   * 本方法仅续期 holder key（供 {@link #getCurrentLeader} 读取真实节点），
   * 并返回锁是否仍被当前线程持有，供定时任务判断是否需要重新抢占。
   *
   * <p>使用 {@link RedisReentrantLock#isHeldByCurrentThread(String, String)} 判定当前线程是否仍持有锁，
   * 确保语义等价于 Redisson RLock 的 {@code isHeldByCurrentThread()}。
   *
   * @param role Leader 角色
   * @return true 锁仍被当前线程持有；false 未持有锁或续期失败
   */
  @Override
  public boolean renew(String role) {
    String lockValue = heldLockValues.get(role);
    if (lockValue == null) {
      return false;
    }
    String key = LOCK_KEY_PREFIX + role;
    try {
      // 使用具体实现类的方法检查当前线程是否仍持有锁
      if (distributedLocker instanceof RedisReentrantLock reentrantLock
          && reentrantLock.isHeldByCurrentThread(key, lockValue)) {
        // 锁由 WatchDog 自动续期；这里仅续期 holder 标识 key，供 getCurrentLeader() 读取真实节点
        Duration lease = Duration.ofSeconds(cronjobProperties.getLeader().getLeaseSeconds());
        String holderKey = HOLDER_KEY_PREFIX + role;
        redissonClient.<String>getBucket(holderKey).set(nodeId, lease);
        log.debug(
            "[LeaderElector] 续期 holder key: role={} lease={}s nodeId={}",
            role,
            lease.toSeconds(),
            nodeId);
        return true;
      }
      // 锁不再由当前线程持有，清理本地状态
      heldLockValues.remove(role);
      return false;
    } catch (Exception e) {
      log.warn("[LeaderElector] 续期失败: role={} reason={}", role, e.getMessage());
      heldLockValues.remove(role);
      return false;
    }
  }

  /**
   * 判断当前节点是否为指定角色的 Leader
   *
   * <p>检查本机是否持有该角色的 lockValue，且锁仍有效（Redis 中未被释放）。
   *
   * @param role Leader 角色
   * @return true 当前节点持有该角色的 Leader 锁
   */
  @Override
  public boolean isLeader(String role) {
    String lockValue = heldLockValues.get(role);
    if (lockValue == null) {
      return false;
    }
    // 若本机持有 lockValue 且锁仍有效（Redis 中未被释放），则当前节点为 Leader
    return distributedLocker.isLocked(LOCK_KEY_PREFIX + role);
  }

  /**
   * 释放指定角色的 Leader 锁
   *
   * <p>优雅下线时调用，主动释放锁和 holder 标识，
   * 让 Follower 节点能立即抢占（无需等待 lease 到期）。
   *
   * @param role Leader 角色
   */
  @Override
  public void release(String role) {
    String lockValue = heldLockValues.remove(role);
    heldEpochs.remove(role);
    if (lockValue != null) {
      try {
        String key = LOCK_KEY_PREFIX + role;
        distributedLocker.unlock(key, lockValue);
        // 清理 holder key
        String holderKey = HOLDER_KEY_PREFIX + role;
        redissonClient.getBucket(holderKey).delete();
        log.info("[LeaderElector] 释放 Leader: role={}", role);
      } catch (Exception e) {
        log.warn("[LeaderElector] 释放 Leader 失败: role={} reason={}", role, e.getMessage());
      }
    }
  }

  /**
   * 获取指定角色的当前 Leader 节点标识
   *
   * <p>优先从 holder key 读取真实节点 ID（hostname:port）；
   * holder key 不存在时检查锁是否存在，存在返回 "unknown"，不存在返回 null。
   *
   * @param role Leader 角色
   * @return Leader 节点标识；无 Leader 时返回 null
   */
  @Override
  public String getCurrentLeader(String role) {
    // P0-3: 从 holder key 读取真实 Leader 节点标识
    // 修复之前返回 "unknown" 的问题
    String holderKey = HOLDER_KEY_PREFIX + role;
    String holder = redissonClient.<String>getBucket(holderKey).get();
    if (holder != null && !holder.isBlank()) {
      return holder;
    }
    // 兜底: holder key 不存在（可能未启用 P0-3 改造），检查锁是否存在
    return distributedLocker.isLocked(LOCK_KEY_PREFIX + role) ? "unknown" : null;
  }

  /**
   * 获取当前节点持有的指定 role 的 Leader 任期号（epoch）。
   *
   * <p>仅当本节点持有该 role 的 Leader 锁时返回真实任期号；否则返回 -1（不参与 fencing）。
   *
   * @param role Leader 角色
   * @return 任期号；非 Leader 返回 -1
   */
  @Override
  public long getEpoch(String role) {
    return heldEpochs.getOrDefault(role, -1L);
  }

  /**
   * 定时续期 Leader 租约（默认每 10s 续期一次，lease 30s）。
   *
   * <p>在 lease 到期前续期，避免误释放导致 Leader 切换。
   */
  @Scheduled(fixedDelayString = "${ydsz.cronjob.leader.renew-interval-seconds:10}s")
  public void renewLeaseTask() {
    if (heldLockValues.isEmpty()) {
      return;
    }
    for (String role : heldLockValues.keySet().toArray(new String[0])) {
      boolean renewed = renew(role);
      if (!renewed) {
        log.warn("[LeaderElector] 续期失败，尝试重新抢占: role={}", role);
        Duration lease = Duration.ofSeconds(cronjobProperties.getLeader().getLeaseSeconds());
        if (tryAcquire(role, lease)) {
          log.info("[LeaderElector] 重新抢占 Leader 成功: role={}", role);
        } else {
          log.warn("[LeaderElector] 重新抢占 Leader 失败，等待下次续期: role={}", role);
        }
      }
    }
  }

  /** 优雅下线：释放所有持有的 Leader 锁。 */
  @PreDestroy
  public void shutdown() {
    for (String role : heldLockValues.keySet().toArray(new String[0])) {
      release(role);
    }
  }
}
