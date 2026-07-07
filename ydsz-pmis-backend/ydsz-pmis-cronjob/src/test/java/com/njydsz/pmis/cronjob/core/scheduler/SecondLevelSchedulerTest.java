package com.njydsz.pmis.cronjob.core.scheduler;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.pmis.cronjob.core.dispatch.TaskDispatcher;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SecondLevelScheduler} 单元测试（P0-3）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>register FIXED_RATE 任务成功注册</li>
 *   <li>register FIXED_DELAY 任务成功注册</li>
 *   <li>register API / CRON 类型不注册</li>
 *   <li>register 非 NORMAL 状态不注册</li>
 *   <li>register 非法间隔不注册</li>
 *   <li>unregister 已注册任务成功</li>
 *   <li>unregister 未注册任务返回 false</li>
 *   <li>reload 加载所有 FIXED_RATE/FIXED_DELAY 任务</li>
 *   <li>reload 跳过 CRON/API 类型任务</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SecondLevelScheduler 秒级调度器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecondLevelSchedulerTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private TaskDispatcher taskDispatcher;
    @Mock
    private LeaderElector leaderElector;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private SecondLevelScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties
        try {
            java.lang.reflect.Field f = SecondLevelScheduler.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(scheduler, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }
        // 启动时 selectAllNormal 返回空列表，避免 reload 干扰单个测试用例
        lenient().when(jobMapper.selectAllNormal()).thenReturn(Collections.emptyList());
        // 默认非 Leader，避免调度任务实际派发
        lenient().when(leaderElector.isLeader(anyString())).thenReturn(false);
        // 调用 @PostConstruct 初始化调度器
        scheduler.init();
    }

    @AfterEach
    void tearDown() {
        // 调用 @PreDestroy 关闭线程池，避免线程泄漏
        scheduler.shutdown();
    }

    @Test
    @DisplayName("register FIXED_RATE 任务应成功注册")
    void register_fixedRate_success() {
        JobDO job = buildJob("job-rate-1", "test-rate-1", "FIXED_RATE", 1000L, null);

        boolean result = scheduler.register(job);

        assertTrue(result, "FIXED_RATE 任务应注册成功");
        assertTrue(scheduler.isRegistered("job-rate-1"), "任务应已注册");
        assertEquals(1, scheduler.getRegisteredCount(), "已注册任务数应为 1");
    }

    @Test
    @DisplayName("register FIXED_DELAY 任务应成功注册")
    void register_fixedDelay_success() {
        JobDO job = buildJob("job-delay-1", "test-delay-1", "FIXED_DELAY", null, 2000L);

        boolean result = scheduler.register(job);

        assertTrue(result, "FIXED_DELAY 任务应注册成功");
        assertTrue(scheduler.isRegistered("job-delay-1"), "任务应已注册");
        assertEquals(1, scheduler.getRegisteredCount(), "已注册任务数应为 1");
    }

    @Test
    @DisplayName("register CRON 类型任务不应注册（返回 false）")
    void register_cronType_notRegistered() {
        JobDO job = buildJob("job-cron-1", "test-cron-1", "CRON", null, null);
        job.setCronExpression("0 0 8 * * ?");

        boolean result = scheduler.register(job);

        assertFalse(result, "CRON 类型任务不应由 SecondLevelScheduler 注册");
        assertFalse(scheduler.isRegistered("job-cron-1"), "CRON 任务不应被注册");
        assertEquals(0, scheduler.getRegisteredCount(), "已注册任务数应为 0");
    }

    @Test
    @DisplayName("register API 类型任务不应注册（返回 false）")
    void register_apiType_notRegistered() {
        JobDO job = buildJob("job-api-1", "test-api-1", "API", null, null);

        boolean result = scheduler.register(job);

        assertFalse(result, "API 类型任务不应由 SecondLevelScheduler 注册");
        assertFalse(scheduler.isRegistered("job-api-1"), "API 任务不应被注册");
        assertEquals(0, scheduler.getRegisteredCount(), "已注册任务数应为 0");
    }

    @Test
    @DisplayName("register 非 NORMAL 状态任务不应注册")
    void register_pausedStatus_notRegistered() {
        JobDO job = buildJob("job-paused-1", "test-paused-1", "FIXED_RATE", 1000L, null);
        job.setStatus("PAUSED");

        boolean result = scheduler.register(job);

        assertFalse(result, "非 NORMAL 状态任务不应注册");
        assertEquals(0, scheduler.getRegisteredCount(), "已注册任务数应为 0");
    }

    @Test
    @DisplayName("register FIXED_RATE 非法间隔（null）不应注册")
    void register_fixedRateNullInterval_notRegistered() {
        JobDO job = buildJob("job-rate-null", "test-rate-null", "FIXED_RATE", null, null);

        boolean result = scheduler.register(job);

        assertFalse(result, "fixedRateMs=null 时不应注册");
        assertEquals(0, scheduler.getRegisteredCount(), "已注册任务数应为 0");
    }

    @Test
    @DisplayName("register FIXED_RATE 非法间隔（<=0）不应注册")
    void register_fixedRateZeroInterval_notRegistered() {
        JobDO job = buildJob("job-rate-zero", "test-rate-zero", "FIXED_RATE", 0L, null);

        boolean result = scheduler.register(job);

        assertFalse(result, "fixedRateMs<=0 时不应注册");
        assertEquals(0, scheduler.getRegisteredCount(), "已注册任务数应为 0");
    }

    @Test
    @DisplayName("register FIXED_DELAY 非法间隔（null）不应注册")
    void register_fixedDelayNullInterval_notRegistered() {
        JobDO job = buildJob("job-delay-null", "test-delay-null", "FIXED_DELAY", null, null);

        boolean result = scheduler.register(job);

        assertFalse(result, "fixedDelayMs=null 时不应注册");
        assertEquals(0, scheduler.getRegisteredCount(), "已注册任务数应为 0");
    }

    @Test
    @DisplayName("register null 任务返回 false")
    void register_nullJob_returnsFalse() {
        boolean result = scheduler.register(null);

        assertFalse(result, "null 任务应返回 false");
    }

    @Test
    @DisplayName("unregister 已注册任务应成功")
    void unregister_registered_returnsTrue() {
        JobDO job = buildJob("job-unreg-1", "test-unreg-1", "FIXED_RATE", 1000L, null);
        scheduler.register(job);
        assertEquals(1, scheduler.getRegisteredCount());

        boolean result = scheduler.unregister("job-unreg-1");

        assertTrue(result, "注销已注册任务应返回 true");
        assertFalse(scheduler.isRegistered("job-unreg-1"), "注销后任务不应再被注册");
        assertEquals(0, scheduler.getRegisteredCount(), "注销后已注册任务数应为 0");
    }

    @Test
    @DisplayName("unregister 未注册任务返回 false")
    void unregister_notRegistered_returnsFalse() {
        boolean result = scheduler.unregister("job-not-exist");

        assertFalse(result, "注销未注册任务应返回 false");
    }

    @Test
    @DisplayName("unregister null 参数返回 false")
    void unregister_null_returnsFalse() {
        boolean result = scheduler.unregister(null);

        assertFalse(result, "null 参数应返回 false");
    }

    @Test
    @DisplayName("重新注册同一任务应先注销旧调度（无重复）")
    void register_twice_noDuplicate() {
        JobDO job = buildJob("job-twice", "test-twice", "FIXED_RATE", 1000L, null);

        scheduler.register(job);
        assertEquals(1, scheduler.getRegisteredCount());

        // 再次注册应先注销旧的，再注册新的
        scheduler.register(job);
        assertEquals(1, scheduler.getRegisteredCount(), "重复注册后任务数仍应为 1");
    }

    @Test
    @DisplayName("reload 应加载所有 FIXED_RATE/FIXED_DELAY 任务")
    void reload_loadsAllFixedRateJobs() {
        JobDO rateJob = buildJob("job-reload-rate", "test-reload-rate", "FIXED_RATE", 1000L, null);
        JobDO delayJob = buildJob("job-reload-delay", "test-reload-delay", "FIXED_DELAY", null, 2000L);
        JobDO cronJob = buildJob("job-reload-cron", "test-reload-cron", "CRON", null, null);
        cronJob.setCronExpression("0 0 8 * * ?");
        JobDO apiJob = buildJob("job-reload-api", "test-reload-api", "API", null, null);
        when(jobMapper.selectAllNormal()).thenReturn(List.of(rateJob, delayJob, cronJob, apiJob));

        scheduler.reload();

        assertEquals(2, scheduler.getRegisteredCount(), "应只注册 FIXED_RATE + FIXED_DELAY 共 2 个任务");
        assertTrue(scheduler.isRegistered("job-reload-rate"), "FIXED_RATE 任务应已注册");
        assertTrue(scheduler.isRegistered("job-reload-delay"), "FIXED_DELAY 任务应已注册");
        assertFalse(scheduler.isRegistered("job-reload-cron"), "CRON 任务不应被注册");
        assertFalse(scheduler.isRegistered("job-reload-api"), "API 任务不应被注册");
    }

    @Test
    @DisplayName("reload 空列表不报错")
    void reload_emptyList_noError() {
        when(jobMapper.selectAllNormal()).thenReturn(Collections.emptyList());

        scheduler.reload();

        assertEquals(0, scheduler.getRegisteredCount(), "空列表后已注册任务数应为 0");
    }

    @Test
    @DisplayName("reload 后应先清空旧任务再加载（避免重复）")
    void reload_clearsOldJobsFirst() {
        // 先注册一个任务
        JobDO job1 = buildJob("job-old", "test-old", "FIXED_RATE", 1000L, null);
        scheduler.register(job1);
        assertEquals(1, scheduler.getRegisteredCount());

        // reload 返回不同的任务
        JobDO job2 = buildJob("job-new", "test-new", "FIXED_DELAY", null, 2000L);
        when(jobMapper.selectAllNormal()).thenReturn(List.of(job2));

        scheduler.reload();

        assertEquals(1, scheduler.getRegisteredCount(), "reload 后应只有新任务");
        assertFalse(scheduler.isRegistered("job-old"), "旧任务应被清空");
        assertTrue(scheduler.isRegistered("job-new"), "新任务应被注册");
    }

    @Test
    @DisplayName("Leader 节点派发 FIXED_RATE 任务时调用 TaskDispatcher")
    void dispatch_fixedRate_callsDispatcher() throws Exception {
        // 设置为 Leader
        when(leaderElector.isLeader(anyString())).thenReturn(true);
        JobDO job = buildJob("job-dispatch", "test-dispatch", "FIXED_RATE", 50L, null);
        scheduler.register(job);

        // 等待调度任务执行（initialDelay=0, period=50ms）
        Thread.sleep(200);

        // 验证 dispatch 被调用（至少一次，可能多次因为固定频率）
        verify(taskDispatcher, atLeast(1)).dispatch(eq(job), any(), eq(DefaultTaskDispatcher.TRIGGER_CRON));
    }

    @Test
    @DisplayName("非 Leader 节点不调用 TaskDispatcher")
    void dispatch_notLeader_skipsDispatcher() throws Exception {
        // 非 Leader（setUp 中默认设置）
        when(leaderElector.isLeader(anyString())).thenReturn(false);
        JobDO job = buildJob("job-no-dispatch", "test-no-dispatch", "FIXED_RATE", 50L, null);
        scheduler.register(job);

        // 等待调度任务执行
        Thread.sleep(200);

        // 验证 dispatch 未被调用
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    /**
     * 构造测试用 JobDO。
     *
     * @param id           任务 ID
     * @param key          任务 KEY
     * @param scheduleType 调度类型
     * @param fixedRateMs  固定频率间隔（毫秒）
     * @param fixedDelayMs 固定延迟间隔（毫秒）
     * @return JobDO 实例
     */
    private JobDO buildJob(String id, String key, String scheduleType, Long fixedRateMs, Long fixedDelayMs) {
        JobDO job = new JobDO();
        job.setId(id);
        job.setJobKey(key);
        job.setJobName("测试任务 " + key);
        job.setJobGroup("DEFAULT");
        job.setHandler("testHandler");
        job.setStatus("NORMAL");
        job.setScheduleType(scheduleType);
        job.setFixedRateMs(fixedRateMs);
        job.setFixedDelayMs(fixedDelayMs);
        return job;
    }
}
