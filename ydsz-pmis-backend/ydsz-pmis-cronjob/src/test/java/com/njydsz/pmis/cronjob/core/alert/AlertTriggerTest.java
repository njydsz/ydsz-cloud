package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertTrigger} 单元测试（P5 告警 + 监控）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AlertTrigger 告警触发器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertTriggerTest {

    @Mock
    private JobAlertRuleMapper jobAlertRuleMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlertTrigger alertTrigger;

    @Test
    @DisplayName("trigger: 无匹配规则时不发布事件")
    void trigger_noMatchingRules_doesNotPublish() {
        AlertContext context = buildContext(AlertType.FAIL, "job-A");
        when(jobAlertRuleMapper.selectByJobIdOrGlobal("job-A")).thenReturn(Collections.emptyList());

        alertTrigger.trigger(context);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("trigger: 匹配 FAIL 规则时发布事件")
    void trigger_matchingFailRule_publishesEvent() {
        AlertContext context = buildContext(AlertType.FAIL, "job-A");
        JobAlertRuleDO rule = buildRule("rule-1", "FAIL", null);
        when(jobAlertRuleMapper.selectByJobIdOrGlobal("job-A")).thenReturn(List.of(rule));

        alertTrigger.trigger(context);

        verify(eventPublisher, times(1)).publishEvent(any(AlertEvent.class));
    }

    @Test
    @DisplayName("trigger: alertType 不匹配时不发布事件")
    void trigger_alertTypeMismatch_doesNotPublish() {
        AlertContext context = buildContext(AlertType.FAIL, "job-A");
        // 规则是 SLOW 类型，但 context 是 FAIL
        JobAlertRuleDO rule = buildRule("rule-1", "SLOW", 5000L);
        when(jobAlertRuleMapper.selectByJobIdOrGlobal("job-A")).thenReturn(List.of(rule));

        alertTrigger.trigger(context);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("trigger: SLOW 类型且耗时 >= 阈值时发布事件")
    void trigger_slowAboveThreshold_publishesEvent() {
        AlertContext context = buildContext(AlertType.SLOW, "job-A", "8000");
        JobAlertRuleDO rule = buildRule("rule-1", "SLOW", 5000L);
        when(jobAlertRuleMapper.selectByJobIdOrGlobal("job-A")).thenReturn(List.of(rule));

        alertTrigger.trigger(context);

        verify(eventPublisher, times(1)).publishEvent(any(AlertEvent.class));
    }

    @Test
    @DisplayName("trigger: SLOW 类型且耗时 < 阈值时不发布事件")
    void trigger_slowBelowThreshold_doesNotPublish() {
        AlertContext context = buildContext(AlertType.SLOW, "job-A", "3000");
        JobAlertRuleDO rule = buildRule("rule-1", "SLOW", 5000L);
        when(jobAlertRuleMapper.selectByJobIdOrGlobal("job-A")).thenReturn(List.of(rule));

        alertTrigger.trigger(context);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("trigger: SLOW 类型 triggerValue 非数字时不发布事件")
    void trigger_slowNonNumericTriggerValue_doesNotPublish() {
        AlertContext context = buildContext(AlertType.SLOW, "job-A", "abc");
        JobAlertRuleDO rule = buildRule("rule-1", "SLOW", 5000L);
        when(jobAlertRuleMapper.selectByJobIdOrGlobal("job-A")).thenReturn(List.of(rule));

        alertTrigger.trigger(context);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("trigger: 全局 jobId 时查询全部启用规则")
    void trigger_globalJobId_loadsAllEnabled() {
        AlertContext context = new AlertContext(
                AlertType.FAIL, null, "global-job", "global", null,
                null, null, null, "1");
        JobAlertRuleDO rule = buildRule("rule-1", "FAIL", null);
        when(jobAlertRuleMapper.selectAllEnabled()).thenReturn(List.of(rule));

        alertTrigger.trigger(context);

        verify(jobAlertRuleMapper, times(1)).selectAllEnabled();
        verify(jobAlertRuleMapper, never()).selectByJobIdOrGlobal(any());
        verify(eventPublisher, times(1)).publishEvent(any(AlertEvent.class));
    }

    @Test
    @DisplayName("trigger: 多条匹配规则时全部发布事件")
    void trigger_multipleMatchingRules_publishesAll() {
        AlertContext context = buildContext(AlertType.FAIL, "job-A");
        JobAlertRuleDO rule1 = buildRule("rule-1", "FAIL", null);
        JobAlertRuleDO rule2 = buildRule("rule-2", "FAIL", null);
        when(jobAlertRuleMapper.selectByJobIdOrGlobal("job-A")).thenReturn(List.of(rule1, rule2));

        alertTrigger.trigger(context);

        verify(eventPublisher, times(2)).publishEvent(any(AlertEvent.class));
    }

    @Test
    @DisplayName("trigger: 发布事件携带正确规则")
    void trigger_publishesEventWithCorrectRule() {
        AlertContext context = buildContext(AlertType.FAIL, "job-A");
        JobAlertRuleDO rule = buildRule("rule-1", "FAIL", null);
        when(jobAlertRuleMapper.selectByJobIdOrGlobal("job-A")).thenReturn(List.of(rule));

        alertTrigger.trigger(context);

        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AlertEvent event = captor.getValue();
        assertEquals("rule-1", event.rule().getId());
        assertEquals(context, event.context());
    }

    @Test
    @DisplayName("trigger: 异常时不影响主流程(不抛出)")
    void trigger_mapperThrows_doesNotPropagate() {
        AlertContext context = buildContext(AlertType.FAIL, "job-A");
        when(jobAlertRuleMapper.selectByJobIdOrGlobal("job-A"))
                .thenThrow(new RuntimeException("DB error"));

        // 不应抛出异常
        alertTrigger.trigger(context);
    }

    // ==================== 辅助方法 ====================

    private AlertContext buildContext(AlertType alertType, String jobId) {
        return buildContext(alertType, jobId, null);
    }

    private AlertContext buildContext(AlertType alertType, String jobId, String triggerValue) {
        return new AlertContext(
                alertType, jobId, "key-" + jobId, "name-" + jobId,
                "log-1", triggerValue, null, "trace-1", "1");
    }

    private JobAlertRuleDO buildRule(String id, String alertType, Long threshold) {
        JobAlertRuleDO rule = new JobAlertRuleDO();
        rule.setId(id);
        rule.setRuleName("rule-" + id);
        rule.setAlertType(alertType);
        rule.setAlertLevel("WARN");
        rule.setThreshold(threshold);
        rule.setChannels("[\"EMAIL\"]");
        rule.setCooldownMinutes(10);
        rule.setEnabled(1);
        return rule;
    }
}
