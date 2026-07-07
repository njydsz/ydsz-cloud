package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.core.dag.DagParser;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobRelationDO;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.JobRelationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobRelationServiceImpl} 单元测试（P4-5 DAG 工作流）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobRelationServiceImpl 依赖关系服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobRelationServiceImplTest {

    @Mock
    private JobRelationMapper jobRelationMapper;
    @Mock
    private JobMapper jobMapper;
    @Spy
    private final DagParser dagParser = new DagParser();

    @InjectMocks
    private JobRelationServiceImpl jobRelationService;

    @Test
    @DisplayName("addRelation: 正常添加依赖关系")
    void addRelation_normal_success() {
        when(jobMapper.selectById("job-A")).thenReturn(buildJob("job-A"));
        when(jobMapper.selectById("job-B")).thenReturn(buildJob("job-B"));
        when(jobRelationMapper.selectAllRelations()).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> jobRelationService.addRelation("job-A", "job-B", "FAIL_FAST"));
        verify(jobRelationMapper, org.mockito.Mockito.times(1)).insert(any(JobRelationDO.class));
    }

    @Test
    @DisplayName("addRelation: 自依赖抛异常")
    void addRelation_selfRef_throwsException() {
        when(jobMapper.selectById("job-A")).thenReturn(buildJob("job-A"));

        assertThrows(BizException.class, () -> jobRelationService.addRelation("job-A", "job-A", null));
        verify(jobRelationMapper, never()).insert(any(JobRelationDO.class));
    }

    @Test
    @DisplayName("addRelation: 前置任务不存在抛异常")
    void addRelation_parentNotFound_throwsException() {
        when(jobMapper.selectById("nonexistent")).thenReturn(null);

        assertThrows(BizException.class, () -> jobRelationService.addRelation("nonexistent", "job-B", null));
        verify(jobRelationMapper, never()).insert(any(JobRelationDO.class));
    }

    @Test
    @DisplayName("addRelation: 形成环抛异常")
    void addRelation_createsCycle_throwsException() {
        // 现有: B→C, 添加 C→B 会形成环
        List<JobRelationDO> existing = List.of(buildRelation("job-B", "job-C"));
        when(jobMapper.selectById("job-C")).thenReturn(buildJob("job-C"));
        when(jobMapper.selectById("job-B")).thenReturn(buildJob("job-B"));
        when(jobRelationMapper.selectAllRelations()).thenReturn(existing);

        assertThrows(BizException.class, () -> jobRelationService.addRelation("job-C", "job-B", null));
        verify(jobRelationMapper, never()).insert(any(JobRelationDO.class));
    }

    @Test
    @DisplayName("addRelation: failStrategy 为 null 时默认 FAIL_FAST")
    void addRelation_nullStrategy_defaultsToFailFast() {
        when(jobMapper.selectById("job-A")).thenReturn(buildJob("job-A"));
        when(jobMapper.selectById("job-B")).thenReturn(buildJob("job-B"));
        when(jobRelationMapper.selectAllRelations()).thenReturn(Collections.emptyList());

        jobRelationService.addRelation("job-A", "job-B", null);

        verify(jobRelationMapper).insert(ArgumentMatchers.<JobRelationDO>argThat(rel ->
                "FAIL_FAST".equals(rel.getFailStrategy())));
    }

    @Test
    @DisplayName("removeRelation: 正常删除")
    void removeRelation_normal_success() {
        JobRelationDO relation = buildRelation("job-A", "job-B");
        when(jobRelationMapper.selectById("rel-1")).thenReturn(relation);

        jobRelationService.removeRelation("rel-1");

        verify(jobRelationMapper, org.mockito.Mockito.times(1)).deleteById("rel-1");
    }

    @Test
    @DisplayName("removeRelation: 不存在抛异常")
    void removeRelation_notFound_throwsException() {
        when(jobRelationMapper.selectById("nonexistent")).thenReturn(null);

        assertThrows(BizException.class, () -> jobRelationService.removeRelation("nonexistent"));
        verify(jobRelationMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("getChildren: 返回后继列表")
    void getChildren_returnsList() {
        List<JobRelationDO> relations = Arrays.asList(
                buildRelation("job-A", "job-B"),
                buildRelation("job-A", "job-C"));
        when(jobRelationMapper.selectByParentJobId("job-A")).thenReturn(relations);

        List<JobRelationDO> result = jobRelationService.getChildren("job-A");

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getParents: 返回前置列表")
    void getParents_returnsList() {
        List<JobRelationDO> relations = List.of(buildRelation("job-A", "job-C"));
        when(jobRelationMapper.selectByChildJobId("job-C")).thenReturn(relations);

        List<JobRelationDO> result = jobRelationService.getParents("job-C");

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getAllRelations: 返回全部依赖边")
    void getAllRelations_returnsAll() {
        List<JobRelationDO> relations = Arrays.asList(
                buildRelation("job-A", "job-B"),
                buildRelation("job-B", "job-C"));
        when(jobRelationMapper.selectAllRelations()).thenReturn(relations);

        List<JobRelationDO> result = jobRelationService.getAllRelations();

        assertEquals(2, result.size());
    }

    // ==================== 辅助方法 ====================

    private JobDO buildJob(String id) {
        JobDO job = new JobDO();
        job.setId(id);
        job.setJobKey("key-" + id);
        job.setHandler("testHandler");
        job.setCronExpression("0 0 8 * * ?");
        job.setStatus("NORMAL");
        return job;
    }

    private JobRelationDO buildRelation(String parent, String child) {
        JobRelationDO r = new JobRelationDO();
        r.setId("rel-" + parent + "-" + child);
        r.setParentJobId(parent);
        r.setChildJobId(child);
        r.setFailStrategy("FAIL_FAST");
        return r;
    }
}
