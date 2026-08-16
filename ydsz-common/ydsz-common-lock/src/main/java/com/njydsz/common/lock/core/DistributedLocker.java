package com.njydsz.common.lock.core;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁核心接口
 *
 * <p>定义分布式锁的基本操作契约，提供非阻塞/阻塞两种获取方式、释放、状态查询能力。
 *
 * <p>本接口是分布式锁的能力提供者（"Locker" 强调"提供锁的对象"角色）， 与 {@code
 * com.njydsz.common.lock.annotation.DistributedLock} 注解（标记方法需要加锁）同名不同物， 命名区分以避免同模块 import 冲突。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li>高可用：锁的获取与释放保证原子性
 *   <li>防死锁：锁必须有过期时间，防止节点宕机导致死锁
 *   <li>高性能：基于 Lua 脚本保证操作原子性，减少网络往返
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DistributedLocker {

  /**
   * 尝试获取锁（非阻塞）
   *
   * @param lockKey 锁的键
   * @param leaseTime 锁的自动释放时间
   * @param timeUnit 时间单位
   * @return 获取成功返回 lockValue（用于释放锁时校验），获取失败返回 null
   */
  String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit);

  /**
   * 尝试获取锁（阻塞等待）
   *
   * @param lockKey 锁的键
   * @param waitTime 最大等待时间
   * @param leaseTime 锁的自动释放时间
   * @param timeUnit 时间单位
   * @return 获取成功返回 lockValue，等待超时返回 null
   * @throws InterruptedException 线程被中断
   */
  String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit)
      throws InterruptedException;

  /**
   * 释放锁
   *
   * @param lockKey 锁的键
   * @param lockValue 获取锁时返回的 lockValue，用于校验锁的持有者
   * @return true-释放成功，false-释放失败或锁已过期
   */
  boolean unlock(String lockKey, String lockValue);

  /**
   * 检查锁是否被持有
   *
   * @param lockKey 锁的键
   * @return true-锁被持有，false-锁未被持有
   */
  boolean isLocked(String lockKey);

  /**
   * 获取锁的剩余过期时间
   *
   * @param lockKey 锁的键
   * @return 剩余时间（毫秒），-1 表示锁未被持有，-2 表示获取失败
   */
  long getRemainTime(String lockKey);
}
