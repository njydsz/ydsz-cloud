package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.njydsz.pmis.message.entity.MsgAggregateDO;
import com.njydsz.pmis.message.enums.AggregateBatchStatusEnum;
import com.njydsz.pmis.message.mapper.MsgAggregateMapper;
import com.njydsz.pmis.message.service.AggregateService;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AggregateScheduler} 单元测试。
 *
 * <p>覆盖：未获取锁跳过、获取锁执行扫描、无到期批次不调用 flushDue、
 * 中断异常处理、扫描异常释放锁。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AggregateScheduler 聚合调度器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AggregateSchedulerTest {

    /**
     * 手动初始化 MyBatis-Plus TableInfo 缓存。
     *
     * <p>纯 Mock 测试未启动 Spring 上下文,MsgAggregateDO 的 lambda
     * 引用(如 {@code MsgAggregateDO::getBatchStatus})会因 lambda cache 未初始化
     * 抛 {@code MybatisPlusException}。此处用 {@link TableInfoHelper#initTableInfo}
     * 手动注册,使 {@link com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper}
     * 的方法引用可解析。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, MsgAggregateDO.class);
    }

    @Mock
    private MsgAggregateMapper msgAggregateMapper;
    @Mock
    private AggregateService aggregateService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    @InjectMocks
    private AggregateScheduler scheduler;

    @BeforeEach
    void setUp() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
    }

    @Test
    @DisplayName("scan: 未获取锁时跳过扫描,不调用 mapper")
    void shouldSkipScanWhenLockNotAcquired() throws InterruptedException {
        when(lock.tryLock(0L, 60L, TimeUnit.SECONDS)).thenReturn(false);

        scheduler.scan();

        verify(msgAggregateMapper, never()).selectList(any());
        verify(aggregateService, never()).flushDue();
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("scan: 获取锁且无到期批次时,不调用 flushDue")
    void shouldNotFlushWhenNoDueBatch() throws InterruptedException {
        when(lock.tryLock(0L, 60L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(msgAggregateMapper.selectList(any())).thenReturn(List.of());

        scheduler.scan();

        verify(aggregateService, never()).flushDue();
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("scan: 获取锁且有到期批次时,流转状态并 flushDue")
    void shouldTransitAndFlushWhenDueBatchExists() throws InterruptedException {
        when(lock.tryLock(0L, 60L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        MsgAggregateDO batch = new MsgAggregateDO();
        batch.setId("b1");
        batch.setBatchStatus(AggregateBatchStatusEnum.PENDING.name());
        when(msgAggregateMapper.selectList(any())).thenReturn(List.of(batch));
        when(aggregateService.flushDue()).thenReturn(1);

        scheduler.scan();

        // 流转 PENDING -> READY
        verify(msgAggregateMapper, times(1)).update(eq(null), any());
        verify(aggregateService, times(1)).flushDue();
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("scan: tryLock 抛 InterruptedException 时中断当前线程且不解锁")
    void shouldHandleInterrupted() throws InterruptedException {
        when(lock.tryLock(0L, 60L, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("interrupted"));

        scheduler.scan();

        verify(msgAggregateMapper, never()).selectList(any());
        // locked=false,不调用 unlock
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("scan: doScan 抛异常时仍释放锁")
    void shouldReleaseLockWhenDoScanThrows() throws InterruptedException {
        when(lock.tryLock(0L, 60L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(msgAggregateMapper.selectList(any()))
                .thenThrow(new RuntimeException("db error"));

        scheduler.scan();

        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("scan: flushDue 抛异常时仍释放锁且不中断")
    void shouldReleaseLockWhenFlushDueThrows() throws InterruptedException {
        when(lock.tryLock(0L, 60L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        MsgAggregateDO batch = new MsgAggregateDO();
        batch.setId("b1");
        when(msgAggregateMapper.selectList(any())).thenReturn(List.of(batch));
        doThrow(new RuntimeException("flush error"))
                .when(aggregateService).flushDue();

        scheduler.scan();

        verify(lock, times(1)).unlock();
    }
}
