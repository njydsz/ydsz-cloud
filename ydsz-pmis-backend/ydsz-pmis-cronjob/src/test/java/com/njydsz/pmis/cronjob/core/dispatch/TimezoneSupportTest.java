package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.scheduler.SecondLevelScheduler;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.service.JobHistoryService;
import com.njydsz.pmis.cronjob.service.TenantQuotaService;
import com.njydsz.pmis.cronjob.service.impl.JobServiceImpl;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * P2-8 多时区支持单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>默认时区（null → Asia/Shanghai）</li>
 *   <li>自定义时区（America/New_York）</li>
 *   <li>UTC 时区</li>
 *   <li>无效时区抛 BizException</li>
 *   <li>不同时区下 nextFireTime 计算结果不同</li>
 * </ul>
 *
 * <p>通过 {@link JobServiceImpl#create(JobDO)} 间接验证 nextFireTime 的时区感知计算，
 * 因为 create 方法内部调用 {@code nextFireTime(job)} 并将结果写入 {@link JobDO#getNextFireTime()}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("P2-8 多时区支持测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class TimezoneSupportTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private TenantQuotaService tenantQuotaService;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private JobServiceImpl jobService;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties
        try {
            java.lang.reflect.Field f = JobServiceImpl.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(jobService, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }
        // 注入空的 taskDispatcherProvider（回退到内部 executeJob 路径）
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
        // 注入空的 secondLevelSchedulerProvider
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
        // 注入空的 jobHistoryServiceProvider
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

        // Leader 模式：避免 register 时 taskScheduler NPE
        cronjobProperties.getLeader().setEnabled(true);
        lenient().when(jobMapper.selectByJobKey(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("P2-8: timezone=null 时使用默认 Asia/Shanghai 计算 nextFireTime")
    void nextFireTime_nullTimezone_usesDefault() {
        JobDO job = buildJob("tz-default", "0 * * * * ?", null);

        jobService.create(job);

        assertNotNull(job.getNextFireTime(), "timezone=null 时 nextFireTime 应已计算");
    }

    @Test
    @DisplayName("P2-8: timezone=America/New_York 时正确计算 nextFireTime")
    void nextFireTime_newYorkTimezone_computesSuccessfully() {
        JobDO job = buildJob("tz-ny", "0 * * * * ?", "America/New_York");

        jobService.create(job);

        assertNotNull(job.getNextFireTime(), "America/New_York 时区 nextFireTime 应已计算");
    }

    @Test
    @DisplayName("P2-8: timezone=UTC 时正确计算 nextFireTime")
    void nextFireTime_utcTimezone_computesSuccessfully() {
        JobDO job = buildJob("tz-utc", "0 * * * * ?", "UTC");

        jobService.create(job);

        assertNotNull(job.getNextFireTime(), "UTC 时区 nextFireTime 应已计算");
    }

    @Test
    @DisplayName("P2-8: 无效时区 ID 抛 BizException")
    void nextFireTime_invalidTimezone_throwsBizException() {
        JobDO job = buildJob("tz-invalid", "0 * * * * ?", "Invalid/NotAZone");

        BizException ex = assertThrows(BizException.class, () -> jobService.create(job));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode(), "无效时区应抛 BAD_REQUEST");
    }

    @Test
    @DisplayName("P2-8: 不同时区下 nextFireTime 计算结果不同")
    void nextFireTime_differentTimezones_produceDifferentResults() {
        // 使用每分钟触发的 cron，确保不同时区的当前时间不同 → nextFireTime 不同
        JobDO jobShanghai = buildJob("tz-diff-shanghai", "0 * * * * ?", "Asia/Shanghai");
        JobDO jobNewYork = buildJob("tz-diff-newyork", "0 * * * * ?", "America/New_York");

        jobService.create(jobShanghai);
        jobService.create(jobNewYork);

        assertNotNull(jobShanghai.getNextFireTime(), "Shanghai nextFireTime 应已计算");
        assertNotNull(jobNewYork.getNextFireTime(), "New York nextFireTime 应已计算");
        // Asia/Shanghai 与 America/New_York 相差约 13 小时，每分钟触发的 cron 会产生不同的 LocalDateTime
        assertNotEquals(jobShanghai.getNextFireTime(), jobNewYork.getNextFireTime(),
                "不同时区下 nextFireTime 应不同");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用 JobDO。
     *
     * @param key      任务 KEY
     * @param cron     cron 表达式
     * @param timezone 时区 ID（null 使用默认）
     * @return 测试用任务定义
     */
    private JobDO buildJob(String key, String cron, String timezone) {
        JobDO job = new JobDO();
        job.setId("job-" + key);
        job.setJobKey(key);
        job.setJobName("测试任务 " + key);
        job.setJobGroup("DEFAULT");
        job.setHandler("testHandler");
        job.setCronExpression(cron);
        job.setStatus("NORMAL");
        job.setTimezone(timezone);
        return job;
    }
}
