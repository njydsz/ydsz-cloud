package com.njydsz.pmis.common.security;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 账号锁定策略结果
 *
 * <p>登录失败达到上限后，账号将被锁定一段时间。
 * 锁定状态由 {@link com.njydsz.pmis.userinfo.domain.entity.UserAccountDO#lockedUntil} 字段承载。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
public class AccountLockInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 失败次数阈值（默认 5） */
    private int maxFailCount;

    /** 锁定时长（分钟，默认 30） */
    private int lockMinutes;

    /**
     * 是否应当锁定
     *
     * @param currentFailCount 当前失败次数
     * @return true 表示达到阈值应当锁定
     */
    public boolean shouldLock(int currentFailCount) {
        return currentFailCount >= maxFailCount;
    }

    /**
     * 计算锁定截止时间
     *
     * @param baseTime 基准时间，为 null 时取当前时间
     * @return 锁定截止时间
     */
    public LocalDateTime calculateLockUntil(LocalDateTime baseTime) {
        LocalDateTime from = baseTime == null ? LocalDateTime.now() : baseTime;
        return from.plusMinutes(lockMinutes);
    }

    /**
     * 是否仍处于锁定状态
     *
     * @param lockedUntil 锁定截止时间，为 null 表示未锁定
     * @return true 表示仍处于锁定状态
     */
    public boolean isLocked(LocalDateTime lockedUntil) {
        if (lockedUntil == null) return false;
        return lockedUntil.isAfter(LocalDateTime.now());
    }

    /**
     * 剩余锁定分钟数（向上取整，最小 1）
     *
     * @param lockedUntil 锁定截止时间，为 null 时返回 0
     * @return 剩余锁定分钟数
     */
    public long remainingMinutes(LocalDateTime lockedUntil) {
        if (lockedUntil == null) return 0;
        long m = Duration.between(LocalDateTime.now(), lockedUntil).toMinutes();
        return Math.max(m, 1);
    }

    /**
     * 默认账号锁定策略（失败 5 次锁定 30 分钟）
     *
     * @return 默认策略
     */
    public static AccountLockInfo defaultPolicy() {
        return AccountLockInfo.builder()
                .maxFailCount(5)
                .lockMinutes(30)
                .build();
    }
}
