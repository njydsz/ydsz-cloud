package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.alert.AlertTrigger;
import com.njydsz.pmis.cronjob.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.pmis.cronjob.core.executor.JobNodeHeartbeat;
import com.njydsz.pmis.cronjob.core.executor.TenantAwareExecutorPool;
import com.njydsz.pmis.cronjob.core.sharding.ShardingStrategy;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.JobNodeMapper;
import com.njydsz.pmis.cronjob.metrics.CronjobMetrics;
import com.njydsz.pmis.cronjob.service.JobLogContentService;
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
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-6: COVER 阻塞策略单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>COVER 策略中断本节点线程 + 释放锁 + 重新派发</li>
 *   <li>COVER 策略中断失败（线程不响应）降级 DISCARD</li>
 *   <li>COVER 策略远程节点任务无法中断，降级 DISCARD</li>
 *   <li>COVER 策略无 RUNNING 日志时（残留锁）尝试释放并重试</li>
 * </ul>
 *
 * <p>注意：实际中断线程涉及 {@link Thread#getAllStackTraces()}，难以稳定 mock。
 * 测试中通过模拟 RUNNING 日志的 execThreadId 设置一个"已结束"的辅助线程 ID，
 * 让 {@code interruptThread} 找不到线程返回 false（验证降级 DISCARD 路径）。
 * 重新派发成功场景通过 mock 锁释放后第二次 setIfAbsent 返回 TRUE 验证。
 *
 * @author ydsy-pmis-team
 * @since 1.0.0
 */
@DisplayName("COVER 阻塞策略测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class CoverStrategyTest {

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
    private ObjectProvider<JobNodeHeartbeat> jobNodeHeartbeatProvider;
    @Mock
    private ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider;
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
    private ObjectProvider<com.njydsz.pmis.cronjob.core.handler.HttpJobHandler> httpJobHandlerProvider;
    @Mock
    private ObjectProvider<com.njydsz.pmis.cronjob.core.handler.GlueJobHandler> glueJobHandlerProvider;
    @Mock
    private ObjectProvider<com.njydsz.pmis.cronjob.core.handler.ScriptJobHandler> scriptJobHandlerProvider;
    @Mock
    private ObjectProvider<RemoteTaskClient> remoteTaskClientProvider;
    @Mock
    private ObjectProvider<JobLogContentService> jobLogContentServiceProvider;
    @Mock
    private ObjectProvider<com.njydsz.pmis.cronjob.core.map.MapTaskExecutor> mapTaskExecutorProvider;
    @Mock
    private ObjectProvider<TenantAwareExecutorPool> tenantAwareExecutorPoolProvider;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private DefaultTaskDispatcher dispatcher;

    /** 当前节点 ID（用于 COVER 策略判断是否本节点） */
    private static final String CURRENT_NODE_ID = "test-host:9004";

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties
        setField(dispatcher, "cronjobProperties", cronjobProperties);
        // 注入所有 ObjectProvider
        setField(dispatcher, "shardingStrategyProvider", shardingStrategyProvider);
        setField(dispatcher, "alertTriggerProvider", alertTriggerProvider);
        setField(dispatcher, "cronjobMetricsProvider", cronjobMetricsProvider);
        setField(dispatcher, "tenantQuotaServiceProvider", tenantQuotaServiceProvider);
        setField(dispatcher, "httpJobHandlerProvider", httpJobHandlerProvider);
        setField(dispatcher, "glueJobHandlerProvider", glueJobHandlerProvider);
        setField(dispatcher, "scriptJobHandlerProvider", scriptJobHandlerProvider);
        setField(dispatcher, "remoteTaskClientProvider", remoteTaskClientProvider);
        setField(dispatcher, "jobLogContentServiceProvider", jobLogContentServiceProvider);
        setField(dispatcher, "mapTaskExecutorProvider", mapTaskExecutorProvider);
        setField(dispatcher, "tenantAwareExecutorPoolProvider", tenantAwareExecutorPoolProvider);
        setField(dispatcher, "jobNodeHeartbeatProvider", jobNodeHeartbeatProvider);
        setField(dispatcher, "nodeDiscoveryStrategyProvider", nodeDiscoveryStrategyProvider);
        // P2-6: 注入当前节点 ID，用于 COVER 策略判断
        setField(dispatcher, "nodeId", CURRENT_NODE_ID);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(anyString(), eq(JobHandler.class))).thenReturn(jobHandler);
        lenient().when(shardingStrategyProvider.getIfAvailable()).thenReturn(null);
        lenient().when(alertTriggerProvider.getIfAvailable()).thenReturn(null);
        lenient().when(cronjobMetricsProvider.getIfAvailable()).thenReturn(null);
        lenient().when(tenantQuotaServiceProvider.getIfAvailable()).thenReturn(null);
        lenient().when(httpJobHandlerProvider.getIfAvailable()).thenReturn(null);
        lenient().when(glueJobHandlerProvider.getIfAvailable()).thenReturn(null);
        lenient().when(scriptJobHandlerProvider.getIfAvailable()).thenReturn(null);
        lenient().when(remoteTaskClientProvider.getIfAvailable()).thenReturn(null);
        lenient().when(jobLogContentServiceProvider.getIfAvailable()).thenReturn(null);
        lenient().when(mapTaskExecutorProvider.getIfAvailable()).thenReturn(null);
        lenient().when(tenantAwareExecutorPoolProvider.getIfAvailable()).thenReturn(null);
        lenient().when(jobNodeHeartbeatProvider.getIfAvailable()).thenReturn(jobNodeHeartbeat);
        lenient().when(nodeDiscoveryStrategyProvider.getIfAvailable()).thenReturn(null);

        // P1-7: 测试用同步执行线程池（任务在调用线程中执行）
        java.util.concurrent.ThreadPoolExecutor syncPool = new java.util.concurrent.ThreadPoolExecutor(
                1, 1, 0, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(),
                r -> { Thread t = new Thread(r, "test-cover-exec"); t.setDaemon(true); return t; },
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()) {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
        setField(dispatcher, "taskExecutorPool", syncPool);

        lenient().when(jobLogMapper.insert(any(JobLogDO.class))).thenAnswer(invocation -> {
            JobLogDO log = invocation.getArgument(0);
            log.setId("log-cover-" + System.nanoTime());
            return 1;
        });
    }

    /**
     * COVER 策略 + 远程节点任务 → 降级 DISCARD（无法中断远程线程）。
     */
    @Test
    @DisplayName("COVER 策略: 远程节点任务无法中断, 降级 DISCARD")
    void cover_remoteNode_degradesToDiscard() throws Exception {
        JobDO job = buildCoverJob("cover-remote");
        // 锁被持有
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);
        // 模拟 RUNNING 日志在远程节点
        JobLogDO runningLog = new JobLogDO();
        runningLog.setJobKey("cover-remote");
        runningLog.setStatus("RUNNING");
        runningLog.setExecNodeId("remote-host:9004");
        runningLog.setExecThreadId(99999L);
        runningLog.setLockHolder("remote-host:pid");
        when(jobLogMapper.selectOne(any())).thenReturn(runningLog);

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        // 远程节点无法中断，降级 DISCARD 返回 null
        assertNull(logId, "远程节点任务无法中断应返回 null");
        // 不应尝试释放锁（直接降级 DISCARD）
        verify(redisTemplate, never()).execute(any(RedisScript.class), any(), (Object) any());
        // 不应执行任务
        verify(jobHandler, never()).execute(any());
        verify(jobLogMapper, never()).insert(any(JobLogDO.class));
    }

    /**
     * COVER 策略 + 线程不响应中断（线程已不存在） → 降级 DISCARD。
     *
     * <p>这里使用一个不存在的 threadId（99999L），让 interruptThread 找不到线程返回 false。
     */
    @Test
    @DisplayName("COVER 策略: 中断失败时降级 DISCARD")
    void cover_interruptFails_degradesToDiscard() throws Exception {
        JobDO job = buildCoverJob("cover-fail");
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);
        // 本节点任务，但线程 ID 不存在（已结束）
        JobLogDO runningLog = new JobLogDO();
        runningLog.setJobKey("cover-fail");
        runningLog.setStatus("RUNNING");
        runningLog.setExecNodeId(CURRENT_NODE_ID);
        runningLog.setExecThreadId(99999L); // 不存在的线程 ID
        runningLog.setLockHolder("test-holder");
        when(jobLogMapper.selectOne(any())).thenReturn(runningLog);

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        // 中断失败降级 DISCARD
        assertNull(logId, "中断失败应降级 DISCARD 返回 null");
        // 不应释放锁（中断失败直接返回）
        verify(redisTemplate, never()).execute(any(RedisScript.class), any(), (Object) any());
        // 不应执行任务
        verify(jobHandler, never()).execute(any());
    }

    /**
     * COVER 策略 + 无 RUNNING 日志（残留锁） → 尝试释放锁 + 重新派发执行。
     */
    @Test
    @DisplayName("COVER 策略: 无 RUNNING 日志(残留锁) 释放后重新派发")
    void cover_noRunningLog_releasesAndRedispatches() throws Exception {
        JobDO job = buildCoverJob("cover-stale");
        // 第一次 setIfAbsent 失败（锁被持有），第二次成功（释放后重新获取）
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE)
                .thenReturn(Boolean.TRUE);
        // 无 RUNNING 日志
        when(jobLogMapper.selectOne(any())).thenReturn(null);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        // P1-7: CRON 触发走异步派发返回 null，但任务已同步执行（测试用同步线程池）
        assertNull(logId, "CRON 异步派发返回 null");
        // 应调用两次 Lua 脚本释放锁: ① COVER 主动释放残留锁 ② 重新派发任务执行完成后释放新锁
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), any(), (Object) any());
        // 应执行任务（重新派发成功）
        verify(jobHandler, times(1)).execute(any());
        verify(jobLogMapper, times(1)).insert(any(JobLogDO.class));
    }

    /**
     * COVER 策略 + 本节点任务 + 中断成功 → 释放锁 + 重新派发。
     *
     * <p>启动一个辅助线程让它 sleep（保持 alive），记录其 threadId。
     * mock RUNNING 日志使用该 threadId，让 interruptThread 能找到线程并中断。
     * helper 线程响应 InterruptedException 退出，interruptThread 返回 true。
     */
    @Test
    @DisplayName("COVER 策略: 中断本节点线程 + 释放锁 + 重新派发")
    void cover_interruptLocalThread_releasesLockAndRedispatches() throws Exception {
        JobDO job = buildCoverJob("cover-success");
        // 启动一个辅助线程让它 sleep（保持 alive），记录其 threadId
        CountDownLatch started = new CountDownLatch(1);
        long[] threadIdHolder = new long[1];
        Thread helper = new Thread(() -> {
            threadIdHolder[0] = Thread.currentThread().threadId();
            started.countDown();
            try {
                // 长时间 sleep，等待被中断
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                // 响应中断退出
                Thread.currentThread().interrupt();
            }
        }, "cover-helper");
        helper.setDaemon(true);
        helper.start();
        started.await();
        // 等 helper 线程记录 threadId 并进入 sleep
        Thread.sleep(50);

        // 第一次 setIfAbsent 失败（锁被持有），第二次成功（释放后重新获取）
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE)
                .thenReturn(Boolean.TRUE);
        // 本节点任务
        JobLogDO runningLog = new JobLogDO();
        runningLog.setJobKey("cover-success");
        runningLog.setStatus("RUNNING");
        runningLog.setExecNodeId(CURRENT_NODE_ID);
        runningLog.setExecThreadId(threadIdHolder[0]); // helper 线程 ID
        runningLog.setLockHolder("test-holder");
        when(jobLogMapper.selectOne(any())).thenReturn(runningLog);
        when(jobHandler.execute(any())).thenReturn("ok");

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        // P1-7: CRON 异步派发返回 null，但任务已同步执行
        assertNull(logId, "CRON 异步派发返回 null");
        // 应调用两次 Lua 脚本释放锁: ① COVER 中断后主动释放旧锁 ② 重新派发任务执行完成后释放新锁
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), any(), (Object) any());
        // 应执行任务（重新派发成功）
        verify(jobHandler, times(1)).execute(any());
        verify(jobLogMapper, times(1)).insert(any(JobLogDO.class));
        // helper 线程应已退出
        assertEquals(false, helper.isAlive(), "helper 线程应被中断退出");
    }

    /**
     * COVER 策略 + 重新获取锁失败 → 降级 DISCARD。
     */
    @Test
    @DisplayName("COVER 策略: 重新获取锁失败时降级 DISCARD")
    void cover_reAcquireFails_degradesToDiscard() throws Exception {
        JobDO job = buildCoverJob("cover-reacquire-fail");
        // 第一次失败，第二次也失败
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);
        // 无 RUNNING 日志（走残留锁释放路径）
        when(jobLogMapper.selectOne(any())).thenReturn(null);

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        // 重新获取锁失败，降级 DISCARD
        assertNull(logId, "重新获取锁失败应降级 DISCARD");
        // 应调用一次 Lua 脚本释放锁
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(), (Object) any());
        // 不应执行任务
        verify(jobHandler, never()).execute(any());
    }

    /**
     * SERIAL 策略（默认）锁被持有 → 跳过不调用 COVER 逻辑。
     */
    @Test
    @DisplayName("SERIAL 策略: 锁被持有时跳过不调用 COVER 逻辑")
    void serialStrategy_lockHeld_skipsCover() throws Exception {
        JobDO job = buildJob("serial-key", null);
        // blockStrategy=null，默认走 SERIAL 路径
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);

        assertNull(logId, "SERIAL 策略锁被持有应返回 null");
        // 不应调用 Lua 脚本释放锁
        verify(redisTemplate, never()).execute(any(RedisScript.class), any(), (Object) any());
        // 不应查询 RUNNING 日志
        verify(jobLogMapper, never()).selectOne(any());
    }

    // ==================== 辅助方法 ====================

    private JobDO buildCoverJob(String key) {
        JobDO job = new JobDO();
        job.setId("job-" + key);
        job.setJobKey(key);
        job.setJobName("COVER 测试任务 " + key);
        job.setHandler("testHandler");
        job.setCronExpression("0 0 8 * * ?");
        job.setStatus("NORMAL");
        job.setBlockStrategy("COVER");
        job.setTenantId("tenant-test");
        return job;
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
        job.setTenantId("tenant-test");
        return job;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
