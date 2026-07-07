package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.RetryStrategyResolver;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RetryScanner} 单元测试。
 *
 * <p>验证分布式锁获取/释放、重试成功/失败/死信流转、P1-7 可配重试策略集成。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@DisplayName("RetryScanner 重试扫描器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetryScannerTest {

    @Mock
    private MsgLogMapper msgLogMapper;
    @Mock
    private ChannelRouter channelRouter;
    @Mock
    private MessageMetrics messageMetrics;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RetryStrategyResolver retryStrategyResolver;
    @Mock
    private RLock lock;

    @InjectMocks
    private RetryScanner retryScanner;

    @BeforeEach
    void setUp() throws InterruptedException {
        when(redissonClient.getLock(MessageConstants.RETRY_SCAN_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        // P1-7: 默认未达上限,退避 2s
        when(retryStrategyResolver.isMaxRetriesReached(anyInt(), anyString())).thenReturn(false);
        when(retryStrategyResolver.calcNextRetryAt(anyInt(), anyString()))
                .thenReturn(LocalDateTime.now().plusSeconds(2));
    }

    @Test
    @DisplayName("scan 未获取锁时跳过扫描")
    void scanShouldSkipWhenLockNotAcquired() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(false);

        retryScanner.scan();

        verify(msgLogMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("scan 无待重试消息时直接返回")
    void scanShouldReturnWhenNoDueMessages() {
        when(msgLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> retryScanner.scan());

        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("scan 重试成功 → SUCCESS")
    void scanShouldMarkSuccessWhenRetrySucceeds() {
        MsgLogDO logDO = buildRetryLog("log-1", "SMS", 0);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(logDO));
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("provider-trace-1");

        retryScanner.scan();

        verify(msgLogMapper, atLeastOnce()).updateById(any(MsgLogDO.class));
        verify(messageMetrics).recordSend(eq("SMS"), eq("SUCCESS"), anyLong());
    }

    @Test
    @DisplayName("scan 重试失败未达上限 → RETRY + 更新 nextRetryAt")
    void scanShouldRetryAgainWhenFailureBelowMax() {
        MsgLogDO logDO = buildRetryLog("log-2", "EMAIL", 0);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(logDO));
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenThrow(new RuntimeException("smtp down"));

        retryScanner.scan();

        verify(messageMetrics).recordRetry("EMAIL");
        verify(retryStrategyResolver).calcNextRetryAt(1, "EMAIL");
    }

    @Test
    @DisplayName("scan 重试失败达上限 → DEAD")
    void scanShouldMarkDeadWhenMaxRetriesReached() {
        MsgLogDO logDO = buildRetryLog("log-3", "PUSH", 2);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(logDO));
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenThrow(new RuntimeException("push down"));
        when(retryStrategyResolver.isMaxRetriesReached(3, "PUSH")).thenReturn(true);

        retryScanner.scan();

        verify(messageMetrics).recordDead("PUSH");
        verify(retryStrategyResolver, never()).calcNextRetryAt(anyInt(), anyString());
    }

    @Test
    @DisplayName("scan 单条重试异常被吞,不影响其他消息")
    void scanShouldSwallowSingleRetryException() {
        MsgLogDO log1 = buildRetryLog("log-4", "SMS", 0);
        MsgLogDO log2 = buildRetryLog("log-5", "EMAIL", 0);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(log1, log2));
        // log1 dispatch 抛异常(非发送失败,是 updateById 抛的异常等)
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("unexpected"))
                .thenReturn("trace-5");

        assertDoesNotThrow(() -> retryScanner.scan());
    }

    @Test
    @DisplayName("scan 获取锁中断异常被吞")
    void scanShouldSwallowInterruptedException() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupted"));

        assertDoesNotThrow(() -> retryScanner.scan());
    }

    @Test
    @DisplayName("scan 重试次数从 null 开始计数为 1")
    void scanShouldHandleNullRetryCount() {
        MsgLogDO logDO = buildRetryLog("log-6", "SMS", null);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(logDO));
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenThrow(new RuntimeException("fail"));

        retryScanner.scan();

        // null → 1, 未达上限(默认3) → calcNextRetryAt(1, "SMS")
        verify(retryStrategyResolver).calcNextRetryAt(1, "SMS");
    }

    private MsgLogDO buildRetryLog(String id, String channel, Integer retryCount) {
        MsgLogDO logDO = new MsgLogDO();
        logDO.setId(id);
        logDO.setChannel(channel);
        logDO.setStatus(MessageStatusEnum.RETRY.name());
        logDO.setRetryCount(retryCount);
        logDO.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        logDO.setReceiver("u1");
        logDO.setContent("test");
        return logDO;
    }
}
