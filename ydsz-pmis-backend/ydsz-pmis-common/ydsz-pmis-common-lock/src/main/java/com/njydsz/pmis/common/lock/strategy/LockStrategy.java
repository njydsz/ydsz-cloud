package com.njydsz.pmis.common.lock.strategy;

import com.njydsz.pmis.common.lock.RedisReadWriteLock;
import com.njydsz.pmis.common.lock.RedisSemaphore;
import com.njydsz.pmis.common.lock.core.DistributedLocker;
import com.njydsz.pmis.common.lock.annotation.LockType;

/**
 * 锁策略工厂接口
 *
 * <p>根据 {@link LockType} 创建并返回对应的分布式锁实例，
 * 同时提供读写锁和信号量的创建方法。
 *
 * <p>实现类通过 Spring 注入 Redis 连接等资源，确保所有锁实例共享同一连接池。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see DefaultLockStrategy
 */
public interface LockStrategy {

    /**
     * 根据锁类型获取对应的分布式锁实例
     *
     * @param lockType 锁类型，不能为空
     * @return 对应类型的分布式锁实例
     */
    DistributedLocker getLock(LockType lockType);

    /**
     * 获取 Redis 读写锁实例
     *
     * @param key 锁键，不能为空
     * @return 读写锁实例
     */
    RedisReadWriteLock getReadWriteLock(String key);

    /**
     * 获取 Redis 信号量实例
     *
     * @param key     信号量键，不能为空
     * @param permits 最大许可数
     * @return 信号量实例
     */
    RedisSemaphore getSemaphore(String key, int permits);
}
