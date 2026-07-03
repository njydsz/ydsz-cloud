package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.workflow.config.FlowHistoryProperties;
import com.njydsz.pmis.workflow.entity.FlowHisInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.mapper.FlowHisInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisVariableMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowHistoryArchiveServiceImpl 单元测试
 *
 * <p>P2-8：覆盖归档 Service 的核心场景，验证配置外化、归档流程、清理流程的正确性。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>archive：无候选实例时返回 archived=0</li>
 *   <li>archive：有候选实例且任务已归档时执行 insert + 主表删除</li>
 *   <li>archive：实例存在未完成任务时返回 missing=1，不执行 insert</li>
 *   <li>archive：参数覆盖配置默认值（retentionDays=90）</li>
 *   <li>archive：异常时返回 ok=false</li>
 *   <li>purge：purgeEnabled=false 时跳过清理</li>
 *   <li>purge：purgeEnabled=true 时执行 delete</li>
 *   <li>purge：无候选实例时返回 purgedInstances=0</li>
 *   <li>getArchiveConfig：返回完整配置 Map</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class FlowHistoryArchiveServiceImplTest {

    @Mock
    private FlowInstanceMapper instanceMapper;

    @Mock
    private FlowHisTaskMapper hisTaskMapper;

    @Mock
    private FlowTaskMapper taskMapper;

    @Mock
    private FlowHisInstanceMapper hisInstanceMapper;

    @Mock
    private FlowHisVariableMapper hisVariableMapper;

    private FlowHistoryProperties properties;

    @InjectMocks
    private FlowHistoryArchiveServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new FlowHistoryProperties();
        // 使用反射注入 properties，因为 @RequiredArgsConstructor 要求构造器注入
        service = new FlowHistoryArchiveServiceImpl(
                instanceMapper, hisTaskMapper, taskMapper,
                hisInstanceMapper, hisVariableMapper, properties);
    }

    // ============ archive 场景 ============

    @Test
    void archiveShouldReturnZeroWhenNoCandidates() {
        when(instanceMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.archive(null, null, null);

        assertEquals(true, result.get("ok"));
        assertEquals(0, result.get("archived"));
        assertEquals(30, result.get("days")); // 默认值
        assertNotNull(result.get("costMs"));
        verify(hisInstanceMapper, never()).insert(any(FlowHisInstanceDO.class));
        verify(hisInstanceMapper, never()).deleteByOriginalIds(anyList());
    }

    @Test
    void archiveShouldInsertAndDeleteMainWhenTasksArchived() {
        // 准备候选实例
        FlowInstanceDO instance = buildCompletedInstance(1L, 60);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        // 任务已归档到 his_task
        FlowTaskDO task = new FlowTaskDO();
        task.setId(100L);
        task.setTaskStatus("COMPLETED");
        when(taskMapper.selectByInstanceId(1L)).thenReturn(List.of(task));

        FlowHisTaskDO hisTask = new FlowHisTaskDO();
        hisTask.setTaskId(100L);
        when(hisTaskMapper.selectByInstanceId(1L)).thenReturn(List.of(hisTask));

        // 归档表 insert 成功
        when(hisInstanceMapper.insert(any(FlowHisInstanceDO.class))).thenReturn(1);

        Map<String, Object> result = service.archive(null, null, null);

        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("total"));
        assertEquals(1, result.get("archived"));
        assertEquals(0, result.get("missing"));
        assertEquals(0, result.get("errors"));
        verify(hisInstanceMapper, times(1)).insert(any(FlowHisInstanceDO.class));
        verify(hisInstanceMapper, times(1)).deleteByOriginalIds(List.of(1L));
    }

    @Test
    void archiveShouldReturnMissingWhenTaskNotArchived() {
        FlowInstanceDO instance = buildCompletedInstance(2L, 60);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        // 存在未归档且非终态的任务
        FlowTaskDO task = new FlowTaskDO();
        task.setId(200L);
        task.setTaskStatus("PENDING"); // 非终态
        when(taskMapper.selectByInstanceId(2L)).thenReturn(List.of(task));
        when(hisTaskMapper.selectByInstanceId(2L)).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.archive(null, null, null);

        assertEquals(true, result.get("ok"));
        assertEquals(0, result.get("archived"));
        assertEquals(1, result.get("missing"));
        verify(hisInstanceMapper, never()).insert(any(FlowHisInstanceDO.class));
        verify(hisInstanceMapper, never()).deleteByOriginalIds(anyList());
    }

    @Test
    void archiveShouldOverrideDefaultsWithExplicitParams() {
        when(instanceMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.archive(90, 50, 5000L);

        assertEquals(true, result.get("ok"));
        assertEquals(90, result.get("days"));
        assertNotNull(result.get("costMs"));
    }

    @Test
    void archiveShouldReturnErrorWhenSelectListThrows() {
        when(instanceMapper.selectList(any())).thenThrow(new RuntimeException("DB error"));

        Map<String, Object> result = service.archive(null, null, null);

        assertEquals(false, result.get("ok"));
        assertEquals("DB error", result.get("error"));
    }

    @Test
    void archiveShouldHandleTerminalTaskWithoutHisTask() {
        // 任务处于终态（即使未归档到 his_task 也允许归档实例）
        FlowInstanceDO instance = buildCompletedInstance(3L, 60);
        when(instanceMapper.selectList(any())).thenReturn(List.of(instance));

        FlowTaskDO task = new FlowTaskDO();
        task.setId(300L);
        task.setTaskStatus("COMPLETED"); // 终态
        when(taskMapper.selectByInstanceId(3L)).thenReturn(List.of(task));
        when(hisTaskMapper.selectByInstanceId(3L)).thenReturn(Collections.emptyList());
        when(hisInstanceMapper.insert(any(FlowHisInstanceDO.class))).thenReturn(1);

        Map<String, Object> result = service.archive(null, null, null);

        assertEquals(1, result.get("archived"));
        verify(hisInstanceMapper, times(1)).insert(any(FlowHisInstanceDO.class));
    }

    // ============ purge 场景 ============

    @Test
    void purgeShouldSkipWhenPurgeDisabled() {
        properties.setPurgeEnabled(false);

        Map<String, Object> result = service.purge(null);

        assertEquals(true, result.get("skipped"));
        assertEquals("purgeEnabled=false", result.get("reason"));
        verify(hisInstanceMapper, never()).selectByArchivedAtBefore(any(), anyInt());
        verify(hisVariableMapper, never()).delete(any());
        verify(hisInstanceMapper, never()).delete(any());
    }

    @Test
    void purgeShouldDeleteWhenEnabledAndCandidatesExist() {
        properties.setPurgeEnabled(true);
        properties.setPurgeDays(365);

        FlowHisInstanceDO hisInstance = new FlowHisInstanceDO();
        hisInstance.setId(10L);
        when(hisInstanceMapper.selectByArchivedAtBefore(any(), anyInt()))
                .thenReturn(List.of(hisInstance));
        when(hisVariableMapper.delete(any())).thenReturn(5);
        when(hisInstanceMapper.delete(any())).thenReturn(1);

        Map<String, Object> result = service.purge(null);

        assertEquals(true, result.get("ok"));
        assertEquals(1, result.get("purgedInstances"));
        assertEquals(5, result.get("purgedVariables"));
        assertEquals(365, result.get("purgeDays"));
        verify(hisVariableMapper, times(1)).delete(any());
        verify(hisInstanceMapper, times(1)).delete(any());
    }

    @Test
    void purgeShouldReturnZeroWhenNoCandidates() {
        properties.setPurgeEnabled(true);

        when(hisInstanceMapper.selectByArchivedAtBefore(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = service.purge(null);

        assertEquals(true, result.get("ok"));
        assertEquals(0, result.get("purgedInstances"));
        assertEquals(0, result.get("purgedVariables"));
        verify(hisVariableMapper, never()).delete(any());
        verify(hisInstanceMapper, never()).delete(any());
    }

    @Test
    void purgeShouldUseExplicitPurgeDaysParam() {
        properties.setPurgeEnabled(true);
        when(hisInstanceMapper.selectByArchivedAtBefore(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = service.purge(730);

        assertEquals(730, result.get("purgeDays"));
    }

    // ============ getArchiveConfig 场景 ============

    @Test
    void getArchiveConfigShouldReturnAllProperties() {
        properties.setArchiveEnabled(true);
        properties.setRetentionDays(45);
        properties.setBatchSize(200);
        properties.setMaxProcessMs(60000L);
        properties.setCronExpression("0 30 2 * * ?");
        properties.setPurgeEnabled(true);
        properties.setPurgeDays(1000);

        Map<String, Object> config = service.getArchiveConfig();

        assertEquals(true, config.get("archiveEnabled"));
        assertEquals(45, config.get("retentionDays"));
        assertEquals(200, config.get("batchSize"));
        assertEquals(60000L, config.get("maxProcessMs"));
        assertEquals("0 30 2 * * ?", config.get("cronExpression"));
        assertEquals(true, config.get("purgeEnabled"));
        assertEquals(1000, config.get("purgeDays"));
    }

    @Test
    void getArchiveConfigShouldReturnDefaultsWhenNotModified() {
        Map<String, Object> config = service.getArchiveConfig();

        assertEquals(true, config.get("archiveEnabled"));
        assertEquals(30, config.get("retentionDays"));
        assertEquals(100, config.get("batchSize"));
        assertEquals(30_000L, config.get("maxProcessMs"));
        assertEquals("0 0 3 * * ?", config.get("cronExpression"));
        assertEquals(false, config.get("purgeEnabled"));
        assertEquals(1825, config.get("purgeDays"));
    }

    // ============ 辅助方法 ============

    /**
     * 构建已结束实例（结束时间在 daysAgo 天前）
     */
    private FlowInstanceDO buildCompletedInstance(Long id, int daysAgo) {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(id);
        instance.setFlowCode("LEAVE");
        instance.setFlowName("请假流程");
        instance.setFlowStatus(FlowInstanceStatus.COMPLETED.name());
        instance.setEndAt(LocalDateTime.now().minusDays(daysAgo));
        instance.setVariable("{\"reason\":\"annual leave\",\"days\":3}");
        return instance;
    }
}
