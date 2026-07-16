package com.njydsz.userinfo.server.security;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 账号锁定策略（userinfo 模块本地版本）
 *
 * <p>原参考实现位于 ydsz-common-security 包，因 common 重构后该策略类已迁移到各业务模块本地化。
 * 封装锁定阈值、锁定时长等参数，由 {@code UserAccountServiceImpl} 用于判断是否锁定账号。
 *
 * <p>使用示例：
 * <pre>{@code
 * AccountLockInfo policy = AccountLockInfo.defaultPolicy();
 * if (policy.shouldLock(failCount)) {
 *     u.setLockedUntil(policy.calculateLockUntil(LocalDateTime.now()));
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
public class AccountLockInfo {

    /** 登录失败达到此次数后触发账号锁定 */
    private final int maxFailCount;
    /** 账号锁定时长（分钟） */
    private final int lockMinutes;

    public AccountLockInfo(int maxFailCount, int lockMinutes) {
        this.maxFailCount = maxFailCount;
        this.lockMinutes = lockMinutes;
    }

    /**
     * 默认策略：5 次失败 → 锁定 30 分钟
     */
    public static AccountLockInfo defaultPolicy() {
        return new AccountLockInfo(5, 30);
    }

    public int getMaxFailCount() {
        return maxFailCount;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    /**
     * 判断当前失败次数是否应触发锁定
     */
    public boolean shouldLock(int failCount) {
        return failCount >= maxFailCount;
    }

    /**
     * 计算锁定到期时间（当前时间 + 锁定时长）
     */
    public LocalDateTime calculateLockUntil(LocalDateTime now) {
        return now.plusMinutes(lockMinutes);
    }

    /**
     * 判断账号当前是否处于锁定状态（LocalDateTime 版本）
     *
     * @param lockedUntil 锁定到期时间（null 表示未锁定）
     * @return true 表示仍在锁定中
     */
    public boolean isLocked(LocalDateTime lockedUntil) {
        if (lockedUntil == null) {
            return false;
        }
        return isLocked(lockedUntil.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    /**
     * 判断账号当前是否处于锁定状态（Long 毫秒时间戳版本）
     *
     * @param lockedUntil 锁定到期时间（毫秒时间戳或 null）
     * @return true 表示仍在锁定中
     */
    public boolean isLocked(Long lockedUntil) {
        return lockedUntil != null && lockedUntil > System.currentTimeMillis();
    }

    /**
     * 计算距离解锁还剩多少分钟
     *
     * @param lockedUntil 锁定到期时间（毫秒时间戳或 null）
     * @return 剩余分钟数；未锁定时返回 0
     */
    public long remainingMinutes(Long lockedUntil) {
        if (lockedUntil == null) {
            return 0L;
        }
        long remainMs = lockedUntil - System.currentTimeMillis();
        if (remainMs <= 0) {
            return 0L;
        }
        return (remainMs + 59_999L) / 60_000L;
    }

    /**
     * LocalDateTime 形式版本的剩余分钟数计算（兼容不同字段类型）
     */
    public long remainingMinutes(LocalDateTime lockedUntil) {
        if (lockedUntil == null) {
            return 0L;
        }
        long ts = lockedUntil.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return remainingMinutes(ts);
    }
}
