package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.channel.MessageChannel;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.dto.ReceiptResult;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.enums.ReceiptStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.service.MessageLogService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReceiptPuller} 单元测试（P2-9 回执闭环）。
 *
 * <p>覆盖：未获取锁跳过、无待处理消息、超时优先标记 TIMEOUT、渠道拉取成功更新回执、
 * 渠道不支持拉取跳过、拉取异常不中断、中断异常处理、扫描异常释放锁。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ReceiptPuller 回执闭环调度器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiptPullerTest {

    /**
     * 手动初始化 MyBatis-Plus TableInfo 缓存。
     *
     * <p>纯 Mock 测试未启动 Spring 上下文,MsgLogDO 的 lambda 引用(如
     * {@code MsgLogDO::getStatus})会因 lambda cache 未初始化抛
     * {@code MybatisPlusException}。此处用 {@link TableInfoHelper#initTableInfo}
     * 手动注册。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, MsgLogDO.class);
    }

    @Mock
    private MsgLogMapper msgLogMapper;
    @Mock
    private ChannelRouter channelRouter;
    @Mock
    private MessageLogService messageLogService;
    @Mock
    private MessageProperties messageProperties;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    @InjectMocks
    private ReceiptPuller puller;

    @BeforeEach
    void setUp() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        // 默认配置：拉取延迟 5 分钟，超时阈值 30 分钟
        when(messageProperties.getReceiptPullDelayMinutes()).thenReturn(5L);
        when(messageProperties.getReceiptTimeoutMinutes()).thenReturn(30L);
    }

    /**
     * 构造测试消息日志：SUCCESS + NONE 回执，createdAt 距今指定分钟数。
     */
    private MsgLogDO buildLog(String id, String channel, int minutesAgo) {
        MsgLogDO logDO = new MsgLogDO();
        logDO.setId(id);
        logDO.setChannel(channel);
        logDO.setStatus(MessageStatusEnum.SUCCESS.name());
        logDO.setReceiptStatus(ReceiptStatusEnum.NONE.name());
        logDO.setCreatedAt(LocalDateTime.now().minusMinutes(minutesAgo));
        return logDO;
    }

    @Test
    @DisplayName("scan: 未获取锁时跳过扫描,不调用 mapper")
    void shouldSkipScanWhenLockNotAcquired() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(false);

        puller.scan();

        verify(msgLogMapper, never()).selectList(any());
        verify(messageLogService, never()).updateReceipt(anyString(), anyString(), any());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("scan: 获取锁但无待处理消息时,不调用任何 service")
    void shouldNotPullWhenNoPending() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(msgLogMapper.selectList(any())).thenReturn(List.of());

        puller.scan();

        verify(messageLogService, never()).updateReceipt(anyString(), anyString(), any());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("scan: 超过超时阈值(40min)的消息优先标记 TIMEOUT,不调用渠道拉取")
    void shouldMarkTimeoutWhenExceedThreshold() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        MsgLogDO logDO = buildLog("log-1", "SMS", 40);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(logDO));

        puller.scan();

        // 标记 TIMEOUT，不调用渠道
        verify(messageLogService, times(1)).updateReceipt(eq("log-1"),
                eq(ReceiptStatusEnum.TIMEOUT.name()), any());
        verify(channelRouter, never()).route(anyString());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("scan: 未超时(10min)且渠道拉取返回 DELIVERED 时,更新回执为 DELIVERED")
    void shouldUpdateReceiptWhenChannelReturnsDelivered() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        MsgLogDO logDO = buildLog("log-2", "SMS", 10);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(logDO));
        MessageChannel channel = org.mockito.Mockito.mock(MessageChannel.class);
        when(channelRouter.route("SMS")).thenReturn(channel);
        when(channel.queryReceipt(logDO))
                .thenReturn(Optional.of(ReceiptResult.of(ReceiptStatusEnum.DELIVERED, "OK")));

        puller.scan();

        verify(messageLogService, times(1)).updateReceipt(eq("log-2"),
                eq(ReceiptStatusEnum.DELIVERED.name()), any());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("scan: 渠道不支持拉取(empty)时跳过,不更新回执")
    void shouldSkipWhenChannelNotSupportPull() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        MsgLogDO logDO = buildLog("log-3", "IN_APP", 10);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(logDO));
        MessageChannel channel = org.mockito.Mockito.mock(MessageChannel.class);
        when(channelRouter.route("IN_APP")).thenReturn(channel);
        when(channel.queryReceipt(logDO)).thenReturn(Optional.empty());

        puller.scan();

        verify(messageLogService, never()).updateReceipt(anyString(), anyString(), any());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("scan: 渠道拉取抛异常时 WARN 跳过,不中断后续消息且不更新回执")
    void shouldSkipWhenChannelPullThrows() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        MsgLogDO logDO = buildLog("log-4", "EMAIL", 10);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(logDO));
        MessageChannel channel = org.mockito.Mockito.mock(MessageChannel.class);
        when(channelRouter.route("EMAIL")).thenReturn(channel);
        when(channel.queryReceipt(logDO)).thenThrow(new RuntimeException("provider error"));

        puller.scan();

        verify(messageLogService, never()).updateReceipt(anyString(), anyString(), any());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("scan: 混合场景 - 超时标记 + 拉取成功 + 渠道不支持,各只处理一次")
    void shouldHandleMixedBatchCorrectly() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        MsgLogDO timeoutLog = buildLog("log-timeout", "SMS", 45);
        MsgLogDO pullableLog = buildLog("log-pull", "SMS", 8);
        MsgLogDO unsupportedLog = buildLog("log-unsupported", "IN_APP", 8);
        when(msgLogMapper.selectList(any())).thenReturn(List.of(timeoutLog, pullableLog, unsupportedLog));

        MessageChannel smsChannel = org.mockito.Mockito.mock(MessageChannel.class);
        MessageChannel inAppChannel = org.mockito.Mockito.mock(MessageChannel.class);
        when(channelRouter.route("SMS")).thenReturn(smsChannel);
        when(channelRouter.route("IN_APP")).thenReturn(inAppChannel);
        when(smsChannel.queryReceipt(pullableLog))
                .thenReturn(Optional.of(ReceiptResult.of(ReceiptStatusEnum.READ)));
        when(inAppChannel.queryReceipt(unsupportedLog)).thenReturn(Optional.empty());

        puller.scan();

        // 超时标记 1 次（timeoutLog），拉取成功更新 1 次（pullableLog），不支持跳过（unsupportedLog）
        verify(messageLogService, times(1)).updateReceipt(eq("log-timeout"),
                eq(ReceiptStatusEnum.TIMEOUT.name()), any());
        verify(messageLogService, times(1)).updateReceipt(eq("log-pull"),
                eq(ReceiptStatusEnum.READ.name()), any());
        verify(messageLogService, never()).updateReceipt(eq("log-unsupported"), anyString(), any());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("scan: tryLock 抛 InterruptedException 时中断当前线程且不解锁")
    void shouldHandleInterrupted() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("interrupted"));

        puller.scan();

        verify(msgLogMapper, never()).selectList(any());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("scan: doScan 抛异常时仍释放锁")
    void shouldReleaseLockWhenDoScanThrows() throws InterruptedException {
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(msgLogMapper.selectList(any())).thenThrow(new RuntimeException("db error"));

        puller.scan();

        verify(lock, times(1)).unlock();
    }
}
