package com.njydsz.pmis.cronjob.core.dag;

import com.njydsz.pmis.cronjob.core.dispatch.TaskDispatcher;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobRelationDO;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.JobRelationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DagExecutor} 单元测试（P4-3 DAG 工作流）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DagExecutor 依赖触发执行器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DagExecutorTest {

    @Mock
    private JobRelationMapper jobRelationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private TaskDispatcher taskDispatcher;

    @InjectMocks
    private DagExecutor dagExecutor;

    @Test
    @DisplayName("前置成功时触发所有后继任务")
    void triggerDependents_parentSuccess_triggersAllChildren() {
        List<JobRelationDO> relations = Arrays.asList(
                buildRelation("parent", "child1", "FAIL_FAST"),
                buildRelation("parent", "child2", "CONTINUE_ON_FAIL"));
        when(jobRelationMapper.selectByParentJobId("parent")).thenReturn(relations);
        when(jobMapper.selectById("child1")).thenReturn(buildJob("child1", "NORMAL"));
        when(jobMapper.selectById("child2")).thenReturn(buildJob("child2", "NORMAL"));
        when(taskDispatcher.dispatch(any(), isNull(), eq("DEPENDENT"))).thenReturn("log-1", "log-2");

        dagExecutor.triggerDependents("parent", true);

        // child1 + child2 均为 NORMAL 且前置成功，应触发 2 次
        verify(taskDispatcher, times(2)).dispatch(any(), isNull(), eq("DEPENDENT"));
    }

    @Test
    @DisplayName("前置失败 + FAIL_FAST 不触发后继")
    void triggerDependents_parentFailFailFast_doesNotTrigger() {
        List<JobRelationDO> relations = List.of(
                buildRelation("parent", "child1", "FAIL_FAST"));
        when(jobRelationMapper.selectByParentJobId("parent")).thenReturn(relations);

        dagExecutor.triggerDependents("parent", false);

        verify(taskDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("前置失败 + CONTINUE_ON_FAIL 仍触发后继")
    void triggerDependents_parentFailContinueOnFail_triggersChild() {
        List<JobRelationDO> relations = List.of(
                buildRelation("parent", "child1", "CONTINUE_ON_FAIL"));
        when(jobRelationMapper.selectByParentJobId("parent")).thenReturn(relations);
        when(jobMapper.selectById("child1")).thenReturn(buildJob("child1", "NORMAL"));

        dagExecutor.triggerDependents("parent", false);

        verify(taskDispatcher, times(1)).dispatch(any(), isNull(), eq("DEPENDENT"));
    }

    @Test
    @DisplayName("后继任务不存在时跳过")
    void triggerDependents_childNotFound_skips() {
        List<JobRelationDO> relations = List.of(
                buildRelation("parent", "child1", "FAIL_FAST"));
        when(jobRelationMapper.selectByParentJobId("parent")).thenReturn(relations);
        when(jobMapper.selectById("child1")).thenReturn(null);

        dagExecutor.triggerDependents("parent", true);

        verify(taskDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("后继任务非 NORMAL 状态时跳过")
    void triggerDependents_childNotNormal_skips() {
        List<JobRelationDO> relations = List.of(
                buildRelation("parent", "child1", "FAIL_FAST"));
        when(jobRelationMapper.selectByParentJobId("parent")).thenReturn(relations);
        when(jobMapper.selectById("child1")).thenReturn(buildJob("child1", "PAUSED"));

        dagExecutor.triggerDependents("parent", true);

        verify(taskDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("无后继依赖时不触发任何任务")
    void triggerDependents_noRelations_doesNothing() {
        when(jobRelationMapper.selectByParentJobId("parent")).thenReturn(Collections.emptyList());

        dagExecutor.triggerDependents("parent", true);

        verify(taskDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("混合策略: FAIL_FAST 跳过 + CONTINUE_ON_FAIL 触发")
    void triggerDependents_mixedStrategies_failCase() {
        List<JobRelationDO> relations = Arrays.asList(
                buildRelation("parent", "child1", "FAIL_FAST"),
                buildRelation("parent", "child2", "CONTINUE_ON_FAIL"));
        when(jobRelationMapper.selectByParentJobId("parent")).thenReturn(relations);
        when(jobMapper.selectById("child2")).thenReturn(buildJob("child2", "NORMAL"));

        dagExecutor.triggerDependents("parent", false);

        // child1 (FAIL_FAST) 跳过, child2 (CONTINUE_ON_FAIL) 触发
        verify(taskDispatcher, times(1)).dispatch(any(), isNull(), eq("DEPENDENT"));
    }

    @Test
    @DisplayName("单个后继派发异常不影响其他后继")
    void triggerDependents_oneChildThrows_othersStillTriggered() {
        List<JobRelationDO> relations = Arrays.asList(
                buildRelation("parent", "child1", "FAIL_FAST"),
                buildRelation("parent", "child2", "FAIL_FAST"));
        when(jobRelationMapper.selectByParentJobId("parent")).thenReturn(relations);
        when(jobMapper.selectById("child1")).thenThrow(new RuntimeException("DB error"));
        when(jobMapper.selectById("child2")).thenReturn(buildJob("child2", "NORMAL"));

        dagExecutor.triggerDependents("parent", true);

        // child1 抛异常, child2 仍被触发
        verify(taskDispatcher, times(1)).dispatch(any(), isNull(), eq("DEPENDENT"));
    }

    // ==================== 辅助方法 ====================

    private JobRelationDO buildRelation(String parent, String child, String strategy) {
        JobRelationDO r = new JobRelationDO();
        r.setParentJobId(parent);
        r.setChildJobId(child);
        r.setFailStrategy(strategy);
        return r;
    }

    private JobDO buildJob(String id, String status) {
        JobDO job = new JobDO();
        job.setId(id);
        job.setJobKey("key-" + id);
        job.setHandler("testHandler");
        job.setCronExpression("0 0 8 * * ?");
        job.setStatus(status);
        return job;
    }
}
