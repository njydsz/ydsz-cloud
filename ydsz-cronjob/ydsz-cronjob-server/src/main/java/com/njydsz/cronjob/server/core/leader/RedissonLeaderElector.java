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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.cronjob.server.config.CronjobProperties;

/**
 * 基于 Redisson 的 Leader 选举实现。
 *
 * <p>使用 Redisson {@link RLock} 实现分布式 Leader 选举：
 *
 * <ul>
 *   <li>抢锁：{@code tryLock(0, -1, MILLISECONDS)} 非阻塞获取，leaseTime=-1 启用 WatchDog 自动续期
 *   <li>续期：WatchDog 每 10s 自动续期锁租约（30s），无需手动干预；holder key 由定时任务刷新
 *   <li>释放：优雅下线时 {@code @PreDestroy} 主动释放
 * </ul>
 *
 * <h3>多节点协作</h3>
 *
 * <ol>
 *   <li>所有节点启动时尝试 {@link #tryAcquire}，仅一节点成功
 *   <li>Leader 节点由 WatchDog 自动续期锁（默认 lease 30s，每 10s 续期）
 *   <li>Leader 崩溃后锁在 lease 内自动释放，Follower 下次 {@link #tryAcquire} 抢占
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * <h3>云顶规范 §22.5 报备（P1-2）</h3>
 *
 * <p>本类直接依赖 Redisson {@link RLock}，利用 <b>WatchDog 自动续期</b>（{@code tryLock(0, -1, MILLISECONDS)} 中
 * leaseTime=-1 启用 Redisson 看门狗，每 10s 续期至 30s 租约）与<b>线程级持有判定</b>（{@code isHeldByCurrentThread()}）
 * 实现 Leader 选举。经评估，{@code ydsz-common-lock} 的
 * {@link com.njydsz.common.lock.core.DistributedLocker} 当前仅提供通用分布式锁契约（tryLock/unlock/isLocked/getRemainTime），
 * <b>不具备选举语义所需的 WatchDog 续期与线程持有判定</b>，无法等价替换，否则会导致 Leader 身份漂移、多主风险。
 *
 * <p>依据《云顶编码规范》§22.5.3「评估必须自建时的报备机制」，此处临时保留 RLock 直用并报备：
 * 待 {@code ydsz-common-lock} 补充 {@code LeaderElector} 选举封装能力（基于 Redisson 看门狗 + 线程持有语义）后迁移。
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

  private final RedissonClient redissonClient;
  private final CronjobProperties cronjobProperties;

  /** 当前节点持有的 Leader 锁（role -> RLock） */
  private final Map<String, RLock> heldLocks = new ConcurrentHashMap<>();

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
    log.info("[LeaderElector] 节点 ID 初始化: nodeId={}", nodeId);
  }

  /**
   * 尝试抢占指定角色的 Leader 锁
   *
   * <p>使用 Redisson tryLock(0, -1, MILLISECONDS) 非阻塞获取， 仅当当前无 Leader 时成功。
   *
   * <p><b>WatchDog 自动续期</b>：leaseTime 传 -1 时 Redisson 启动 WatchDog 定时续期
   * （默认每 10s 续一次，续到 30s 租约），Leader 存活期间锁不会过期；Leader 崩溃后
   * 锁在 30s 内自动释放，其他节点即可抢占。显式传入有限 leaseTime 会禁用 WatchDog，
   * 导致锁到期后身份判定漂移，故此处必须使用 -1。
   *
   * <p>成功后写入 holder key（TTL=lease）供 {@link #getCurrentLeader} 读取真实节点。
   *
   * @param role Leader 角色（如 job-scheduler）
   * @param lease 租约时长（仅用于 holder key 的 TTL，锁本身由 WatchDog 续期）
   * @return true 抢占成功；false 已有其他节点持有
   */
  @Override
  public boolean tryAcquire(String role, Duration lease) {
    String key = LOCK_KEY_PREFIX + role;
    RLock lock = redissonClient.getLock(key);
    try {
      // leaseTime=-1：由 Redisson WatchDog 自动续期，避免显式租约到期导致 Leader 身份漂移
      boolean acquired = lock.tryLock(0, -1, TimeUnit.MILLISECONDS);
      if (acquired) {
        heldLocks.put(role, lock);
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
      }
      return acquired;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("[LeaderElector] 抢占 Leader 被中断: role={}", role);
      return false;
    }
  }

  /**
   * 续期 Leader 租约。
   *
   * <p>RLock 本体由 Redisson WatchDog 自动续期，无需手动干预； 本方法仅续期 holder key
   * （供 {@link #getCurrentLeader} 读取真实节点），并返回锁是否仍被当前线程持有，
   * 供定时任务判断是否需要重新抢占。
   *
   * @param role Leader 角色
   * @return true 锁仍被当前线程持有；false 未持有锁或续期失败
   */
  @Override
  public boolean renew(String role) {
    RLock lock = heldLocks.get(role);
    if (lock == null) {
      return false;
    }
    try {
      if (lock.isHeldByCurrentThread()) {
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
      return false;
    } catch (Exception e) {
      log.warn("[LeaderElector] 续期失败: role={} reason={}", role, e.getMessage());
      heldLocks.remove(role);
      return false;
    }
  }

  /**
   * 判断当前节点是否为指定角色的 Leader
   *
   * @param role Leader 角色
   * @return true 当前节点持有该角色的 Leader 锁
   */
  @Override
  public boolean isLeader(String role) {
    RLock lock = heldLocks.get(role);
    return lock != null && lock.isHeldByCurrentThread();
  }

  /**
   * 释放指定角色的 Leader 锁
   *
   * <p>优雅下线时调用，主动释放锁和 holder 标识， 让 Follower 节点能立即抢占（无需等待 lease 到期）。
   *
   * @param role Leader 角色
   */
  @Override
  public void release(String role) {
    RLock lock = heldLocks.remove(role);
    heldEpochs.remove(role);
    if (lock != null && lock.isHeldByCurrentThread()) {
      try {
        lock.unlock();
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
   * <p>优先从 holder key 读取真实节点 ID（hostname:port）； holder key 不存在时检查锁是否存在，存在返回 "unknown"，不存在返回 null。
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
    RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + role);
    return lock.isLocked() ? "unknown" : null;
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
    if (heldLocks.isEmpty()) {
      return;
    }
    for (String role : heldLocks.keySet()) {
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
    for (String role : heldLocks.keySet().toArray(new String[0])) {
      release(role);
    }
  }
}
