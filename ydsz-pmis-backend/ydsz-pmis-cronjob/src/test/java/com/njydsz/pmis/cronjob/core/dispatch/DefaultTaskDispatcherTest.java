package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.alert.AlertTrigger;
import com.njydsz.pmis.cronjob.core.executor.JobNodeHeartbeat;
import com.njydsz.pmis.cronjob.core.sharding.ShardingStrategy;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.JobNodeMapper;
import com.njydsz.pmis.cronjob.metrics.CronjobMetrics;
import com.njydsz.pmis.cronjob.service.TenantQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultTaskDispatcher} 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>dispatch(MANUAL) 不抢锁直接执行</li>
 *   <li>dispatch(CRON) 锁获取成功时执行</li>
 *   <li>dispatch(CRON) 锁被持有时返回 null</li>
 *   <li>handler 抛异常时日志标 FAILED 并释放锁</li>
 *   <li>成功后释放锁并调用心跳组件</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DefaultTaskDispatcher 任务派发器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class DefaultTaskDispatcherTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private JobHandler jobHandler;
    @Mock
    private JobNodeHeartbeat jobNodeHeartbeat;
    @Mock
    private JobNodeMapper jobNodeMapper;
    @Mock
    private ObjectProvider<ShardingStrategy> shardingStrategyProvider;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ObjectProvider<AlertTrigger> alertTriggerProvider;
    @Mock
    private ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
    @Mock
    private ObjectProvider<TenantQuotaService> tenantQuotaServiceProvider;
    @Mock
    private TenantQuotaService tenantQuotaService;
    @Mock
    private ObjectProvider<com.njydsz.pmis.cronjob.core.handler.HttpJobHandler> httpJobHandlerProvider;
    @Mock
    private ObjectProvider<RemoteTaskClient> remoteTaskClientProvider;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private DefaultTaskDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@RequiredArgsConstructor 不识别非 @Mock 字段）
        try {
            java.lang.reflect.Field f = DefaultTaskDispatcher.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(dispatcher, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }
        // P5/P6-2: 手动注入 ObjectProvider 字段，避免 @InjectMocks 因类型擦除将
        // shardingStrategyProvider / alertTriggerProvider / cronjobMetricsProvider 互相错位注入
        try {
            java.lang.reflect.Field f1 = DefaultTaskDispatcher.class.getDeclaredField("shardingStrategyProvider");
            f1.setAccessible(true);
            f1.set(dispatcher, shardingStrategyProvider);
            java.lang.reflect.Field f2 = DefaultTaskDispatcher.class.getDeclaredField("alertTriggerProvider");
            f2.setAccessible(true);
            f2.set(dispatcher, alertTriggerProvider);
            java.lang.reflect.Field f3 = DefaultTaskDispatcher.class.getDeclaredField("cronjobMetricsProvider");
            f3.setAccessible(true);
            f3.set(dispatcher, cronjobMetricsProvider);
            // P7-3: 注入 TenantQuotaService ObjectProvider
            java.lang.reflect.Field f4 = DefaultTaskDispatcher.class.getDeclaredField("tenantQuotaServiceProvider");
            f4.setAccessible(true);
            f4.set(dispatcher, tenantQuotaServiceProvider);
            // P1-5: 注入 HttpJobHandler ObjectProvider
            java.lang.reflect.Field f5 = DefaultTaskDispatcher.class.getDeclaredField("httpJobHandlerProvider");
            f5.setAccessible(true);
            f5.set(dispatcher, httpJobHandlerProvider);
            // P1-4: 注入 RemoteTaskClient ObjectProvider
            java.lang.reflect.Field f6 = DefaultTaskDispatcher.class.getDeclaredField("remoteTaskClientProvider");
            f6.setAccessible(true);
            f6.set(dispatcher, remoteTaskClientProvider);
        } catch (Exception e) {
            throw new IllegalStateException("注入 ObjectProvider 失败", e);
        }

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(anyString(), eq(JobHandler.class))).thenReturn(jobHandler);
        // P5/P6-2: ObjectProvider 默认返回 null（告警触发器与指标收集器在测试中不启用）
        lenient().when(shardingStrategyProvider.getIfAvailable()).thenReturn(null);
        lenient().when(alertTriggerProvider.getIfAvailable()).thenReturn(null);
        lenient().when(cronjobMetricsProvider.getIfAvailable()).thenReturn(null);
        // P7-3: TenantQuotaService 默认不启用（配额检查在测试中默认关闭）
        lenient().when(tenantQuotaServiceProvider.getIfAvailable()).thenReturn(null);
        // P1-5: HttpJobHandler 默认不启用（HTTP 任务类型在测试中默认降级到 BEAN 模式）
        lenient().when(httpJobHandlerProvider.getIfAvailable()).thenReturn(null);
        // P1-4: RemoteTaskClient 默认不启用（分片任务在测试中默认本地执行）
        lenient().when(remoteTaskClientProvider.getIfAvailable()).thenReturn(null);
        // P1-7: 初始化执行线程池（测试用同步执行器，任务在调用线程中执行）
        try {
            java.util.concurrent.ThreadPoolExecutor syncPool = new java.util.concurrent.ThreadPoolExecutor(
                    1, 1, 0, java.util.concurrent.TimeUnit.MILLISECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(),
                    r -> { Thread t = new Thread(r, "test-exec"); t.setDaemon(true); return t; },
                    new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()) {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            };
            java.lang.reflect.Field f6 = DefaultTaskDispatcher.class.getDeclaredField("taskExecutorPool");
            f6.setAccessible(true);
            f6.set(dispatcher, syncPool);
        } catch (Exception e) {
            throw new IllegalStateException("注入 taskExecutorPool 失败", e);
        }
        lenient().when(jobLogMapper.insert(any(JobLogDO.class))).thenAnswer(invocation -> {
            JobLogDO log = invocation.getArgument(0);
            log.setId("log-test-" + System.nanoTime());
            return 1;
        });
        // 默认非分片模式：ShardingStrategy 不可用
        lenient().when(shardingStrategyProvider.getIfAvailable()).thenReturn(null);
        // P5: AlertTrigger 默认不可用（告警触发器在测试中不启用）
        lenient().when(alertTriggerProvider.getIfAvailable()).thenReturn(null);
    }

    @Test
    @DisplayName("dispatch(MANUAL) 不抢锁直接执行并返回日志 ID")
    void dispatch_manual_doesNotAcquireLock() throws Exception {
        JobDO job = buildJob("manual-key", null);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        assertNotNull(logId);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(jobLogMapper, times(1)).insert(any(JobLogDO.class));
        verify(jobLogMapper, times(1)).updateById(any(JobLogDO.class));
        verify(jobNodeHeartbeat, times(1)).onTaskStart();
        verify(jobNodeHeartbeat, times(1)).onTaskComplete();
    }

    @Test
    @DisplayName("dispatch(CRON) 锁获取成功时异步执行任务（P1-7 异步派发返回 null）")
    void dispatch_cron_lockAcquired_executesJob() throws Exception {
        JobDO job = buildJob("cron-key", null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        // P1-7: CRON 触发走异步派发，dispatch 返回 null，但任务已同步执行（测试用同步线程池）
        assertNull(logId);
        verify(valueOps, times(1)).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(), (Object) any());
    }

    @Test
    @DisplayName("dispatch(CRON) 锁被持有时返回 null")
    void dispatch_cron_lockHeld_returnsNull() {
        JobDO job = buildJob("held-key", null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        assertNull(logId);
        verify(jobLogMapper, never()).insert(any(JobLogDO.class));
        verify(jobNodeHeartbeat, never()).onTaskStart();
    }

    @Test
    @DisplayName("dispatch(CRON) 执行失败时日志标 FAILED 且释放锁")
    void dispatch_cron_executionFailed_releasesLock() throws Exception {
        JobDO job = buildJob("fail-key", null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any())).thenThrow(new RuntimeException("boom"));

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        // P1-7: CRON 异步派发返回 null，但任务已执行（测试用同步线程池）
        assertNull(logId);
        verify(jobLogMapper, times(1)).updateById(any(JobLogDO.class));
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(), (Object) any());
        verify(jobNodeHeartbeat, times(1)).onTaskStart();
        verify(jobNodeHeartbeat, times(1)).onTaskComplete();
    }

    @Test
    @DisplayName("dispatch(MANUAL) handler 抛异常时任务状态标 ERROR")
    void dispatch_manual_handlerThrows_statusError() throws Exception {
        JobDO job = buildJob("err-key", null);
        when(jobHandler.execute(any())).thenThrow(new RuntimeException("err"));

        dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        // 手动触发不更新 next_fire_time（triggerType != CRON）
        verify(jobMapper, times(1)).updateStats(anyString(), any(), eq(null),
                eq(1L), eq(0L), eq(1L), eq("ERROR"));
    }

    @Test
    @DisplayName("dispatch(CRON) 任务级 lockTtlMs 在 [30s,24h] 区间内使用任务级 TTL")
    void dispatch_cron_taskLevelTtl_inRange() {
        JobDO job = buildJob("ttl-key", Duration.ofMinutes(10).toMillis());
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        verify(valueOps).setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(10)));
    }

    // ==================== P3 分片场景测试 ====================

    @Test
    @DisplayName("P3: shardTotal=null 走非分片模式")
    void dispatch_shardTotalNull_fallsBackToNonSharded() throws Exception {
        JobDO job = buildJob("non-shard", null);
        job.setShardTotal(null);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        assertNotNull(logId);
        // 验证调用的是单参 execute，而非分片 execute
        verify(jobHandler, times(1)).execute(any());
    }

    @Test
    @DisplayName("P3: shardTotal=1 走非分片模式")
    void dispatch_shardTotal1_fallsBackToNonSharded() throws Exception {
        JobDO job = buildJob("single-shard", null);
        job.setShardTotal(1);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        assertNotNull(logId);
        verify(jobHandler, times(1)).execute(any());
    }

    @Test
    @DisplayName("P3: shardTotal>1 但 ShardingStrategy 不可用时 fallback 到非分片")
    void dispatch_shardStrategyUnavailable_fallsBackToNonSharded() throws Exception {
        JobDO job = buildJob("no-strategy", null);
        job.setShardTotal(4);
        // shardingStrategyProvider.getIfAvailable() 默认返回 null（见 setUp）
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        assertNotNull(logId);
        verify(jobHandler, times(1)).execute(any());
    }

    @Test
    @DisplayName("P3: 分片任务无在线节点时本地执行全部分片")
    void dispatch_shardedNoOnlineNodes_executesAllShardsLocally() throws Exception {
        JobDO job = buildJob("shard-no-nodes", null);
        job.setShardTotal(3);
        when(shardingStrategyProvider.getIfAvailable()).thenReturn(new com.njydsz.pmis.cronjob.core.sharding.AverageShardingStrategy());
        when(jobNodeMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(jobNodeHeartbeat.getNodeId()).thenReturn("local-node");
        when(jobHandler.execute(any(), any(com.njydsz.pmis.common.job.ShardingContext.class))).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        assertNotNull(logId);
        // 应执行 3 个分片（无锁，因为是 MANUAL）
        verify(jobHandler, times(3)).execute(any(), any(com.njydsz.pmis.common.job.ShardingContext.class));
        verify(jobLogMapper, times(3)).insert(any(JobLogDO.class));
        verify(jobNodeHeartbeat, times(3)).onTaskStart();
        verify(jobNodeHeartbeat, times(3)).onTaskComplete();
    }

    @Test
    @DisplayName("P3: 分片任务 CRON 模式每分片独立加锁")
    void dispatch_shardedCron_acquiresShardLevelLocks() throws Exception {
        JobDO job = buildJob("shard-cron", null);
        job.setShardTotal(2);
        when(shardingStrategyProvider.getIfAvailable()).thenReturn(new com.njydsz.pmis.cronjob.core.sharding.AverageShardingStrategy());
        when(jobNodeMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(jobNodeHeartbeat.getNodeId()).thenReturn("local-node");
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any(), any(com.njydsz.pmis.common.job.ShardingContext.class))).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        assertNotNull(logId);
        // 2 个分片，每个加锁一次
        verify(valueOps, times(2)).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), any(), (Object) any());
    }

    @Test
    @DisplayName("P3: 分片锁被持有时跳过该分片")
    void dispatch_shardedLockHeld_skipsShard() throws Exception {
        JobDO job = buildJob("shard-held", null);
        job.setShardTotal(2);
        when(shardingStrategyProvider.getIfAvailable()).thenReturn(new com.njydsz.pmis.cronjob.core.sharding.AverageShardingStrategy());
        when(jobNodeMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(jobNodeHeartbeat.getNodeId()).thenReturn("local-node");
        // 第一个分片锁获取失败，第二个成功
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE)
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any(), any(com.njydsz.pmis.common.job.ShardingContext.class))).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        assertNotNull(logId);
        // 只执行了第二个分片
        verify(jobHandler, times(1)).execute(any(), any(com.njydsz.pmis.common.job.ShardingContext.class));
    }

    @Test
    @DisplayName("P3: 分片 handler 接收正确的 ShardingContext")
    void dispatch_sharded_handlerReceivesCorrectContext() throws Exception {
        JobDO job = buildJob("shard-ctx", null);
        job.setShardTotal(2);
        when(shardingStrategyProvider.getIfAvailable()).thenReturn(new com.njydsz.pmis.cronjob.core.sharding.AverageShardingStrategy());
        when(jobNodeMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(jobNodeHeartbeat.getNodeId()).thenReturn("local-node");
        when(jobHandler.execute(any(), any(com.njydsz.pmis.common.job.ShardingContext.class))).thenReturn("ok");

        dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        // 验证 handler 被调用时传入了正确的 ShardingContext
        org.mockito.ArgumentCaptor<com.njydsz.pmis.common.job.ShardingContext> captor =
                org.mockito.ArgumentCaptor.forClass(com.njydsz.pmis.common.job.ShardingContext.class);
        verify(jobHandler, times(2)).execute(any(), captor.capture());
        List<com.njydsz.pmis.common.job.ShardingContext> contexts = captor.getAllValues();
        // 第一个分片
        assertEquals(2, contexts.get(0).getShardTotal());
        assertEquals(0, contexts.get(0).getShardIndex());
        assertEquals("shard-ctx", contexts.get(0).getJobKey());
        // 第二个分片
        assertEquals(2, contexts.get(1).getShardTotal());
        assertEquals(1, contexts.get(1).getShardIndex());
    }

    @Test
    @DisplayName("P3: 分片执行失败不影响其他分片")
    void dispatch_shardedOneFails_othersStillExecute() throws Exception {
        JobDO job = buildJob("shard-partial-fail", null);
        job.setShardTotal(2);
        when(shardingStrategyProvider.getIfAvailable()).thenReturn(new com.njydsz.pmis.cronjob.core.sharding.AverageShardingStrategy());
        when(jobNodeMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(jobNodeHeartbeat.getNodeId()).thenReturn("local-node");
        // 第一个分片失败，第二个成功
        when(jobHandler.execute(any(), any(com.njydsz.pmis.common.job.ShardingContext.class)))
                .thenThrow(new RuntimeException("shard0 failed"))
                .thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        assertNotNull(logId);
        // 两个分片都被尝试执行
        verify(jobHandler, times(2)).execute(any(), any(com.njydsz.pmis.common.job.ShardingContext.class));
    }

    // ==================== P7-3 租户级配额集成测试 ====================

    @Test
    @DisplayName("P7-3: dispatch(CRON) 配额服务可用时调用 checkConcurrentQuota + checkDailyExecutionQuota")
    void dispatch_cron_quotaServiceAvailable_callsQuotaChecks() throws Exception {
        JobDO job = buildJob("quota-pass", null);
        when(tenantQuotaServiceProvider.getIfAvailable()).thenReturn(tenantQuotaService);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        // P1-7: CRON 异步派发返回 null，但配额检查在派发前同步执行
        assertNull(logId);
        verify(tenantQuotaService, times(1)).checkConcurrentQuota("tenant-test");
        verify(tenantQuotaService, times(1)).checkDailyExecutionQuota("tenant-test");
    }

    @Test
    @DisplayName("P7-3: dispatch(CRON) 并发配额超限时抛 BizException 且不执行任务")
    void dispatch_cron_quotaExceeded_throwsAndNoExecution() throws Exception {
        JobDO job = buildJob("quota-exceeded", null);
        when(tenantQuotaServiceProvider.getIfAvailable()).thenReturn(tenantQuotaService);
        doThrow(new BizException(BizErrorCode.QUOTA_EXCEEDED,
                "error.cronjob.msg_quota_concurrent_exceeded", "tenant-test", 5L, 5))
                .when(tenantQuotaService).checkConcurrentQuota("tenant-test");

        BizException ex = assertThrows(BizException.class,
                () -> dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON));

        assertEquals(BizErrorCode.QUOTA_EXCEEDED.getCode(), ex.getCode());
        verify(jobHandler, never()).execute(any());
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(jobLogMapper, never()).insert(any(JobLogDO.class));
        // 配额超限时不应记录执行计数
        verify(tenantQuotaService, never()).recordExecutionStart(anyString());
        verify(tenantQuotaService, never()).recordExecutionEnd(anyString());
    }

    @Test
    @DisplayName("P7-3: dispatch(CRON) 配额服务异常时降级放行（仍执行任务）")
    void dispatch_cron_quotaServiceException_degradesAndExecutes() throws Exception {
        JobDO job = buildJob("quota-degrade", null);
        when(tenantQuotaServiceProvider.getIfAvailable()).thenReturn(tenantQuotaService);
        // 非 BizException 异常（如 Redis 故障）触发降级放行
        doThrow(new RuntimeException("redis down"))
                .when(tenantQuotaService).checkConcurrentQuota("tenant-test");
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        // P1-7: CRON 异步派发返回 null，但任务已执行（测试用同步线程池）
        assertNull(logId);
        verify(jobHandler, times(1)).execute(any());
        verify(jobLogMapper, times(1)).insert(any(JobLogDO.class));
    }

    @Test
    @DisplayName("P7-3: dispatch(MANUAL) 不触发配额检查")
    void dispatch_manual_doesNotCheckQuota() throws Exception {
        JobDO job = buildJob("quota-manual", null);
        when(tenantQuotaServiceProvider.getIfAvailable()).thenReturn(tenantQuotaService);
        when(jobHandler.execute(any())).thenReturn("ok");

        dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_MANUAL);

        verify(tenantQuotaService, never()).checkConcurrentQuota(anyString());
        verify(tenantQuotaService, never()).checkDailyExecutionQuota(anyString());
    }

    @Test
    @DisplayName("P7-3: dispatch(CRON) 正常执行时调用 recordExecutionStart + recordExecutionEnd")
    void dispatch_cron_normal_callsRecordStartAndEnd() throws Exception {
        JobDO job = buildJob("quota-record-normal", null);
        when(tenantQuotaServiceProvider.getIfAvailable()).thenReturn(tenantQuotaService);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any())).thenReturn("ok");

        dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        verify(tenantQuotaService, times(1)).recordExecutionStart("tenant-test");
        verify(tenantQuotaService, times(1)).recordExecutionEnd("tenant-test");
    }

    @Test
    @DisplayName("P7-3: dispatch(CRON) 执行异常时 finally 块仍调用 recordExecutionEnd")
    void dispatch_cron_executionFails_stillCallsRecordExecutionEnd() throws Exception {
        JobDO job = buildJob("quota-record-fail", null);
        when(tenantQuotaServiceProvider.getIfAvailable()).thenReturn(tenantQuotaService);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any())).thenThrow(new RuntimeException("boom"));

        dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        verify(tenantQuotaService, times(1)).recordExecutionStart("tenant-test");
        // finally 块中调用 recordExecutionEnd，即使执行失败
        verify(tenantQuotaService, times(1)).recordExecutionEnd("tenant-test");
    }

    @Test
    @DisplayName("P7-3: dispatch(CRON) tenantId 为空时跳过配额检查")
    void dispatch_cron_emptyTenantId_skipsQuotaCheck() throws Exception {
        JobDO job = buildJob("quota-empty-tenant", null);
        job.setTenantId(null);
        when(tenantQuotaServiceProvider.getIfAvailable()).thenReturn(tenantQuotaService);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(any())).thenReturn("ok");

        dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        verify(tenantQuotaService, never()).checkConcurrentQuota(anyString());
        verify(tenantQuotaService, never()).recordExecutionStart(anyString());
    }

    private JobDO buildJob(String key, Long lockTtlMs) {
        JobDO job = new JobDO();
        job.setId("job-" + key);
        job.setJobKey(key);
        job.setJobName("测试任务 " + key);
        job.setHandler("testHandler");
        job.setCronExpression("0 0 8 * * ?");
        job.setStatus("NORMAL");
        job.setLockTtlMs(lockTtlMs);
        // P7-3: 默认设置 tenantId，便于配额检查测试
        job.setTenantId("tenant-test");
        return job;
    }
}
