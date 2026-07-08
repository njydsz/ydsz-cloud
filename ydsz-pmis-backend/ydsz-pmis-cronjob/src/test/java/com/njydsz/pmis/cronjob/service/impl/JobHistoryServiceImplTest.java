package com.njydsz.pmis.cronjob.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobHistoryDO;
import com.njydsz.pmis.cronjob.mapper.JobHistoryMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobHistoryServiceImpl} 单元测试（P1-6 任务版本管理）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>saveHistory: 快照序列化、版本号、冗余字段</li>
 *   <li>listVersions: 按版本号降序返回</li>
 *   <li>getVersion: 查询指定版本</li>
 *   <li>rollback: 恢复配置字段、version 递增、保留统计字段、保存新历史</li>
 *   <li>compareVersions: 差异字段检测</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobHistoryServiceImpl 任务历史版本服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobHistoryServiceImplTest {

    @Mock
    private JobHistoryMapper jobHistoryMapper;
    @Mock
    private JobMapper jobMapper;

    @InjectMocks
    private JobHistoryServiceImpl jobHistoryService;

    // ==================== saveHistory ====================

    @Test
    @DisplayName("saveHistory: 快照序列化包含完整 JobDO，版本号取自 job.version")
    void saveHistory_snapshotContainsFullJobDO_versionFromJob() {
        JobDO job = buildJob("job-1", "test-key", "0 0 8 * * ?", 3);
        job.setJobName("测试任务");
        job.setParamsJson("{\"key\":\"value\"}");
        job.setRemark("备注信息");

        jobHistoryService.saveHistory(job, "user-001");

        ArgumentCaptor<JobHistoryDO> captor = ArgumentCaptor.forClass(JobHistoryDO.class);
        verify(jobHistoryMapper).insert(captor.capture());
        JobHistoryDO saved = captor.getValue();
        // 版本号取自 job.version
        assertEquals(3, saved.getVersion());
        assertEquals("job-1", saved.getJobId());
        // 快照包含完整 JobDO JSON
        JobDO snapshotJob = JSON.parseObject(saved.getSnapshot(), JobDO.class);
        assertEquals("job-1", snapshotJob.getId());
        assertEquals("test-key", snapshotJob.getJobKey());
        assertEquals("测试任务", snapshotJob.getJobName());
        assertEquals("0 0 8 * * ?", snapshotJob.getCronExpression());
        assertEquals("{\"key\":\"value\"}", snapshotJob.getParamsJson());
        assertEquals("备注信息", snapshotJob.getRemark());
        // 冗余字段
        assertEquals("测试任务", saved.getJobName());
        assertEquals("test-key", saved.getJobKey());
        assertEquals("0 0 8 * * ?", saved.getCronExpression());
        assertEquals("{\"key\":\"value\"}", saved.getParamsJson());
        assertEquals("备注信息", saved.getRemark());
        // 修改人
        assertEquals("user-001", saved.getChangedBy());
        assertNotNull(saved.getChangedAt());
        assertEquals(0, saved.getDeleted());
    }

    @Test
    @DisplayName("saveHistory: changedBy 为空时默认 SYSTEM")
    void saveHistory_blankChangedBy_defaultsToSystem() {
        JobDO job = buildJob("job-1", "test-key", "0 0 8 * * ?", 1);

        jobHistoryService.saveHistory(job, null);
        jobHistoryService.saveHistory(job, "");

        ArgumentCaptor<JobHistoryDO> captor = ArgumentCaptor.forClass(JobHistoryDO.class);
        verify(jobHistoryMapper, times(2)).insert(captor.capture());
        assertEquals("SYSTEM", captor.getAllValues().get(0).getChangedBy());
        assertEquals("SYSTEM", captor.getAllValues().get(1).getChangedBy());
    }

    @Test
    @DisplayName("saveHistory: job 为 null 抛 BizException")
    void saveHistory_nullJob_throwsException() {
        assertThrows(BizException.class, () -> jobHistoryService.saveHistory(null, "user-001"));
        verify(jobHistoryMapper, never()).insert(any(JobHistoryDO.class));
    }

    // ==================== listVersions ====================

    @Test
    @DisplayName("listVersions: 按版本号降序返回")
    void listVersions_returnsDescOrder() {
        JobHistoryDO v3 = new JobHistoryDO();
        v3.setVersion(3);
        JobHistoryDO v2 = new JobHistoryDO();
        v2.setVersion(2);
        JobHistoryDO v1 = new JobHistoryDO();
        v1.setVersion(1);
        when(jobHistoryMapper.selectByJobIdOrderByVersionDesc("job-1"))
                .thenReturn(List.of(v3, v2, v1));

        List<JobHistoryDO> result = jobHistoryService.listVersions("job-1");

        assertEquals(3, result.size());
        assertEquals(3, result.get(0).getVersion());
        assertEquals(2, result.get(1).getVersion());
        assertEquals(1, result.get(2).getVersion());
    }

    @Test
    @DisplayName("listVersions: 无记录返回空列表")
    void listVersions_noRecords_returnsEmptyList() {
        when(jobHistoryMapper.selectByJobIdOrderByVersionDesc("job-empty"))
                .thenReturn(List.of());

        List<JobHistoryDO> result = jobHistoryService.listVersions("job-empty");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listVersions: jobId 为空返回空列表")
    void listVersions_blankJobId_returnsEmptyList() {
        assertTrue(jobHistoryService.listVersions("").isEmpty());
        assertTrue(jobHistoryService.listVersions(null).isEmpty());
        verify(jobHistoryMapper, never()).selectByJobIdOrderByVersionDesc(any());
    }

    // ==================== getVersion ====================

    @Test
    @DisplayName("getVersion: 查询指定版本")
    void getVersion_returnsCorrectVersion() {
        JobHistoryDO expected = new JobHistoryDO();
        expected.setJobId("job-1");
        expected.setVersion(2);
        when(jobHistoryMapper.selectByVersion("job-1", 2)).thenReturn(expected);

        JobHistoryDO result = jobHistoryService.getVersion("job-1", 2);

        assertEquals(expected, result);
        verify(jobHistoryMapper).selectByVersion("job-1", 2);
    }

    @Test
    @DisplayName("getVersion: 不存在时返回 null")
    void getVersion_notExists_returnsNull() {
        when(jobHistoryMapper.selectByVersion("job-1", 99)).thenReturn(null);

        JobHistoryDO result = jobHistoryService.getVersion("job-1", 99);

        assertEquals(null, result);
    }

    @Test
    @DisplayName("getVersion: 参数为空返回 null")
    void getVersion_blankParams_returnsNull() {
        assertEquals(null, jobHistoryService.getVersion("", 1));
        assertEquals(null, jobHistoryService.getVersion(null, 1));
        assertEquals(null, jobHistoryService.getVersion("job-1", null));
        verify(jobHistoryMapper, never()).selectByVersion(any(), any());
    }

    // ==================== rollback ====================

    @Test
    @DisplayName("rollback: 恢复配置字段、version 递增、保留统计字段、保存新历史")
    void rollback_restoresConfig_incrementsVersion_preservesStats() {
        // 构造目标历史版本快照（v2 配置）
        JobDO snapshotJob = buildJob("job-1", "test-key", "0 0 9 * * ?", 2);
        snapshotJob.setJobName("旧配置名称");
        snapshotJob.setHandler("oldHandler");
        snapshotJob.setRemark("旧备注");
        JobHistoryDO targetHistory = new JobHistoryDO();
        targetHistory.setJobId("job-1");
        targetHistory.setVersion(2);
        targetHistory.setSnapshot(JSON.toJSONString(snapshotJob));
        when(jobHistoryMapper.selectByVersion("job-1", 2)).thenReturn(targetHistory);

        // 当前任务（有统计数据）
        JobDO currentJob = buildJob("job-1", "test-key", "0 0 8 * * ?", 5);
        currentJob.setJobName("当前配置名称");
        currentJob.setHandler("currentHandler");
        currentJob.setFireCount(100L);
        currentJob.setSuccessCount(95L);
        currentJob.setFailCount(5L);
        currentJob.setTenantId("tenant-001");
        when(jobMapper.selectById("job-1")).thenReturn(currentJob);

        // 历史版本列表（最大版本为 5）
        JobHistoryDO maxVersion = new JobHistoryDO();
        maxVersion.setVersion(5);
        when(jobHistoryMapper.selectByJobIdOrderByVersionDesc("job-1"))
                .thenReturn(List.of(maxVersion));

        // 执行回滚
        JobDO result = jobHistoryService.rollback("job-1", 2);

        // 验证配置字段从快照恢复
        assertEquals("旧配置名称", result.getJobName());
        assertEquals("oldHandler", result.getHandler());
        assertEquals("0 0 9 * * ?", result.getCronExpression());
        assertEquals("旧备注", result.getRemark());
        // 验证统计字段保留当前值
        assertEquals(100L, result.getFireCount());
        assertEquals(95L, result.getSuccessCount());
        assertEquals(5L, result.getFailCount());
        assertEquals("tenant-001", result.getTenantId());
        // 验证版本号 = max(5) + 1 = 6
        assertEquals(6, result.getVersion());
        // 验证 jobMapper.updateById 被调用
        verify(jobMapper).updateById(any(JobDO.class));
        // 验证保存了新的历史版本
        ArgumentCaptor<JobHistoryDO> captor = ArgumentCaptor.forClass(JobHistoryDO.class);
        verify(jobHistoryMapper).insert(captor.capture());
        assertEquals(6, captor.getValue().getVersion());
    }

    @Test
    @DisplayName("rollback: 目标版本不存在抛 BizException")
    void rollback_targetNotFound_throwsException() {
        when(jobHistoryMapper.selectByVersion("job-1", 99)).thenReturn(null);

        assertThrows(BizException.class, () -> jobHistoryService.rollback("job-1", 99));
        verify(jobMapper, never()).updateById(any(JobDO.class));
        verify(jobHistoryMapper, never()).insert(any(JobHistoryDO.class));
    }

    @Test
    @DisplayName("rollback: 当前任务不存在抛 BizException")
    void rollback_currentJobNotFound_throwsException() {
        JobDO snapshotJob = buildJob("job-1", "test-key", "0 0 9 * * ?", 2);
        JobHistoryDO targetHistory = new JobHistoryDO();
        targetHistory.setVersion(2);
        targetHistory.setSnapshot(JSON.toJSONString(snapshotJob));
        when(jobHistoryMapper.selectByVersion("job-1", 2)).thenReturn(targetHistory);
        when(jobMapper.selectById("job-1")).thenReturn(null);

        assertThrows(BizException.class, () -> jobHistoryService.rollback("job-1", 2));
        verify(jobMapper, never()).updateById(any(JobDO.class));
    }

    @Test
    @DisplayName("rollback: jobId 为空抛 BizException")
    void rollback_blankJobId_throwsException() {
        assertThrows(BizException.class, () -> jobHistoryService.rollback("", 1));
        assertThrows(BizException.class, () -> jobHistoryService.rollback(null, 1));
        verify(jobHistoryMapper, never()).insert(any(JobHistoryDO.class));
    }

    @Test
    @DisplayName("rollback: version 无效抛 BizException")
    void rollback_invalidVersion_throwsException() {
        assertThrows(BizException.class, () -> jobHistoryService.rollback("job-1", 0));
        assertThrows(BizException.class, () -> jobHistoryService.rollback("job-1", -1));
        assertThrows(BizException.class, () -> jobHistoryService.rollback("job-1", null));
        verify(jobHistoryMapper, never()).insert(any(JobHistoryDO.class));
    }

    // ==================== compareVersions ====================

    @Test
    @DisplayName("compareVersions: 检测差异字段并返回 field/oldValue/newValue")
    void compareVersions_detectsDiffFields() {
        // v1 快照
        JobDO job1 = buildJob("job-1", "test-key", "0 0 8 * * ?", 1);
        job1.setJobName("旧名称");
        job1.setHandler("oldHandler");
        job1.setRemark("旧备注");
        JobHistoryDO h1 = new JobHistoryDO();
        h1.setVersion(1);
        h1.setSnapshot(JSON.toJSONString(job1));

        // v2 快照（修改了 jobName 和 handler，remark 不变）
        JobDO job2 = buildJob("job-1", "test-key", "0 0 8 * * ?", 2);
        job2.setJobName("新名称");
        job2.setHandler("newHandler");
        job2.setRemark("旧备注");
        JobHistoryDO h2 = new JobHistoryDO();
        h2.setVersion(2);
        h2.setSnapshot(JSON.toJSONString(job2));

        when(jobHistoryMapper.selectByVersion("job-1", 1)).thenReturn(h1);
        when(jobHistoryMapper.selectByVersion("job-1", 2)).thenReturn(h2);

        List<Map<String, Object>> diffs = jobHistoryService.compareVersions("job-1", 1, 2);

        // 应检测到 jobName 和 handler 两个差异字段
        assertEquals(2, diffs.size());
        // 验证 jobName 差异
        Map<String, Object> jobNameDiff = diffs.stream()
                .filter(d -> "jobName".equals(d.get("field")))
                .findFirst().orElse(null);
        assertNotNull(jobNameDiff);
        assertEquals("旧名称", jobNameDiff.get("oldValue"));
        assertEquals("新名称", jobNameDiff.get("newValue"));
        // 验证 handler 差异
        Map<String, Object> handlerDiff = diffs.stream()
                .filter(d -> "handler".equals(d.get("field")))
                .findFirst().orElse(null);
        assertNotNull(handlerDiff);
        assertEquals("oldHandler", handlerDiff.get("oldValue"));
        assertEquals("newHandler", handlerDiff.get("newValue"));
    }

    @Test
    @DisplayName("compareVersions: 无差异时返回空列表")
    void compareVersions_noDiff_returnsEmptyList() {
        JobDO job = buildJob("job-1", "test-key", "0 0 8 * * ?", 1);
        job.setJobName("相同名称");
        job.setHandler("sameHandler");
        JobHistoryDO h1 = new JobHistoryDO();
        h1.setVersion(1);
        h1.setSnapshot(JSON.toJSONString(job));
        JobHistoryDO h2 = new JobHistoryDO();
        h2.setVersion(2);
        h2.setSnapshot(JSON.toJSONString(job));

        when(jobHistoryMapper.selectByVersion("job-1", 1)).thenReturn(h1);
        when(jobHistoryMapper.selectByVersion("job-1", 2)).thenReturn(h2);

        List<Map<String, Object>> diffs = jobHistoryService.compareVersions("job-1", 1, 2);

        assertTrue(diffs.isEmpty());
    }

    @Test
    @DisplayName("compareVersions: 版本不存在返回空列表")
    void compareVersions_versionNotFound_returnsEmptyList() {
        when(jobHistoryMapper.selectByVersion("job-1", 1)).thenReturn(null);
        when(jobHistoryMapper.selectByVersion("job-1", 2)).thenReturn(null);

        List<Map<String, Object>> diffs = jobHistoryService.compareVersions("job-1", 1, 2);

        assertTrue(diffs.isEmpty());
    }

    @Test
    @DisplayName("compareVersions: 参数为空返回空列表")
    void compareVersions_blankParams_returnsEmptyList() {
        assertTrue(jobHistoryService.compareVersions("", 1, 2).isEmpty());
        assertTrue(jobHistoryService.compareVersions(null, 1, 2).isEmpty());
        assertTrue(jobHistoryService.compareVersions("job-1", null, 2).isEmpty());
        assertTrue(jobHistoryService.compareVersions("job-1", 1, null).isEmpty());
        verify(jobHistoryMapper, never()).selectByVersion(any(), any());
    }

    /**
     * 构造测试用 JobDO。
     */
    private JobDO buildJob(String id, String key, String cron, Integer version) {
        JobDO job = new JobDO();
        job.setId(id);
        job.setJobKey(key);
        job.setJobName("测试任务 " + key);
        job.setJobGroup("DEFAULT");
        job.setHandler("testHandler");
        job.setCronExpression(cron);
        job.setStatus("NORMAL");
        job.setScheduleType("CRON");
        job.setVersion(version);
        return job;
    }
}
