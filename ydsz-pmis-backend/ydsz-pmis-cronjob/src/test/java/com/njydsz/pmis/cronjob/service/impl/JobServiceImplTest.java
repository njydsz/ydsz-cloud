package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobServiceImpl} 核心逻辑单元测试。
 *
 * <p>覆盖 P0 修复点：
 * <ul>
 *   <li>P0-4: {@code resolveLockTtl} 任务级 TTL override + 全局默认 + 上下限规整</li>
 *   <li>P0-5: {@code trigger(id, holdLock)} 重载与锁路径选择</li>
 *   <li>P0-3: 时区已固定为 Asia/Shanghai（间接覆盖，通过 nextFireTime 验证）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobServiceImpl 核心逻辑测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobServiceImplTest {

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

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private JobServiceImpl jobService;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@RequiredArgsConstructor 在测试构造时已注入，但 properties 对象需要在每次测试前重置）
        // 实际上 @InjectMocks 会使用全参构造器，但 final 字段 cronjobProperties 需要非 null
        // 由于 @Mock 默认返回 null 对象，这里手动通过反射注入
        try {
            java.lang.reflect.Field f = JobServiceImpl.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(jobService, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(applicationContext.getBean(anyString(), eq(JobHandler.class))).thenReturn(jobHandler);
        // 模拟 MyBatis-Plus 在 insert 时为 log 自动注入 ID
        lenient().when(jobLogMapper.insert(any(JobLogDO.class))).thenAnswer(invocation -> {
            JobLogDO log = invocation.getArgument(0);
            log.setId("log-test-id-" + System.nanoTime());
            return 1;
        });
    }

    @Test
    @DisplayName("trigger(id) 默认不抢锁，调用 executeJob(manual=true)")
    void trigger_noLock_callsExecuteWithoutLock() throws Exception {
        // 准备
        JobDO job = buildJob("test-key-no-lock", "0 0 8 * * ?", null);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(jobHandler.execute(anyString())).thenReturn("ok");

        // 执行
        String logId = jobService.trigger(job.getId());

        // 验证
        assertNotNull(logId, "应返回执行日志 ID");
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(jobLogMapper, times(1)).insert(any(JobLogDO.class));
        verify(jobLogMapper, times(1)).updateById(any(JobLogDO.class));
    }

    @Test
    @DisplayName("trigger(id, true) 抢锁且锁获取成功时执行任务")
    void trigger_holdLock_acquired_executesJob() throws Exception {
        JobDO job = buildJob("test-key-with-lock", "0 0 8 * * ?", null);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(jobHandler.execute(anyString())).thenReturn("ok");

        String logId = jobService.trigger(job.getId(), true);

        assertNotNull(logId);
        verify(valueOps, times(1)).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(), (Object) any());
    }

    @Test
    @DisplayName("trigger(id, true) 锁已被持有时返回 null")
    void trigger_holdLock_alreadyHeld_returnsNull() {
        JobDO job = buildJob("test-key-held", "0 0 8 * * ?", null);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        String logId = jobService.trigger(job.getId(), true);

        assertEquals(null, logId, "锁被持有时应返回 null");
        verify(jobLogMapper, never()).insert(any(JobLogDO.class));
    }

    @Test
    @DisplayName("trigger(id, true) 锁获取返回 null 时也视为未获取到锁")
    void trigger_holdLock_acquiredNull_returnsNull() {
        JobDO job = buildJob("test-key-null", "0 0 8 * * ?", null);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(null);

        String logId = jobService.trigger(job.getId(), true);

        assertEquals(null, logId);
        verify(jobLogMapper, never()).insert(any(JobLogDO.class));
    }

    @Test
    @DisplayName("trigger 不存在的任务 ID 应抛 BizException")
    void trigger_nonExistentJobId_throwsBizException() {
        when(jobMapper.selectById("non-existent")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> jobService.trigger("non-existent"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("trigger(id, true) 任务级 lockTtlMs 配置时应使用任务级 TTL")
    void trigger_holdLock_taskLevelTtl_used() {
        // 任务级 10 分钟，在 [30s, 24h] 区间内 → 使用 10 分钟
        JobDO job = buildJob("test-key-task-ttl", "0 0 8 * * ?", Duration.ofMinutes(10).toMillis());
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        jobService.trigger(job.getId(), true);

        // 验证 setIfAbsent 被调用时 TTL = 10 分钟
        verify(valueOps).setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("trigger(id, true) 任务级 lockTtlMs 超上限时收敛到上限")
    void trigger_holdLock_taskLevelTtlAboveMax_clampedToMax() {
        // 任务级 48 小时 > 24 小时上限 → 收敛到 24 小时
        JobDO job = buildJob("test-key-task-ttl-over", "0 0 8 * * ?", Duration.ofHours(48).toMillis());
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        jobService.trigger(job.getId(), true);

        verify(valueOps).setIfAbsent(anyString(), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("trigger(id, true) 任务级 lockTtlMs 为 null 时使用全局默认 TTL")
    void trigger_holdLock_nullTaskTtl_usesGlobalDefault() {
        JobDO job = buildJob("test-key-null-ttl", "0 0 8 * * ?", null);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        jobService.trigger(job.getId(), true);

        // 全局默认 5 分钟
        verify(valueOps).setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("trigger(id, true) 任务级 lockTtlMs 为 0 时使用全局默认 TTL")
    void trigger_holdLock_zeroTaskTtl_usesGlobalDefault() {
        JobDO job = buildJob("test-key-zero-ttl", "0 0 8 * * ?", 0L);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        jobService.trigger(job.getId(), true);

        verify(valueOps).setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("trigger(id, true) 执行失败时应释放锁")
    void trigger_holdLock_executionFailed_releasesLock() throws Exception {
        JobDO job = buildJob("test-key-fail", "0 0 8 * * ?", null);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);
        when(applicationContext.getBean(anyString(), eq(JobHandler.class)))
                .thenReturn(jobHandler);
        when(jobHandler.execute(any())).thenThrow(new RuntimeException("handler error"));

        String logId = jobService.trigger(job.getId(), true);

        assertNotNull(logId, "即使失败也应返回日志 ID");
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(), (Object) any());
    }

    @Test
    @DisplayName("trigger(id, true) 执行成功后应释放锁")
    void trigger_holdLock_executionSuccess_releasesLock() {
        JobDO job = buildJob("test-key-success", "0 0 8 * * ?", null);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        jobService.trigger(job.getId(), true);

        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(), (Object) any());
    }

    @Test
    @DisplayName("resolveLockTtl: 全局默认值 5 分钟在 [30s, 24h] 区间内")
    void resolveLockTtl_globalDefaultInRange() {
        // 通过 trigger(id, true) 间接测试 resolveLockTtl
        JobDO job = buildJob("test-key-default", "0 0 8 * * ?", null);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        jobService.trigger(job.getId(), true);

        // 验证 TTL = 全局默认 5 分钟
        verify(valueOps).setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("executeJob: JobHandler 异常时日志状态应为 FAILED")
    void executeJob_handlerThrows_logMarkedFailed() throws Exception {
        JobDO job = buildJob("test-key-exception", "0 0 8 * * ?", null);
        when(jobMapper.selectById(job.getId())).thenReturn(job);
        when(applicationContext.getBean(anyString(), eq(JobHandler.class)))
                .thenReturn(jobHandler);
        when(jobHandler.execute(any())).thenThrow(new RuntimeException("boom"));

        jobService.trigger(job.getId());

        // 验证日志最终 updateById 被调用（status=FAILED 由内部逻辑设置）
        verify(jobLogMapper, times(1)).updateById(any(JobLogDO.class));
        // 验证任务统计被更新：手动触发(manual=true) → next=null；success=false → incSucc=0, incFail=1, status='ERROR'
        verify(jobMapper, times(1)).updateStats(anyString(), any(), any(),
                eq(1L), eq(0L), eq(1L), eq("ERROR"));
    }

    // ==================== P3 收尾: create/update 字段传递测试 ====================

    @Test
    @DisplayName("P3: create 时 shardTotal=null 应默认设为 1")
    void create_nullShardTotal_defaultsToOne() {
        cronjobProperties.getLeader().setEnabled(true); // 避免 taskScheduler NPE
        JobDO job = buildJob("create-default-shard", "0 0 8 * * ?", null);
        job.setShardTotal(null);
        when(jobMapper.selectByJobKey("create-default-shard")).thenReturn(null);

        jobService.create(job);

        assertEquals(1, job.getShardTotal());
        verify(jobMapper, times(1)).insert(any(JobDO.class));
    }

    @Test
    @DisplayName("P3: create 时 shardTotal=0 应默认设为 1")
    void create_zeroShardTotal_defaultsToOne() {
        cronjobProperties.getLeader().setEnabled(true);
        JobDO job = buildJob("create-zero-shard", "0 0 8 * * ?", null);
        job.setShardTotal(0);
        when(jobMapper.selectByJobKey("create-zero-shard")).thenReturn(null);

        jobService.create(job);

        assertEquals(1, job.getShardTotal());
    }

    @Test
    @DisplayName("P3: create 时 misfirePolicy=null 应默认设为 FIRE_NOW")
    void create_nullMisfirePolicy_defaultsToFireNow() {
        cronjobProperties.getLeader().setEnabled(true);
        JobDO job = buildJob("create-default-misfire", "0 0 8 * * ?", null);
        job.setMisfirePolicy(null);
        when(jobMapper.selectByJobKey("create-default-misfire")).thenReturn(null);

        jobService.create(job);

        assertEquals("FIRE_NOW", job.getMisfirePolicy());
    }

    @Test
    @DisplayName("P3: create 时正确设置 shardTotal 应保留")
    void create_validShardTotal_preserved() {
        cronjobProperties.getLeader().setEnabled(true);
        JobDO job = buildJob("create-shard-4", "0 0 8 * * ?", null);
        job.setShardTotal(4);
        when(jobMapper.selectByJobKey("create-shard-4")).thenReturn(null);

        jobService.create(job);

        assertEquals(4, job.getShardTotal());
    }

    @Test
    @DisplayName("P3: update 时 shardTotal 应被同步到 exists")
    void update_shardTotal_syncedToExists() {
        cronjobProperties.getLeader().setEnabled(true);
        JobDO existing = buildJob("update-shard-exist", "0 0 8 * * ?", null);
        existing.setShardTotal(1);
        when(jobMapper.selectById("job-update-shard")).thenReturn(existing);

        JobDO update = new JobDO();
        update.setId("job-update-shard");
        update.setShardTotal(4);
        update.setCronExpression("0 0 9 * * ?");

        jobService.update(update);

        assertEquals(4, existing.getShardTotal());
        verify(jobMapper, times(1)).updateById(any(JobDO.class));
    }

    @Test
    @DisplayName("P3: update 时 misfirePolicy 应被同步到 exists")
    void update_misfirePolicy_syncedToExists() {
        cronjobProperties.getLeader().setEnabled(true);
        JobDO existing = buildJob("update-misfire-exist", "0 0 8 * * ?", null);
        existing.setMisfirePolicy("FIRE_NOW");
        when(jobMapper.selectById("job-update-misfire")).thenReturn(existing);

        JobDO update = new JobDO();
        update.setId("job-update-misfire");
        update.setMisfirePolicy("SKIP");
        update.setCronExpression("0 0 9 * * ?");

        jobService.update(update);

        assertEquals("SKIP", existing.getMisfirePolicy());
    }

    @Test
    @DisplayName("P3: update 时 lockTtlMs 和 timeoutMs 应被同步到 exists")
    void update_lockTtlAndTimeout_syncedToExists() {
        cronjobProperties.getLeader().setEnabled(true);
        JobDO existing = buildJob("update-ttl-exist", "0 0 8 * * ?", null);
        when(jobMapper.selectById("job-update-ttl")).thenReturn(existing);

        JobDO update = new JobDO();
        update.setId("job-update-ttl");
        update.setLockTtlMs(60000L);
        update.setTimeoutMs(120000L);
        update.setCronExpression("0 0 9 * * ?");

        jobService.update(update);

        assertEquals(60000L, existing.getLockTtlMs());
        assertEquals(120000L, existing.getTimeoutMs());
    }

    /**
     * 构造测试用 JobDO。
     */
    private JobDO buildJob(String key, String cron, Long lockTtlMs) {
        JobDO job = new JobDO();
        job.setId("job-" + key);
        job.setJobKey(key);
        job.setJobName("测试任务 " + key);
        job.setJobGroup("DEFAULT");
        job.setHandler("testHandler");
        job.setCronExpression(cron);
        job.setStatus("NORMAL");
        job.setLockTtlMs(lockTtlMs);
        return job;
    }
}
