package com.njydsz.pmis.cronjob.core.leader;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedissonLeaderElector} 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>tryAcquire 成功：记录到 heldLocks 且 isLeader 返回 true</li>
 *   <li>tryAcquire 失败：未记录到 heldLocks 且 isLeader 返回 false</li>
 *   <li>renew 是 Leader 时通过 RBucket.expire 续期</li>
 *   <li>renew 不是 Leader 时返回 false</li>
 *   <li>release 调用 lock.unlock</li>
 *   <li>getCurrentLeader 返回 unknown 或 null</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RedissonLeaderElector Leader 选举测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedissonLeaderElectorTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;
    @Mock
    private RBucket<Object> bucket;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private RedissonLeaderElector elector;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        cronjobProperties.getLeader().setLeaseSeconds(30);
        // 通过反射注入 cronjobProperties
        try {
            java.lang.reflect.Field f = RedissonLeaderElector.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(elector, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(redissonClient.getBucket(anyString())).thenReturn(bucket);
    }

    @Test
    @DisplayName("tryAcquire 成功时记录到 heldLocks 且 isLeader 返回 true")
    void tryAcquire_success_marksAsLeader() throws InterruptedException {
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        boolean acquired = elector.tryAcquire("role-1", Duration.ofSeconds(30));

        assertTrue(acquired);
        assertTrue(elector.isLeader("role-1"));
    }

    @Test
    @DisplayName("tryAcquire 失败时不记录到 heldLocks")
    void tryAcquire_failed_notLeader() throws InterruptedException {
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        boolean acquired = elector.tryAcquire("role-1", Duration.ofSeconds(30));

        assertFalse(acquired);
        assertFalse(elector.isLeader("role-1"));
    }

    @Test
    @DisplayName("tryAcquire 被中断时返回 false 并恢复中断状态")
    void tryAcquire_interrupted_returnsFalse() throws InterruptedException {
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("test"));

        boolean acquired = elector.tryAcquire("role-1", Duration.ofSeconds(30));

        assertFalse(acquired);
        assertTrue(Thread.currentThread().isInterrupted());
        // 清理中断标志
        Thread.interrupted();
    }

    @Test
    @DisplayName("renew 是 Leader 时通过 RBucket.expire 续期返回 true")
    void renew_isLeader_extendsLease() throws InterruptedException {
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        elector.tryAcquire("role-1", Duration.ofSeconds(30));

        boolean renewed = elector.renew("role-1");

        assertTrue(renewed);
        verify(bucket, times(1)).expire(eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("renew 未持有时返回 false")
    void renew_notHeld_returnsFalse() {
        boolean renewed = elector.renew("role-1");
        assertFalse(renewed);
        verify(bucket, never()).expire(anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("release 调用 lock.unlock 移除 heldLocks")
    void release_callsUnlock() throws InterruptedException {
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        elector.tryAcquire("role-1", Duration.ofSeconds(30));
        elector.release("role-1");

        verify(lock, times(1)).unlock();
        assertFalse(elector.isLeader("role-1"));
    }

    @Test
    @DisplayName("release 未持有的 role 时不调用 unlock")
    void release_notHeld_doesNothing() {
        elector.release("role-1");
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("getCurrentLeader 锁活跃时返回 unknown")
    void getCurrentLeader_locked_returnsUnknown() {
        when(lock.isLocked()).thenReturn(true);
        String leader = elector.getCurrentLeader("role-1");
        assertTrue("unknown".equals(leader));
    }

    @Test
    @DisplayName("getCurrentLeader 锁未活跃时返回 null")
    void getCurrentLeader_unlocked_returnsNull() {
        when(lock.isLocked()).thenReturn(false);
        String leader = elector.getCurrentLeader("role-1");
        assertFalse(leader != null);
    }

    @Test
    @DisplayName("shutdown 释放所有持有的 Leader 锁")
    void shutdown_releasesAllHeldLocks() throws InterruptedException {
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        elector.tryAcquire("role-1", Duration.ofSeconds(30));
        elector.shutdown();

        verify(lock, times(1)).unlock();
    }
}
