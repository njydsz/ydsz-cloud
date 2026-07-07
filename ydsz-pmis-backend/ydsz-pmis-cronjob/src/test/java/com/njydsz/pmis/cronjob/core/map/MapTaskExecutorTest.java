package com.njydsz.pmis.cronjob.core.map;

import com.njydsz.pmis.common.job.MapContext;
import com.njydsz.pmis.common.job.MapProcessor;
import com.njydsz.pmis.common.job.MapReduceProcessor;
import com.njydsz.pmis.common.job.MapTask;
import com.njydsz.pmis.common.job.ProcessResult;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.entity.JobTaskDO;
import com.njydsz.pmis.cronjob.mapper.JobTaskMapper;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MapTaskExecutor} 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>root task 无子任务（纯 Map，不产生子任务）</li>
 *   <li>root task 产生 3 个子任务，全部成功</li>
 *   <li>子任务部分失败（不影响其他子任务执行）</li>
 *   <li>MapReduceProcessor 的 reduce 调用</li>
 *   <li>root task 失败时不产生子任务</li>
 *   <li>子任务调用 map() 被忽略</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MapTaskExecutor MapReduce 任务执行器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapTaskExecutorTest {

    @Mock
    private JobTaskMapper jobTaskMapper;
    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private MapTaskExecutor executor;

    @BeforeEach
    void setUp() {
        // jobTaskMapper.insert 默认填充 ID（模拟 MyBatis Plus 行为）
        lenient().when(jobTaskMapper.insert(any(JobTaskDO.class))).thenAnswer(invocation -> {
            JobTaskDO task = invocation.getArgument(0);
            task.setId("task-" + System.nanoTime());
            return 1;
        });
        // jobTaskMapper.updateStatus 默认返回 1
        lenient().when(jobTaskMapper.updateStatus(anyString(), anyString(), any(), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("root task 无子任务时直接返回 root 结果")
    void executeMapJob_noSubTasks_returnsRootResult() throws Exception {
        JobDO job = buildJob("no-subtasks", "testMapProcessor");
        JobLogDO log0 = buildLog(job);
        MapProcessor processor = (context) -> ProcessResult.success("root done");
        when(applicationContext.getBean("testMapProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        assertTrue(result.isSuccess());
        assertEquals("root done", result.getResult());
        // 仅插入 ROOT TaskDO，无子任务
        verify(jobTaskMapper, times(1)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("root task 产生 3 个子任务全部成功")
    void executeMapJob_threeSubTasksAllSuccess() throws Exception {
        JobDO job = buildJob("three-success", "testMapProcessor");
        JobLogDO log0 = buildLog(job);
        MapProcessor processor = (context) -> {
            if (context.isRootTask()) {
                List<MapTask> subTasks = Arrays.asList(
                        new MapTask("sub-1", "params-1"),
                        new MapTask("sub-2", "params-2"),
                        new MapTask("sub-3", "params-3"));
                context.map(subTasks);
                return ProcessResult.success();
            }
            return ProcessResult.success("processed-" + context.getTaskName());
        };
        when(applicationContext.getBean("testMapProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        assertTrue(result.isSuccess());
        // ROOT + 3 子任务 = 4 次 insert
        verify(jobTaskMapper, times(4)).insert(any(JobTaskDO.class));
        // ROOT + 3 子任务 = 4 次 updateStatus（每个任务 RUNNING→最终状态 2 次，但首次 updateStatus 到 RUNNING + 完成 updateStatus）
        // 实际：每个任务 2 次 updateStatus（1 次 RUNNING + 1 次 SUCCESS/FAILED）
        verify(jobTaskMapper, times(8)).updateStatus(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("子任务部分失败不影响其他子任务执行")
    void executeMapJob_partialFailure_continuesOthers() throws Exception {
        JobDO job = buildJob("partial-fail", "testMapProcessor");
        JobLogDO log0 = buildLog(job);
        MapProcessor processor = (context) -> {
            if (context.isRootTask()) {
                context.map(Arrays.asList(
                        new MapTask("sub-ok-1", "p1"),
                        new MapTask("sub-fail", "p2"),
                        new MapTask("sub-ok-2", "p3")));
                return ProcessResult.success();
            }
            if ("sub-fail".equals(context.getTaskName())) {
                return ProcessResult.failed("intentional failure");
            }
            return ProcessResult.success("ok-" + context.getTaskName());
        };
        when(applicationContext.getBean("testMapProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        // 非 MapReduceProcessor，root 成功即整体成功
        assertTrue(result.isSuccess());
        // ROOT + 3 子任务 = 4 次 insert（即使有子任务失败也全部执行）
        verify(jobTaskMapper, times(4)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("MapReduceProcessor 的 reduce 被调用并返回汇总结果")
    void executeMapJob_mapReduce_reduceCalled() throws Exception {
        JobDO job = buildJob("map-reduce", "testMapReduceProcessor");
        JobLogDO log0 = buildLog(job);
        MapReduceProcessor processor = new MapReduceProcessor() {
            @Override
            public ProcessResult process(MapContext context) throws Exception {
                if (context.isRootTask()) {
                    context.map(Arrays.asList(
                            new MapTask("sub-1", "1"),
                            new MapTask("sub-2", "2"),
                            new MapTask("sub-3", "3")));
                    return ProcessResult.success();
                }
                return ProcessResult.success(context.getTaskParams());
            }

            @Override
            public ProcessResult reduce(MapContext context, List<ProcessResult> taskResults) throws Exception {
                int sum = 0;
                for (ProcessResult r : taskResults) {
                    if (r.isSuccess() && r.getResult() != null) {
                        sum += Integer.parseInt(r.getResult());
                    }
                }
                return ProcessResult.success("sum=" + sum);
            }
        };
        when(applicationContext.getBean("testMapReduceProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        assertTrue(result.isSuccess());
        assertEquals("sum=6", result.getResult());
        // ROOT + 3 子任务 = 4 次 insert
        verify(jobTaskMapper, times(4)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("root task 失败时不产生子任务")
    void executeMapJob_rootFails_noSubTasks() throws Exception {
        JobDO job = buildJob("root-fail", "testMapProcessor");
        JobLogDO log0 = buildLog(job);
        MapProcessor processor = (context) -> ProcessResult.failed("root failed");
        when(applicationContext.getBean("testMapProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        assertFalse(result.isSuccess());
        assertEquals("root failed", result.getErrorMessage());
        // 仅 ROOT TaskDO，无子任务
        verify(jobTaskMapper, times(1)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("子任务调用 map() 被忽略")
    void executeMapJob_subTaskMapCallIgnored() throws Exception {
        JobDO job = buildJob("sub-map-ignored", "testMapProcessor");
        JobLogDO log0 = buildLog(job);
        MapProcessor processor = (context) -> {
            if (context.isRootTask()) {
                context.map(List.of(new MapTask("sub-1", "p1")));
                return ProcessResult.success();
            }
            // 子任务内调用 map() 应被忽略（isRootTask=false）
            context.map(List.of(new MapTask("illegal-sub", "illegal")));
            return ProcessResult.success("done");
        };
        when(applicationContext.getBean("testMapProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        assertTrue(result.isSuccess());
        // ROOT + 1 子任务 = 2 次 insert（子任务调用 map() 产生的 illegal-sub 不应被创建）
        verify(jobTaskMapper, times(2)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("root task 抛异常时返回失败结果")
    void executeMapJob_rootThrows_returnsFailed() throws Exception {
        JobDO job = buildJob("root-throws", "testMapProcessor");
        JobLogDO log0 = buildLog(job);
        MapProcessor processor = (context) -> {
            if (context.isRootTask()) {
                throw new RuntimeException("root exception");
            }
            return ProcessResult.success();
        };
        when(applicationContext.getBean("testMapProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("root exception"));
        // 仅 ROOT TaskDO，异常后不产生子任务
        verify(jobTaskMapper, times(1)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("子任务抛异常时记录 FAILED 状态且继续其他子任务")
    void executeMapJob_subTaskThrows_recordsFailedAndContinues() throws Exception {
        JobDO job = buildJob("sub-throws", "testMapProcessor");
        JobLogDO log0 = buildLog(job);
        MapProcessor processor = (context) -> {
            if (context.isRootTask()) {
                context.map(Arrays.asList(
                        new MapTask("sub-ok", "p1"),
                        new MapTask("sub-throw", "p2")));
                return ProcessResult.success();
            }
            if ("sub-throw".equals(context.getTaskName())) {
                throw new RuntimeException("sub task exception");
            }
            return ProcessResult.success("ok");
        };
        when(applicationContext.getBean("testMapProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        // 非 MapReduceProcessor，root 成功即整体成功
        assertTrue(result.isSuccess());
        // ROOT + 2 子任务 = 3 次 insert（异常子任务也创建了 TaskDO）
        verify(jobTaskMapper, times(3)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("MapReduceProcessor reduce 抛异常时返回失败结果")
    void executeMapJob_reduceThrows_returnsFailed() throws Exception {
        JobDO job = buildJob("reduce-throws", "testMapReduceProcessor");
        JobLogDO log0 = buildLog(job);
        MapReduceProcessor processor = new MapReduceProcessor() {
            @Override
            public ProcessResult process(MapContext context) throws Exception {
                if (context.isRootTask()) {
                    context.map(List.of(new MapTask("sub-1", "p1")));
                    return ProcessResult.success();
                }
                return ProcessResult.success("ok");
            }

            @Override
            public ProcessResult reduce(MapContext context, List<ProcessResult> taskResults) throws Exception {
                throw new RuntimeException("reduce exception");
            }
        };
        when(applicationContext.getBean("testMapReduceProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("reduce exception"));
        // ROOT + 1 子任务 = 2 次 insert
        verify(jobTaskMapper, times(2)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("Processor Bean 不存在时返回失败结果")
    void executeMapJob_beanNotFound_returnsFailed() {
        JobDO job = buildJob("no-bean", "missingProcessor");
        JobLogDO log0 = buildLog(job);
        when(applicationContext.getBean("missingProcessor", MapProcessor.class))
                .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("missingProcessor"));

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("获取 MapProcessor Bean 失败"));
        // Bean 获取失败，不插入任何 TaskDO
        verify(jobTaskMapper, times(0)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("process 返回 null 时视为成功")
    void executeMapJob_processReturnsNull_treatedAsSuccess() throws Exception {
        JobDO job = buildJob("null-return", "testMapProcessor");
        JobLogDO log0 = buildLog(job);
        MapProcessor processor = (context) -> null;
        when(applicationContext.getBean("testMapProcessor", MapProcessor.class)).thenReturn(processor);

        ProcessResult result = executor.executeMapJob(job, log0, "CRON");

        assertTrue(result.isSuccess());
        // 仅 ROOT TaskDO
        verify(jobTaskMapper, times(1)).insert(any(JobTaskDO.class));
    }

    @Test
    @DisplayName("TaskDO 状态更新正确（PENDING→RUNNING→SUCCESS）")
    void executeMapJob_statusTransitionsCorrect() throws Exception {
        JobDO job = buildJob("status-transition", "testMapProcessor");
        JobLogDO log0 = buildLog(job);
        MapProcessor processor = (context) -> ProcessResult.success("ok");
        when(applicationContext.getBean("testMapProcessor", MapProcessor.class)).thenReturn(processor);

        executor.executeMapJob(job, log0, "CRON");

        // 验证 ROOT TaskDO 状态转换：1 次 RUNNING + 1 次 SUCCESS
        verify(jobTaskMapper, times(1)).updateStatus(anyString(), eq("RUNNING"), isNull(), isNull(), any());
        verify(jobTaskMapper, times(1)).updateStatus(anyString(), eq("SUCCESS"), eq("ok"), isNull(), any());
    }

    // ==================== 辅助方法 ====================

    private JobDO buildJob(String key, String handler) {
        JobDO job = new JobDO();
        job.setId("job-" + key);
        job.setJobKey(key);
        job.setJobName("测试任务 " + key);
        job.setHandler(handler);
        job.setCronExpression("0 0 8 * * ?");
        job.setStatus("NORMAL");
        job.setParamsJson("{}");
        return job;
    }

    private JobLogDO buildLog(JobDO job) {
        JobLogDO log0 = new JobLogDO();
        log0.setId("log-" + job.getJobKey());
        log0.setJobId(job.getId());
        log0.setJobKey(job.getJobKey());
        log0.setStatus("RUNNING");
        return log0;
    }
}
