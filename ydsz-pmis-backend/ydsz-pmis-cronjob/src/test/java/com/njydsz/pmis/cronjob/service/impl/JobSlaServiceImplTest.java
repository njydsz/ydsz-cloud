package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.dto.JobSlaSaveDTO;
import com.njydsz.pmis.cronjob.entity.JobSlaDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobSlaMapper;
import com.njydsz.pmis.cronjob.service.JobSlaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobSlaServiceImpl} 单元测试（P2-7 SLA 管理）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>createSla 正常/约束校验失败（无约束/无效时长/无效比率）</li>
 *   <li>updateSla 正常/不存在</li>
 *   <li>deleteSla 正常/不存在</li>
 *   <li>getSlaById 正常/不存在</li>
 *   <li>listSla</li>
 *   <li>toggleSla 正常/无效 enabled/不存在</li>
 *   <li>checkViolation: 无 SLA/禁用/无执行记录/各项违约</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobSlaServiceImpl SLA 服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobSlaServiceImplTest {

    @Mock
    private JobSlaMapper jobSlaMapper;
    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private CronjobProperties cronjobProperties;

    @InjectMocks
    private JobSlaServiceImpl slaService;

    // ==================== createSla ====================

    @Test
    @DisplayName("createSla: 正常创建（含最大执行时长）")
    void createSla_normalDuration_returnsSlaId() {
        JobSlaSaveDTO dto = buildDto(5000L, null, null);
        when(jobSlaMapper.insert(any(JobSlaDO.class))).thenAnswer(invocation -> {
            JobSlaDO sla = invocation.getArgument(0);
            sla.setId("sla-new-1");
            return 1;
        });

        String slaId = slaService.createSla(dto);

        assertEquals("sla-new-1", slaId);
        ArgumentCaptor<JobSlaDO> captor = ArgumentCaptor.forClass(JobSlaDO.class);
        verify(jobSlaMapper, times(1)).insert(captor.capture());
        JobSlaDO saved = captor.getValue();
        assertEquals("job-1", saved.getJobId());
        assertEquals("key-1", saved.getJobKey());
        assertEquals(5000L, saved.getMaxDurationMs());
        assertEquals("WARNING", saved.getAlertLevel());
        assertEquals(1, saved.getEnabled());
    }

    @Test
    @DisplayName("createSla: 三项约束全为空抛 BizException")
    void createSla_noConstraint_throwsBizException() {
        JobSlaSaveDTO dto = buildDto(null, null, null);

        BizException ex = assertThrows(BizException.class, () -> slaService.createSla(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobSlaMapper, never()).insert(any(JobSlaDO.class));
    }

    @Test
    @DisplayName("createSla: 最大执行时长 <= 0 抛 BizException")
    void createSla_invalidDuration_throwsBizException() {
        JobSlaSaveDTO dto = buildDto(0L, null, null);

        BizException ex = assertThrows(BizException.class, () -> slaService.createSla(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobSlaMapper, never()).insert(any(JobSlaDO.class));
    }

    @Test
    @DisplayName("createSla: 失败率 > 100 抛 BizException")
    void createSla_invalidFailRate_throwsBizException() {
        JobSlaSaveDTO dto = buildDto(null, new BigDecimal("101"), null);

        BizException ex = assertThrows(BizException.class, () -> slaService.createSla(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobSlaMapper, never()).insert(any(JobSlaDO.class));
    }

    @Test
    @DisplayName("createSla: alertLevel 为空时默认 WARNING")
    void createSla_emptyAlertLevel_defaultsToWarning() {
        JobSlaSaveDTO dto = buildDto(5000L, null, null);
        dto.setAlertLevel(null);
        when(jobSlaMapper.insert(any(JobSlaDO.class))).thenAnswer(invocation -> {
            JobSlaDO sla = invocation.getArgument(0);
            sla.setId("sla-1");
            return 1;
        });

        slaService.createSla(dto);

        ArgumentCaptor<JobSlaDO> captor = ArgumentCaptor.forClass(JobSlaDO.class);
        verify(jobSlaMapper).insert(captor.capture());
        assertEquals("WARNING", captor.getValue().getAlertLevel());
    }

    // ==================== updateSla ====================

    @Test
    @DisplayName("updateSla: SLA 不存在抛 BizException")
    void updateSla_notFound_throwsBizException() {
        JobSlaSaveDTO dto = buildDto(5000L, null, null);
        when(jobSlaMapper.selectById("sla-x")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> slaService.updateSla("sla-x", dto));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(jobSlaMapper, never()).updateById(any(JobSlaDO.class));
    }

    @Test
    @DisplayName("updateSla: 正常更新")
    void updateSla_normal_updatesEntity() {
        JobSlaSaveDTO dto = buildDto(8000L, null, null);
        JobSlaDO exists = new JobSlaDO();
        exists.setId("sla-1");
        exists.setJobId("job-1");
        when(jobSlaMapper.selectById("sla-1")).thenReturn(exists);

        slaService.updateSla("sla-1", dto);

        ArgumentCaptor<JobSlaDO> captor = ArgumentCaptor.forClass(JobSlaDO.class);
        verify(jobSlaMapper, times(1)).updateById(captor.capture());
        assertEquals(8000L, captor.getValue().getMaxDurationMs());
    }

    // ==================== deleteSla ====================

    @Test
    @DisplayName("deleteSla: SLA 不存在抛 BizException")
    void deleteSla_notFound_throwsBizException() {
        when(jobSlaMapper.selectById("sla-x")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> slaService.deleteSla("sla-x"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("deleteSla: 正常删除")
    void deleteSla_normal_deletesEntity() {
        JobSlaDO exists = new JobSlaDO();
        exists.setId("sla-1");
        when(jobSlaMapper.selectById("sla-1")).thenReturn(exists);

        slaService.deleteSla("sla-1");

        verify(jobSlaMapper, times(1)).deleteById("sla-1");
    }

    // ==================== getSlaById ====================

    @Test
    @DisplayName("getSlaById: SLA 不存在抛 BizException")
    void getSlaById_notFound_throwsBizException() {
        when(jobSlaMapper.selectById("sla-x")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> slaService.getSlaById("sla-x"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("getSlaById: 正常返回")
    void getSlaById_normal_returnsSla() {
        JobSlaDO sla = new JobSlaDO();
        sla.setId("sla-1");
        sla.setJobId("job-1");
        when(jobSlaMapper.selectById("sla-1")).thenReturn(sla);

        JobSlaDO result = slaService.getSlaById("sla-1");

        assertEquals("sla-1", result.getId());
        assertEquals("job-1", result.getJobId());
    }

    // ==================== listSla ====================

    @Test
    @DisplayName("listSla: 返回全部 SLA 列表")
    void listSla_returnsAll() {
        JobSlaDO sla1 = new JobSlaDO();
        sla1.setId("sla-1");
        JobSlaDO sla2 = new JobSlaDO();
        sla2.setId("sla-2");
        when(jobSlaMapper.selectList(null)).thenReturn(List.of(sla1, sla2));

        List<JobSlaDO> list = slaService.listSla();

        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("listSla: 无 SLA 时返回空列表")
    void listSla_empty_returnsEmptyList() {
        when(jobSlaMapper.selectList(null)).thenReturn(Collections.emptyList());

        List<JobSlaDO> list = slaService.listSla();

        assertTrue(list.isEmpty());
    }

    // ==================== toggleSla ====================

    @Test
    @DisplayName("toggleSla: enabled=null 抛 BizException")
    void toggleSla_nullEnabled_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> slaService.toggleSla("sla-1", null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("toggleSla: enabled=2 无效值抛 BizException")
    void toggleSla_invalidEnabled_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> slaService.toggleSla("sla-1", 2));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("toggleSla: SLA 不存在抛 BizException")
    void toggleSla_notFound_throwsBizException() {
        when(jobSlaMapper.selectById("sla-x")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> slaService.toggleSla("sla-x", 1));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("toggleSla: 正常切换为禁用")
    void toggleSla_disable_normal() {
        JobSlaDO exists = new JobSlaDO();
        exists.setId("sla-1");
        exists.setEnabled(1);
        when(jobSlaMapper.selectById("sla-1")).thenReturn(exists);

        slaService.toggleSla("sla-1", 0);

        ArgumentCaptor<JobSlaDO> captor = ArgumentCaptor.forClass(JobSlaDO.class);
        verify(jobSlaMapper, times(1)).updateById(captor.capture());
        assertEquals(0, captor.getValue().getEnabled());
    }

    // ==================== checkViolation ====================

    @Test
    @DisplayName("checkViolation: jobId 为空返回空列表")
    void checkViolation_blankJobId_returnsEmpty() {
        List<JobSlaService.SlaViolation> result = slaService.checkViolation("");

        assertTrue(result.isEmpty());
        verify(jobSlaMapper, never()).selectByJobId(any());
    }

    @Test
    @DisplayName("checkViolation: 无 SLA 规则返回空列表")
    void checkViolation_noSla_returnsEmpty() {
        when(jobSlaMapper.selectByJobId("job-1")).thenReturn(null);

        List<JobSlaService.SlaViolation> result = slaService.checkViolation("job-1");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("checkViolation: SLA 禁用返回空列表")
    void checkViolation_disabledSla_returnsEmpty() {
        JobSlaDO sla = buildSla("sla-1", "job-1", "key-1");
        sla.setEnabled(0);
        when(jobSlaMapper.selectByJobId("job-1")).thenReturn(sla);

        List<JobSlaService.SlaViolation> result = slaService.checkViolation("job-1");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("checkViolation: 无执行记录返回空列表")
    void checkViolation_noExecutions_returnsEmpty() {
        JobSlaDO sla = buildSla("sla-1", "job-1", "key-1");
        when(jobSlaMapper.selectByJobId("job-1")).thenReturn(sla);
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any())).thenReturn(stats(0L, 0L));

        List<JobSlaService.SlaViolation> result = slaService.checkViolation("job-1");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("checkViolation: 失败率超过阈值返回违约")
    void checkViolation_failRateExceeded_returnsViolation() {
        JobSlaDO sla = buildSla("sla-1", "job-1", "key-1");
        sla.setMaxFailRate(new BigDecimal("30"));
        when(jobSlaMapper.selectByJobId("job-1")).thenReturn(sla);
        // total=10, failed=5 → failRate=50% > 30
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any())).thenReturn(stats(10L, 5L));

        List<JobSlaService.SlaViolation> result = slaService.checkViolation("job-1");

        assertEquals(1, result.size());
        assertEquals("FAIL_RATE", result.get(0).metric());
    }

    @Test
    @DisplayName("checkViolation: 成功率低于阈值返回违约")
    void checkViolation_successRateBelow_returnsViolation() {
        JobSlaDO sla = buildSla("sla-1", "job-1", "key-1");
        sla.setMinSuccessRate(new BigDecimal("90"));
        when(jobSlaMapper.selectByJobId("job-1")).thenReturn(sla);
        // total=10, failed=5 → successRate=50% < 90
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any())).thenReturn(stats(10L, 5L));

        List<JobSlaService.SlaViolation> result = slaService.checkViolation("job-1");

        assertEquals(1, result.size());
        assertEquals("SUCCESS_RATE", result.get(0).metric());
    }

    @Test
    @DisplayName("checkViolation: P95 耗时超过阈值返回违约")
    void checkViolation_durationExceeded_returnsViolation() {
        JobSlaDO sla = buildSla("sla-1", "job-1", "key-1");
        sla.setMaxDurationMs(3000L);
        when(jobSlaMapper.selectByJobId("job-1")).thenReturn(sla);
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any())).thenReturn(stats(10L, 1L));
        // P95=5000ms > 3000ms
        when(jobLogMapper.selectDurationP95(eq("job-1"), any())).thenReturn(5000L);

        List<JobSlaService.SlaViolation> result = slaService.checkViolation("job-1");

        assertEquals(1, result.size());
        assertEquals("MAX_DURATION", result.get(0).metric());
        assertEquals("5000", result.get(0).actual());
    }

    @Test
    @DisplayName("checkViolation: 各项均未违约返回空列表")
    void checkViolation_noViolation_returnsEmpty() {
        JobSlaDO sla = buildSla("sla-1", "job-1", "key-1");
        sla.setMaxDurationMs(10000L);
        sla.setMaxFailRate(new BigDecimal("80"));
        sla.setMinSuccessRate(new BigDecimal("20"));
        when(jobSlaMapper.selectByJobId("job-1")).thenReturn(sla);
        // total=10, failed=1 → failRate=10% < 80, successRate=90% > 20
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any())).thenReturn(stats(10L, 1L));
        when(jobLogMapper.selectDurationP95(eq("job-1"), any())).thenReturn(5000L);

        List<JobSlaService.SlaViolation> result = slaService.checkViolation("job-1");

        assertTrue(result.isEmpty());
    }

    // ==================== 辅助方法 ====================

    private JobSlaSaveDTO buildDto(Long maxDurationMs, BigDecimal maxFailRate, BigDecimal minSuccessRate) {
        JobSlaSaveDTO dto = new JobSlaSaveDTO();
        dto.setJobId("job-1");
        dto.setJobKey("key-1");
        dto.setMaxDurationMs(maxDurationMs);
        dto.setMaxFailRate(maxFailRate);
        dto.setMinSuccessRate(minSuccessRate);
        dto.setAlertLevel("WARNING");
        dto.setEnabled(1);
        return dto;
    }

    private JobSlaDO buildSla(String id, String jobId, String jobKey) {
        JobSlaDO sla = new JobSlaDO();
        sla.setId(id);
        sla.setJobId(jobId);
        sla.setJobKey(jobKey);
        sla.setAlertLevel("WARNING");
        sla.setEnabled(1);
        return sla;
    }

    private Map<String, Object> stats(long total, long failed) {
        Map<String, Object> map = new HashMap<>();
        map.put("total", total);
        map.put("failed", failed);
        return map;
    }
}
