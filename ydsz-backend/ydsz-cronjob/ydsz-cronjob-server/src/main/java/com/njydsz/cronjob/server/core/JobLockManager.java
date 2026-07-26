package com.njydsz.cronjob.server.core;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-1: 任务锁管理器（从 DefaultTaskDispatcher 提取）。
 *
 * <p>封装分布式锁的获取、续期、释放逻辑，消除 DefaultTaskDispatcher 中
 * 大量重复的 Redis SET NX EX + Lua CAS 释放代码。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #tryAcquireLock}：抢占分布式锁（SET NX EX + TTL）</li>
 *   <li>{@link #releaseLock}：安全释放锁（Lua CAS 删除，仅持有者可释放）</li>
 *   <li>{@link #isLocked}：检查锁是否被持有</li>
 *   <li>{@link #getLockHolder}：获取当前锁持有者标识</li>
 * </ul>
 *
 * <h3>提取动机</h3>
 * <p>DefaultTaskDispatcher 1592 行代码中约 200 行涉及锁操作，
 * 提取后可：
 * <ul>
 *   <li>统一锁 key 构造（通过 {@link LockKeyUtil}）</li>
 *   <li>统一 Lua 脚本引用（通过 {@link LockKeyUtil#RELEASE_LOCK_SCRIPT}）</li>
 *   <li>统一错误处理和日志格式</li>
 *   <li>便于单元测试（Mock JobLockManager 即可）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@RequiredArgsConstructor
public class JobLockManager {

    private final StringRedisTemplate redisTemplate;

    /** 释放锁脚本（CAS 删除，仅持有者可释放） */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT;
    static {
        RELEASE_SCRIPT = new DefaultRedisScript<>();
        RELEASE_SCRIPT.setScriptText(LockKeyUtil.RELEASE_LOCK_SCRIPT);
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    /**
     * 抢占分布式锁。
     *
     * @param jobKey     任务 KEY
     * @param shardIndex 分片索引（null=非分片任务）
     * @param ttlMs      锁 TTL（毫秒）
     * @return 锁持有者标识（hostname:pid:uuid）；获取失败返回 null
     */
    public String tryAcquireLock(String jobKey, Integer shardIndex, long ttlMs) {
        String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
        String lockValue = generateLockValue();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofMillis(ttlMs));
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("[JobLock] 获取锁成功: key={} value={} ttl={}ms", lockKey, lockValue, ttlMs);
            return lockValue;
        }
        return null;
    }

    /**
     * 安全释放分布式锁（仅持有者可释放）。
     *
     * @param jobKey     任务 KEY
     * @param shardIndex 分片索引
     * @param lockValue  锁持有者标识（tryAcquireLock 返回值）
     * @return true=释放成功，false=锁已被其他节点持有或已过期
     */
    public boolean releaseLock(String jobKey, Integer shardIndex, String lockValue) {
        String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
        Long result = redisTemplate.execute(RELEASE_SCRIPT,
                java.util.Collections.singletonList(lockKey), lockValue);
        boolean released = result != null && result > 0;
        if (released) {
            log.debug("[JobLock] 释放锁成功: key={}", lockKey);
        }
        return released;
    }

    /**
     * 检查锁是否被持有。
     *
     * @param jobKey     任务 KEY
     * @param shardIndex 分片索引
     * @return true=锁存在
     */
    public boolean isLocked(String jobKey, Integer shardIndex) {
        String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    /**
     * 获取当前锁持有者标识。
     *
     * @param jobKey     任务 KEY
     * @param shardIndex 分片索引
     * @return 锁 value；无锁时返回 null
     */
    public String getLockHolder(String jobKey, Integer shardIndex) {
        String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
        return redisTemplate.opsForValue().get(lockKey);
    }

    /**
     * 生成锁 value（hostname:pid:uuid）。
     */
    private String generateLockValue() {
        return java.net.InetAddress.getLoopbackAddress().getHostName()
                + ":" + ProcessHandle.current().pid()
                + ":" + UUID.randomUUID().toString().substring(0, 8);
    }
}
