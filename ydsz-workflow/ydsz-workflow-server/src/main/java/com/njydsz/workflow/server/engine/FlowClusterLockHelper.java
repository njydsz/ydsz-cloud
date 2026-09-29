package com.njydsz.workflow.server.engine;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.core.LockTemplate;

/**
 * P0-2: 工作流集群调度分布式锁辅助工具（P0-C2：委托 LockTemplate 消除 try-finally 样板代码）
 *
 * <p>用于包装 {@code @Scheduled} 定时任务，确保多节点部署时同一任务同一时刻只有一个节点执行。
 *
 * <p>实现：委托给 ydsz-common-lock 的 {@link LockTemplate}（内部使用 {@link
 * com.njydsz.common.lock.impl.RedisReentrantLock} + 看门狗自动续期），获取失败时返回 null（不阻塞等待）。
 * 锁 key 以 {@code ydsz:flow:schedule:} 为前缀，TTL 略大于任务预计执行时间。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowClusterLockHelper {

  /** 锁 key 前缀 */
  private static final String LOCK_PREFIX = "ydsz:flow:schedule:";

  private final LockTemplate lockTemplate;

  /**
   * 构造集群锁助手
   *
   * @param lockTemplate 锁模板
   */
  public FlowClusterLockHelper(LockTemplate lockTemplate) {
    this.lockTemplate = lockTemplate;
    log.info("[FlowClusterLock] LockTemplate 已注入，定时任务将以集群锁模式运行");
  }

  /**
   * 尝试获取分布式锁并执行任务
   *
   * <p>获取失败（其他节点正在执行）时直接返回 null，跳过本次执行。
   *
   * @param lockKey 锁 key（不含前缀，自动添加）
   * @param leaseTimeSec 锁持有时间（秒），应略大于任务预计执行时间
   * @param task 要执行的任务
   * @param <T> 返回类型
   * @return 任务执行结果；未获取锁时返回 null
   */
  public <T> T tryRun(String lockKey, long leaseTimeSec, Supplier<T> task) {
    String fullKey = LOCK_PREFIX + lockKey;
    return lockTemplate.executeOrDefault(fullKey, leaseTimeSec, TimeUnit.SECONDS, task, null);
  }

  /**
   * 尝试获取分布式锁并执行无返回值任务
   *
   * @param lockKey 锁 key
   * @param leaseTimeSec 锁持有时间（秒）
   * @param task 要执行的任务
   */
  public void tryRun(String lockKey, long leaseTimeSec, Runnable task) {
    tryRun(lockKey, leaseTimeSec, () -> {
      task.run();
      return null;
    });
  }
}
