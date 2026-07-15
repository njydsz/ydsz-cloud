package com.njydsz.pmis.common.lock.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.njydsz.pmis.common.lock.core.DistributedLocker;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 多Key联锁实现 - 支持同时获取多个锁，原子性保证
 *
 * <p>用于需要同时锁定多个资源的场景（如：跨多实体的事务性操作）。
 * 所有锁必须全部获取成功才算成功，否则回滚已获取的所有锁，避免死锁。
 *
 * <p><b>核心原理：</b>
 * <ul>
 *   <li>按固定顺序依次获取每个锁，避免死锁</li>
 *   <li>任何一把锁获取失败时，立即回滚已获取的所有锁</li>
 *   <li>解锁时按相反顺序释放锁</li>
 *   <li>支持 WatchDog 对所有子锁统一续期</li>
 * </ul>
 *
 * <p><b>适用场景：</b>
 * <ul>
 *   <li>需要同时修改多个关联资源的场景</li>
 *   <li>跨多个业务实体的原子性操作</li>
 *   <li>防止多资源操作中的部分成功/部分失败问题</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
public class RedisMultiLock implements DistributedLocker {

	/**
	 * WatchDog 续期调度线程池（由 Spring 管理，支持优雅停机和配置化）
	 */
	private final ScheduledExecutorService renewalExecutor;

	/**
	 * 构造多Key联锁（需注入续期线程池）
	 *
	 * @param locks 底层分布式锁列表，至少需要 2 个锁
	 * @param renewalExecutor 续期调度线程池（由 Spring 管理）
	 */
	public RedisMultiLock(List<DistributedLocker> locks, ScheduledExecutorService renewalExecutor) {
		if (locks == null || locks.size() < 2) {
			throw new IllegalArgumentException("RedisMultiLock 至少需要 2 个底层锁");
		}
		this.locks = Collections.unmodifiableList(new ArrayList<>(locks));
		this.renewalExecutor = renewalExecutor;
	}

	/**
	 * 每个实例的续期任务映射（lockKey → ScheduledFuture）
	 */
	private final Map<String, ScheduledFuture<?>> renewalFutures = new ConcurrentHashMap<>();

	/**
	 * 底层分布式锁列表（按获取顺序）
	 */
	private final List<DistributedLocker> locks;

	/**
	 * 当前持有的锁值映射（lockKey → lockValue）
	 */
	private final Map<String, String> acquiredLockValues = new ConcurrentHashMap<>();

	/**
	 * WatchDog 续期状态
	 */
	private final AtomicBoolean renewing = new AtomicBoolean(false);

	/**
	 * 构造多Key联锁（需要注入自定义锁列表）
	 *
	 * @param locks 底层分布式锁列表，至少需要 2 个锁
	 */
	public RedisMultiLock(List<DistributedLocker> locks) {
		this(locks, Executors.newScheduledThreadPool(2, r -> {
			Thread t = new Thread(r, "ydsz-lock-multi-watchdog-" + System.nanoTime());
			t.setDaemon(true);
			return t;
		}));
	}

	/**
	 * 尝试获取多Key联锁（非阻塞）
	 *
	 * <p>依次获取每个子锁，任一失败则回滚所有已获取的锁。
	 *
	 * @param lockKey   锁的键（此参数在多锁场景下仅作日志标识，实际使用各子锁的键）
	 * @param leaseTime 租约时间
	 * @param timeUnit  时间单位
	 * @return 复合锁值（各子锁值用 '|' 拼接），获取成功返回非 null
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
			String compositeValue = String.join("|", acquired);
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
	 * @param lockKey   锁的键
	 * @param waitTime  最大等待时间
	 * @param leaseTime 租约时间
	 * @param timeUnit  时间单位
	 * @return 复合锁值，获取成功返回非 null
	 * @throws InterruptedException 等待过程中线程被中断
	 */
	@Override
	public String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
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

			String compositeValue = String.join("|", acquired);
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
	 * @param lockKey   锁的键
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
		return minRemain == Long.MAX_VALUE ? -2 : minRemain;
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
	 * @param index   子锁索引
	 * @return 子锁键
	 */
	protected String buildSubLockKey(String lockKey, int index) {
		return lockKey + ":multi:" + index;
	}

	/**
	 * 回滚已获取的锁
	 *
	 * @param lockKey       主锁键
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
	 * <p>对所有子锁统一进行续期操作。
	 *
	 * @param lockKey   主锁键
	 * @param leaseTime 租约时间
	 * @param timeUnit  时间单位
	 */
	private void startWatchDog(String lockKey, long leaseTime, TimeUnit timeUnit) {
		long leaseTimeMs = timeUnit.toMillis(leaseTime);
		long renewIntervalMs = leaseTimeMs / 3;

		renewing.set(true);

		ScheduledFuture<?> future = renewalExecutor.scheduleAtFixedRate(() -> {
			if (!renewing.get()) {
				return;
			}
			try {
				boolean renewed = renewAllLocks(lockKey, leaseTime, timeUnit);
				if (!renewed) {
					log.warn("RedisMultiLock WatchDog 续期失败，停止续期, key={}", lockKey);
					renewing.set(false);
					renewalFutures.remove(lockKey);
				} else {
					log.debug("RedisMultiLock WatchDog 续期成功, key={}", lockKey);
				}
			} catch (Exception e) {
				log.error("RedisMultiLock WatchDog 续期异常, key={}", lockKey, e);
				renewing.set(false);
				renewalFutures.remove(lockKey);
			}
		}, renewIntervalMs, renewIntervalMs, TimeUnit.MILLISECONDS);

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
			log.debug("RedisMultiLock WatchDog 已停止, key={}", lockKey);
		}
	}

	/**
	 * 续期所有子锁
	 *
	 * @param lockKey   主锁键
	 * @param leaseTime 租约时间
	 * @param timeUnit  时间单位
	 * @return true-全部续期成功
	 */
	private boolean renewAllLocks(String lockKey, long leaseTime, TimeUnit timeUnit) {
		for (int i = 0; i < locks.size(); i++) {
			DistributedLocker lock = locks.get(i);
			String subLockKey = buildSubLockKey(lockKey, i);
			String lockValue = acquiredLockValues.get(subLockKey);
			if (lockValue == null) {
				return false;
			}
			// 尝试通过 pexpire 续期
			try {
				long result = lock.pexpire(subLockKey, leaseTime, timeUnit);
				if (result <= 0) {
					return false;
				}
			} catch (UnsupportedOperationException e) {
				// 不支持 pexpire 的子锁，尝试重新获取
				if (!lock.isLocked(subLockKey)) {
					return false;
				}
			} catch (Exception e) {
				return false;
			}
		}
		return true;
	}
}
