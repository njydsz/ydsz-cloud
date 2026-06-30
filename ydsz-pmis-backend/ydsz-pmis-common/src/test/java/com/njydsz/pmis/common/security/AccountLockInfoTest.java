package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 账号锁定策略测试
 *
 * @author ydsz-pmis-team
 */
class AccountLockInfoTest {

    @Test
    @DisplayName("默认策略 5 次失败 / 30 分钟")
    void defaultPolicy() {
        AccountLockInfo p = AccountLockInfo.defaultPolicy();
        assertThat(p.getMaxFailCount()).isEqualTo(5);
        assertThat(p.getLockMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("shouldLock 在达到阈值时返回 true")
    void shouldLock() {
        AccountLockInfo p = AccountLockInfo.builder().maxFailCount(5).lockMinutes(30).build();
        assertThat(p.shouldLock(4)).isFalse();
        assertThat(p.shouldLock(5)).isTrue();
        assertThat(p.shouldLock(10)).isTrue();
    }

    @Test
    @DisplayName("calculateLockUntil 基于 now")
    void calculateLockUntil() {
        AccountLockInfo p = AccountLockInfo.builder().maxFailCount(5).lockMinutes(30).build();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime until = p.calculateLockUntil(base);
        assertThat(until).isEqualTo(base.plusMinutes(30));
    }

    @Test
    @DisplayName("isLocked 状态判定")
    void isLocked() {
        AccountLockInfo p = AccountLockInfo.defaultPolicy();
        assertThat(p.isLocked(null)).isFalse();
        assertThat(p.isLocked(LocalDateTime.now().plusMinutes(10))).isTrue();
        assertThat(p.isLocked(LocalDateTime.now().minusMinutes(1))).isFalse();
    }

    @Test
    @DisplayName("remainingMinutes")
    void remainingMinutes() {
        AccountLockInfo p = AccountLockInfo.defaultPolicy();
        assertThat(p.remainingMinutes(null)).isEqualTo(0);
        long m = p.remainingMinutes(LocalDateTime.now().plusMinutes(5));
        assertThat(m).isBetween(4L, 5L);
    }
}
