package com.njydsz.pmis.cronjob.service;

import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.dispatch.TaskDispatcher;
import com.njydsz.pmis.cronjob.core.scheduler.SecondLevelScheduler;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.service.impl.JobServiceImpl;
import com.njydsz.pmis.cronjob.service.JobHistoryService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobServiceImpl} 批量操作单元测试。
 *
 * <p>覆盖 P2-4 任务批量操作：
 * <ul>
 *   <li>批量暂停（全部成功 / 部分失败不影响其他）</li>
 *   <li>批量恢复</li>
 *   <li>批量触发</li>
 *   <li>批量删除</li>
 *   <li>空列表边界场景</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobServiceImpl 批量操作测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class JobBatchTest {

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
    private TenantQuotaService tenantQuotaService;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private JobServiceImpl jobService;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@RequiredArgsConstructor 在测试构造时已注入，但 properties 对象需要在每次测试前重置）
        try {
            java.lang.reflect.Field f = JobServiceImpl.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(jobService, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }
        // 注入 taskDispatcherProvider（P1-7 可选注入；测试场景默认无 Dispatcher，回退到内部 executeJob 路径）
        try {
            java.lang.reflect.Field f = JobServiceImpl.class.getDeclaredField("taskDispatcherProvider");
            f.setAccessible(true);
            ObjectProvider<TaskDispatcher> emptyProvider =
                    (ObjectProvider<TaskDispatcher>) org.mockito.Mockito.mock(ObjectProvider.class);
            org.mockito.Mockito.when(emptyProvider.getIfAvailable()).thenReturn(null);
            f.set(jobService, emptyProvider);
        } catch (Exception e) {
            throw new IllegalStateException("注入 taskDispatcherProvider 失败", e);
        }
        // P0-3: 注入 secondLevelSchedulerProvider（可选注入；测试场景默认无 SecondLevelScheduler）
        try {
            java.lang.reflect.Field f = JobServiceImpl.class.getDeclaredField("secondLevelSchedulerProvider");
            f.setAccessible(true);
            ObjectProvider<SecondLevelScheduler> emptyProvider =
                    (ObjectProvider<SecondLevelScheduler>) org.mockito.Mockito.mock(ObjectProvider.class);
            org.mockito.Mockito.when(emptyProvider.getIfAvailable()).thenReturn(null);
            f.set(jobService, emptyProvider);
        } catch (Exception e) {
            throw new IllegalStateException("注入 secondLevelSchedulerProvider 失败", e);
        }
        // P1-6: 注入 jobHistoryServiceProvider（可选注入；测试场景默认无 JobHistoryService）
        try {
            java.lang.reflect.Field f = JobServiceImpl.class.getDeclaredField("jobHistoryServiceProvider");
            f.setAccessible(true);
            ObjectProvider<JobHistoryService> emptyProvider =
                    (ObjectProvider<JobHistoryService>) org.mockito.Mockito.mock(ObjectProvider.class);
            org.mockito.Mockito.when(emptyProvider.getIfAvailable()).thenReturn(null);
            f.set(jobService, emptyProvider);
        } catch (Exception e) {
            throw new IllegalStateException("注入 jobHistoryServiceProvider 失败", e);
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

    // ==================== 批量暂停测试 ====================

    @Test
    @DisplayName("batchPause: 全部成功时返回成功数量")
    void batchPause_allSuccess_returnsCount() {
        JobDO job1 = buildJob("pause-key-1", "0 0 8 * * ?");
        JobDO job2 = buildJob("pause-key-2", "0 0 9 * * ?");
        JobDO job3 = buildJob("pause-key-3", "0 0 10 * * ?");
        when(jobMapper.selectById("job-pause-key-1")).thenReturn(job1);
        when(jobMapper.selectById("job-pause-key-2")).thenReturn(job2);
        when(jobMapper.selectById("job-pause-key-3")).thenReturn(job3);

        List<String> jobIds = Arrays.asList("job-pause-key-1", "job-pause-key-2", "job-pause-key-3");
        int success = jobService.batchPause(jobIds);

        assertEquals(3, success, "全部成功时应返回 3");
        verify(jobMapper, times(3)).updateById(any(JobDO.class));
    }

    @Test
    @DisplayName("batchPause: 部分失败不影响其他任务")
    void batchPause_partialFailure_continuesOthers() {
        JobDO job1 = buildJob("pause-partial-1", "0 0 8 * * ?");
        JobDO job3 = buildJob("pause-partial-3", "0 0 10 * * ?");
        when(jobMapper.selectById("job-pause-partial-1")).thenReturn(job1);
        when(jobMapper.selectById("job-pause-partial-2")).thenReturn(null); // 任务不存在 → 抛 BizException
        when(jobMapper.selectById("job-pause-partial-3")).thenReturn(job3);

        List<String> jobIds = Arrays.asList("job-pause-partial-1", "job-pause-partial-2", "job-pause-partial-3");
        int success = jobService.batchPause(jobIds);

        assertEquals(2, success, "部分失败时应返回成功数 2");
        verify(jobMapper, times(2)).updateById(any(JobDO.class));
    }

    // ==================== 批量恢复测试 ====================

    @Test
    @DisplayName("batchResume: 全部成功时返回成功数量")
    void batchResume_allSuccess_returnsCount() {
        // Leader 模式启用，避免 register 时 taskScheduler NPE
        cronjobProperties.getLeader().setEnabled(true);
        JobDO job1 = buildJob("resume-key-1", "0 0 8 * * ?");
        job1.setStatus("PAUSED");
        job1.setNextFireTime(LocalDateTime.now().plusHours(1)); // 避免 register 额外调用 updateById
        JobDO job2 = buildJob("resume-key-2", "0 0 9 * * ?");
        job2.setStatus("PAUSED");
        job2.setNextFireTime(LocalDateTime.now().plusHours(1));
        when(jobMapper.selectById("job-resume-key-1")).thenReturn(job1);
        when(jobMapper.selectById("job-resume-key-2")).thenReturn(job2);

        List<String> jobIds = Arrays.asList("job-resume-key-1", "job-resume-key-2");
        int success = jobService.batchResume(jobIds);

        assertEquals(2, success, "全部成功时应返回 2");
        verify(jobMapper, times(2)).updateById(any(JobDO.class));
    }

    @Test
    @DisplayName("batchResume: 部分失败不影响其他任务")
    void batchResume_partialFailure_continuesOthers() {
        cronjobProperties.getLeader().setEnabled(true);
        JobDO job1 = buildJob("resume-partial-1", "0 0 8 * * ?");
        job1.setStatus("PAUSED");
        job1.setNextFireTime(LocalDateTime.now().plusHours(1)); // 避免 register 额外调用 updateById
        when(jobMapper.selectById("job-resume-partial-1")).thenReturn(job1);
        when(jobMapper.selectById("job-resume-partial-2")).thenReturn(null); // 任务不存在
        when(jobMapper.selectById("job-resume-partial-3")).thenReturn(null); // 任务不存在

        List<String> jobIds = Arrays.asList("job-resume-partial-1", "job-resume-partial-2", "job-resume-partial-3");
        int success = jobService.batchResume(jobIds);

        assertEquals(1, success, "部分失败时应返回成功数 1");
    }

    // ==================== 批量触发测试 ====================

    @Test
    @DisplayName("batchTrigger: 全部成功时返回成功数量")
    void batchTrigger_allSuccess_returnsCount() throws Exception {
        JobDO job1 = buildJob("trigger-key-1", "0 0 8 * * ?");
        JobDO job2 = buildJob("trigger-key-2", "0 0 9 * * ?");
        when(jobMapper.selectById("job-trigger-key-1")).thenReturn(job1);
        when(jobMapper.selectById("job-trigger-key-2")).thenReturn(job2);
        when(jobHandler.execute(anyString())).thenReturn("ok");

        List<String> jobIds = Arrays.asList("job-trigger-key-1", "job-trigger-key-2");
        int success = jobService.batchTrigger(jobIds);

        assertEquals(2, success, "全部成功时应返回 2");
        verify(jobLogMapper, times(2)).insert(any(JobLogDO.class));
    }

    @Test
    @DisplayName("batchTrigger: 部分失败不影响其他任务")
    void batchTrigger_partialFailure_continuesOthers() throws Exception {
        JobDO job1 = buildJob("trigger-partial-1", "0 0 8 * * ?");
        when(jobMapper.selectById("job-trigger-partial-1")).thenReturn(job1);
        when(jobMapper.selectById("job-trigger-partial-2")).thenReturn(null); // 任务不存在
        when(jobHandler.execute(anyString())).thenReturn("ok");

        List<String> jobIds = Arrays.asList("job-trigger-partial-1", "job-trigger-partial-2");
        int success = jobService.batchTrigger(jobIds);

        assertEquals(1, success, "部分失败时应返回成功数 1");
        verify(jobLogMapper, times(1)).insert(any(JobLogDO.class));
    }

    // ==================== 批量删除测试 ====================

    @Test
    @DisplayName("batchDelete: 全部成功时返回成功数量")
    void batchDelete_allSuccess_returnsCount() {
        JobDO job1 = buildJob("delete-key-1", "0 0 8 * * ?");
        JobDO job2 = buildJob("delete-key-2", "0 0 9 * * ?");
        when(jobMapper.selectById("job-delete-key-1")).thenReturn(job1);
        when(jobMapper.selectById("job-delete-key-2")).thenReturn(job2);

        List<String> jobIds = Arrays.asList("job-delete-key-1", "job-delete-key-2");
        int success = jobService.batchDelete(jobIds);

        assertEquals(2, success, "全部成功时应返回 2");
        verify(jobMapper, times(2)).deleteById(anyString());
    }

    @Test
    @DisplayName("batchDelete: 部分失败不影响其他任务")
    void batchDelete_partialFailure_continuesOthers() {
        JobDO job1 = buildJob("delete-partial-1", "0 0 8 * * ?");
        JobDO job3 = buildJob("delete-partial-3", "0 0 10 * * ?");
        when(jobMapper.selectById("job-delete-partial-1")).thenReturn(job1);
        when(jobMapper.selectById("job-delete-partial-2")).thenReturn(null); // 任务不存在
        when(jobMapper.selectById("job-delete-partial-3")).thenReturn(job3);

        List<String> jobIds = Arrays.asList("job-delete-partial-1", "job-delete-partial-2", "job-delete-partial-3");
        int success = jobService.batchDelete(jobIds);

        assertEquals(2, success, "部分失败时应返回成功数 2");
        verify(jobMapper, times(2)).deleteById(anyString());
    }

    // ==================== 空列表边界测试 ====================

    @Test
    @DisplayName("batchPause: 空列表返回 0 且不调用任何单条操作")
    void batchPause_emptyList_returnsZero() {
        int success = jobService.batchPause(Collections.emptyList());

        assertEquals(0, success, "空列表应返回 0");
        verify(jobMapper, never()).selectById(anyString());
    }

    @Test
    @DisplayName("batchResume: 空列表返回 0")
    void batchResume_emptyList_returnsZero() {
        int success = jobService.batchResume(Collections.emptyList());

        assertEquals(0, success, "空列表应返回 0");
        verify(jobMapper, never()).selectById(anyString());
    }

    @Test
    @DisplayName("batchTrigger: 空列表返回 0")
    void batchTrigger_emptyList_returnsZero() {
        int success = jobService.batchTrigger(Collections.emptyList());

        assertEquals(0, success, "空列表应返回 0");
        verify(jobMapper, never()).selectById(anyString());
    }

    @Test
    @DisplayName("batchDelete: 空列表返回 0")
    void batchDelete_emptyList_returnsZero() {
        int success = jobService.batchDelete(Collections.emptyList());

        assertEquals(0, success, "空列表应返回 0");
        verify(jobMapper, never()).selectById(anyString());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用 JobDO。
     *
     * @param key 任务 KEY
     * @param cron cron 表达式
     * @return 测试用任务定义
     */
    private JobDO buildJob(String key, String cron) {
        JobDO job = new JobDO();
        job.setId("job-" + key);
        job.setJobKey(key);
        job.setJobName("测试任务 " + key);
        job.setJobGroup("DEFAULT");
        job.setHandler("testHandler");
        job.setCronExpression(cron);
        job.setStatus("NORMAL");
        return job;
    }
}
