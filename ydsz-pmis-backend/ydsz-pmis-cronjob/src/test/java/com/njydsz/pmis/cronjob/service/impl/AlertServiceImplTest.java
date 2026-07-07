package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.dto.AlertRuleSaveDTO;
import com.njydsz.pmis.cronjob.entity.JobAlertLogDO;
import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
import com.njydsz.pmis.cronjob.mapper.JobAlertLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobAlertRuleMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

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
 * {@link AlertServiceImpl} 单元测试（P5 告警 + 监控）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>createRule 正常/约束校验失败（threshold 缺失 / timeWindow 缺失 / 非法 alertType）</li>
 *   <li>updateRule 正常/不存在</li>
 *   <li>deleteRule 正常/不存在</li>
 *   <li>getRuleById 正常/不存在</li>
 *   <li>listRules</li>
 *   <li>toggleRule 正常/无效 enabled/不存在</li>
 *   <li>queryAlertLogs 正常/jobId 为空</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AlertServiceImpl 告警规则服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertServiceImplTest {

    @Mock
    private JobAlertRuleMapper jobAlertRuleMapper;
    @Mock
    private JobAlertLogMapper jobAlertLogMapper;

    @InjectMocks
    private AlertServiceImpl alertService;

    // ==================== createRule ====================

    @Test
    @DisplayName("createRule: 正常创建 FAIL 类型规则")
    void createRule_normalFail_returnsRuleId() {
        AlertRuleSaveDTO dto = buildDto("FAIL", null, null);
        when(jobAlertRuleMapper.insert(any(JobAlertRuleDO.class))).thenAnswer(invocation -> {
            JobAlertRuleDO rule = invocation.getArgument(0);
            rule.setId("rule-new-1");
            return 1;
        });

        String ruleId = alertService.createRule(dto);

        assertEquals("rule-new-1", ruleId);
        ArgumentCaptor<JobAlertRuleDO> captor = ArgumentCaptor.forClass(JobAlertRuleDO.class);
        verify(jobAlertRuleMapper, times(1)).insert(captor.capture());
        JobAlertRuleDO saved = captor.getValue();
        assertEquals("测试规则", saved.getRuleName());
        assertEquals("FAIL", saved.getAlertType());
        assertEquals("WARN", saved.getAlertLevel());
        assertEquals(10, saved.getCooldownMinutes());
        assertEquals(1, saved.getEnabled());
    }

    @Test
    @DisplayName("createRule: SLOW 类型缺失 threshold 抛 BizException")
    void createRule_slowWithoutThreshold_throwsBizException() {
        AlertRuleSaveDTO dto = buildDto("SLOW", null, null);

        BizException ex = assertThrows(BizException.class, () -> alertService.createRule(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).insert(any(JobAlertRuleDO.class));
    }

    @Test
    @DisplayName("createRule: FAIL_RATE 类型缺失 threshold 抛 BizException")
    void createRule_failRateWithoutThreshold_throwsBizException() {
        AlertRuleSaveDTO dto = buildDto("FAIL_RATE", null, 5);

        BizException ex = assertThrows(BizException.class, () -> alertService.createRule(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).insert(any(JobAlertRuleDO.class));
    }

    @Test
    @DisplayName("createRule: FAIL_RATE 类型缺失 timeWindowMinutes 抛 BizException")
    void createRule_failRateWithoutTimeWindow_throwsBizException() {
        AlertRuleSaveDTO dto = buildDto("FAIL_RATE", 80L, null);

        BizException ex = assertThrows(BizException.class, () -> alertService.createRule(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).insert(any(JobAlertRuleDO.class));
    }

    @Test
    @DisplayName("createRule: DURATION_P95 类型缺失 threshold 抛 BizException")
    void createRule_durationP95WithoutThreshold_throwsBizException() {
        AlertRuleSaveDTO dto = buildDto("DURATION_P95", null, 5);

        BizException ex = assertThrows(BizException.class, () -> alertService.createRule(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).insert(any(JobAlertRuleDO.class));
    }

    @Test
    @DisplayName("createRule: 非法 alertType 抛 BizException")
    void createRule_invalidAlertType_throwsBizException() {
        AlertRuleSaveDTO dto = buildDto("INVALID_TYPE", null, null);

        BizException ex = assertThrows(BizException.class, () -> alertService.createRule(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).insert(any(JobAlertRuleDO.class));
    }

    @Test
    @DisplayName("createRule: alertLevel 为空时默认 WARN")
    void createRule_emptyAlertLevel_defaultsToWarn() {
        AlertRuleSaveDTO dto = buildDto("FAIL", null, null);
        dto.setAlertLevel(null);
        when(jobAlertRuleMapper.insert(any(JobAlertRuleDO.class))).thenAnswer(invocation -> {
            JobAlertRuleDO rule = invocation.getArgument(0);
            rule.setId("rule-1");
            return 1;
        });

        alertService.createRule(dto);

        ArgumentCaptor<JobAlertRuleDO> captor = ArgumentCaptor.forClass(JobAlertRuleDO.class);
        verify(jobAlertRuleMapper).insert(captor.capture());
        assertEquals("WARN", captor.getValue().getAlertLevel());
    }

    @Test
    @DisplayName("createRule: cooldownMinutes 为空时默认 10")
    void createRule_emptyCooldownMinutes_defaultsTo10() {
        AlertRuleSaveDTO dto = buildDto("FAIL", null, null);
        dto.setCooldownMinutes(null);
        when(jobAlertRuleMapper.insert(any(JobAlertRuleDO.class))).thenAnswer(invocation -> {
            JobAlertRuleDO rule = invocation.getArgument(0);
            rule.setId("rule-1");
            return 1;
        });

        alertService.createRule(dto);

        ArgumentCaptor<JobAlertRuleDO> captor = ArgumentCaptor.forClass(JobAlertRuleDO.class);
        verify(jobAlertRuleMapper).insert(captor.capture());
        assertEquals(10, captor.getValue().getCooldownMinutes());
    }

    // ==================== updateRule ====================

    @Test
    @DisplayName("updateRule: 规则不存在抛 BizException")
    void updateRule_notFound_throwsBizException() {
        AlertRuleSaveDTO dto = buildDto("FAIL", null, null);
        when(jobAlertRuleMapper.selectById("rule-x")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> alertService.updateRule("rule-x", dto));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).updateById(any(JobAlertRuleDO.class));
    }

    @Test
    @DisplayName("updateRule: 正常更新规则")
    void updateRule_normal_updatesEntity() {
        AlertRuleSaveDTO dto = buildDto("SLOW", 5000L, null);
        dto.setRuleName("更新后的规则");
        JobAlertRuleDO exists = new JobAlertRuleDO();
        exists.setId("rule-1");
        exists.setAlertType("FAIL");
        when(jobAlertRuleMapper.selectById("rule-1")).thenReturn(exists);

        alertService.updateRule("rule-1", dto);

        ArgumentCaptor<JobAlertRuleDO> captor = ArgumentCaptor.forClass(JobAlertRuleDO.class);
        verify(jobAlertRuleMapper, times(1)).updateById(captor.capture());
        JobAlertRuleDO updated = captor.getValue();
        assertEquals("更新后的规则", updated.getRuleName());
        assertEquals("SLOW", updated.getAlertType());
        assertEquals(5000L, updated.getThreshold());
    }

    // ==================== deleteRule ====================

    @Test
    @DisplayName("deleteRule: 规则不存在抛 BizException")
    void deleteRule_notFound_throwsBizException() {
        when(jobAlertRuleMapper.selectById("rule-x")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> alertService.deleteRule("rule-x"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    @DisplayName("deleteRule: 正常删除规则")
    void deleteRule_normal_deletesEntity() {
        JobAlertRuleDO exists = new JobAlertRuleDO();
        exists.setId("rule-1");
        when(jobAlertRuleMapper.selectById("rule-1")).thenReturn(exists);

        alertService.deleteRule("rule-1");

        verify(jobAlertRuleMapper, times(1)).deleteById("rule-1");
    }

    // ==================== getRuleById ====================

    @Test
    @DisplayName("getRuleById: 规则不存在抛 BizException")
    void getRuleById_notFound_throwsBizException() {
        when(jobAlertRuleMapper.selectById("rule-x")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> alertService.getRuleById("rule-x"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("getRuleById: 正常返回规则")
    void getRuleById_normal_returnsRule() {
        JobAlertRuleDO rule = new JobAlertRuleDO();
        rule.setId("rule-1");
        rule.setRuleName("test-rule");
        when(jobAlertRuleMapper.selectById("rule-1")).thenReturn(rule);

        JobAlertRuleDO result = alertService.getRuleById("rule-1");

        assertEquals("rule-1", result.getId());
        assertEquals("test-rule", result.getRuleName());
    }

    // ==================== listRules ====================

    @Test
    @DisplayName("listRules: 返回全部规则列表")
    void listRules_returnsAllRules() {
        JobAlertRuleDO rule1 = new JobAlertRuleDO();
        rule1.setId("rule-1");
        JobAlertRuleDO rule2 = new JobAlertRuleDO();
        rule2.setId("rule-2");
        when(jobAlertRuleMapper.selectList(null)).thenReturn(List.of(rule1, rule2));

        List<JobAlertRuleDO> rules = alertService.listRules();

        assertEquals(2, rules.size());
        assertEquals("rule-1", rules.get(0).getId());
        assertEquals("rule-2", rules.get(1).getId());
    }

    @Test
    @DisplayName("listRules: 无规则时返回空列表")
    void listRules_empty_returnsEmptyList() {
        when(jobAlertRuleMapper.selectList(null)).thenReturn(Collections.emptyList());

        List<JobAlertRuleDO> rules = alertService.listRules();

        assertTrue(rules.isEmpty());
    }

    // ==================== toggleRule ====================

    @Test
    @DisplayName("toggleRule: enabled=null 抛 BizException")
    void toggleRule_nullEnabled_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> alertService.toggleRule("rule-1", null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).updateById(any(JobAlertRuleDO.class));
    }

    @Test
    @DisplayName("toggleRule: enabled=2 无效值抛 BizException")
    void toggleRule_invalidEnabled_throwsBizException() {
        BizException ex = assertThrows(BizException.class,
                () -> alertService.toggleRule("rule-1", 2));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).updateById(any(JobAlertRuleDO.class));
    }

    @Test
    @DisplayName("toggleRule: 规则不存在抛 BizException")
    void toggleRule_notFound_throwsBizException() {
        when(jobAlertRuleMapper.selectById("rule-x")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> alertService.toggleRule("rule-x", 1));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(jobAlertRuleMapper, never()).updateById(any(JobAlertRuleDO.class));
    }

    @Test
    @DisplayName("toggleRule: 正常切换启用状态为 0")
    void toggleRule_disable_normal() {
        JobAlertRuleDO exists = new JobAlertRuleDO();
        exists.setId("rule-1");
        exists.setEnabled(1);
        when(jobAlertRuleMapper.selectById("rule-1")).thenReturn(exists);

        alertService.toggleRule("rule-1", 0);

        ArgumentCaptor<JobAlertRuleDO> captor = ArgumentCaptor.forClass(JobAlertRuleDO.class);
        verify(jobAlertRuleMapper, times(1)).updateById(captor.capture());
        assertEquals(0, captor.getValue().getEnabled());
    }

    @Test
    @DisplayName("toggleRule: 正常切换启用状态为 1")
    void toggleRule_enable_normal() {
        JobAlertRuleDO exists = new JobAlertRuleDO();
        exists.setId("rule-1");
        exists.setEnabled(0);
        when(jobAlertRuleMapper.selectById("rule-1")).thenReturn(exists);

        alertService.toggleRule("rule-1", 1);

        ArgumentCaptor<JobAlertRuleDO> captor = ArgumentCaptor.forClass(JobAlertRuleDO.class);
        verify(jobAlertRuleMapper, times(1)).updateById(captor.capture());
        assertEquals(1, captor.getValue().getEnabled());
    }

    // ==================== queryAlertLogs ====================

    @Test
    @DisplayName("queryAlertLogs: jobId 为空时返回空列表")
    void queryAlertLogs_blankJobId_returnsEmptyList() {
        List<JobAlertLogDO> result = alertService.queryAlertLogs(null, null);

        assertTrue(result.isEmpty());
        verify(jobAlertLogMapper, never()).selectByJobIdSince(any(), any());
    }

    @Test
    @DisplayName("queryAlertLogs: 正常查询返回告警日志列表")
    void queryAlertLogs_normal_returnsLogs() {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        JobAlertLogDO log1 = new JobAlertLogDO();
        log1.setId("log-1");
        when(jobAlertLogMapper.selectByJobIdSince(eq("job-1"), eq(since)))
                .thenReturn(List.of(log1));

        List<JobAlertLogDO> result = alertService.queryAlertLogs("job-1", since);

        assertEquals(1, result.size());
        assertEquals("log-1", result.get(0).getId());
    }

    @Test
    @DisplayName("queryAlertLogs: since 为空时默认查询最近 7 天")
    void queryAlertLogs_nullSince_defaultsTo7Days() {
        JobAlertLogDO log1 = new JobAlertLogDO();
        log1.setId("log-1");
        when(jobAlertLogMapper.selectByJobIdSince(eq("job-1"), any(LocalDateTime.class)))
                .thenReturn(List.of(log1));

        List<JobAlertLogDO> result = alertService.queryAlertLogs("job-1", null);

        assertEquals(1, result.size());
        verify(jobAlertLogMapper, times(1)).selectByJobIdSince(eq("job-1"), any(LocalDateTime.class));
    }

    // ==================== 辅助方法 ====================

    private AlertRuleSaveDTO buildDto(String alertType, Long threshold, Integer timeWindowMinutes) {
        AlertRuleSaveDTO dto = new AlertRuleSaveDTO();
        dto.setRuleName("测试规则");
        dto.setAlertType(alertType);
        dto.setAlertLevel("WARN");
        dto.setThreshold(threshold);
        dto.setTimeWindowMinutes(timeWindowMinutes);
        dto.setChannels("[\"EMAIL\"]");
        dto.setReceivers("[\"admin@test.com\"]");
        dto.setCooldownMinutes(10);
        dto.setEnabled(1);
        return dto;
    }
}
