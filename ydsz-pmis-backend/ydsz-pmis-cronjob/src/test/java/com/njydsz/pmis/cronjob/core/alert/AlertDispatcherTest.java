package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.entity.JobAlertLogDO;
import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
import com.njydsz.pmis.cronjob.mapper.JobAlertLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobAlertRuleMapper;
import com.njydsz.pmis.cronjob.metrics.CronjobMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertDispatcher} 单元测试（P5 告警 + 监控）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>冷却窗口去重（CAS 成功/失败）</li>
 *   <li>通道 JSON 解析（正常/空/格式错误）</li>
 *   <li>接收人 JSON 解析</li>
 *   <li>多通道派发（全部成功/部分失败/全部失败）</li>
 *   <li>通知器缓存懒加载</li>
 *   <li>告警日志持久化</li>
 *   <li>status 判定（SUCCESS/PARTIAL/FAILED）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AlertDispatcher 告警派发器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class AlertDispatcherTest {

    @Mock
    private JobAlertRuleMapper jobAlertRuleMapper;
    @Mock
    private JobAlertLogMapper jobAlertLogMapper;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
    @Mock
    private AlertNotifier emailNotifier;
    @Mock
    private AlertNotifier dingtalkNotifier;

    @InjectMocks
    private AlertDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        // 默认 EMAIL 通知器注册到 Spring 容器
        lenient().when(emailNotifier.supportedChannel()).thenReturn(AlertChannel.EMAIL);
        lenient().when(applicationContext.getBeansOfType(AlertNotifier.class))
                .thenReturn(Map.of("emailNotifier", emailNotifier));
        // P6-2: CronjobMetrics 默认不可用（指标收集器在测试中不启用）
        lenient().when(cronjobMetricsProvider.getIfAvailable()).thenReturn(null);
    }

    @Test
    @DisplayName("dispatch: 冷却窗口内（CAS 失败）跳过告警")
    void dispatch_inCooldown_skipsAlert() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 10);
        AlertContext context = buildContext();
        when(jobAlertRuleMapper.updateLastAlertAtIfNotInCooldown(
                eq("rule-1"), any(), any())).thenReturn(0);

        dispatcher.dispatch(context, rule);

        verify(jobAlertLogMapper, never()).insert(any(JobAlertLogDO.class));
        verify(emailNotifier, never()).notify(any(), any(), any());
    }

    @Test
    @DisplayName("dispatch: 无冷却时间（cooldownMinutes=0）直接派发")
    void dispatch_noCooldown_dispatchesDirectly() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"EMAIL\"]");
        AlertContext context = buildContext();

        dispatcher.dispatch(context, rule);

        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), any());
        verify(jobAlertLogMapper, times(1)).insert(any(JobAlertLogDO.class));
        // cooldownMinutes=0 不应调用 CAS 更新
        verify(jobAlertRuleMapper, never()).updateLastAlertAtIfNotInCooldown(
                anyString(), any(), any());
    }

    @Test
    @DisplayName("dispatch: 冷却窗口外（CAS 成功）正常派发")
    void dispatch_outsideCooldown_dispatches() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 10);
        rule.setChannels("[\"EMAIL\"]");
        AlertContext context = buildContext();
        when(jobAlertRuleMapper.updateLastAlertAtIfNotInCooldown(
                eq("rule-1"), any(), any())).thenReturn(1);

        dispatcher.dispatch(context, rule);

        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), any());
        verify(jobAlertLogMapper, times(1)).insert(any(JobAlertLogDO.class));
    }

    @Test
    @DisplayName("dispatch: 通道 JSON 为空时跳过派发")
    void dispatch_emptyChannels_skips() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("");
        AlertContext context = buildContext();

        dispatcher.dispatch(context, rule);

        verify(emailNotifier, never()).notify(any(), any(), any());
        verify(jobAlertLogMapper, never()).insert(any(JobAlertLogDO.class));
    }

    @Test
    @DisplayName("dispatch: 通道 JSON 格式错误时跳过派发")
    void dispatch_invalidChannelsJson_skips() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("not-a-json");
        AlertContext context = buildContext();

        dispatcher.dispatch(context, rule);

        verify(emailNotifier, never()).notify(any(), any(), any());
        verify(jobAlertLogMapper, never()).insert(any(JobAlertLogDO.class));
    }

    @Test
    @DisplayName("dispatch: 多通道全部成功时 status=SUCCESS")
    void dispatch_allChannelsSucceed_statusSuccess() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"EMAIL\",\"DINGTALK\"]");
        AlertContext context = buildContext();
        // 注册两个通道
        when(dingtalkNotifier.supportedChannel()).thenReturn(AlertChannel.DINGTALK);
        when(applicationContext.getBeansOfType(AlertNotifier.class))
                .thenReturn(Map.of("emailNotifier", emailNotifier,
                        "dingtalkNotifier", dingtalkNotifier));

        dispatcher.dispatch(context, rule);

        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), any());
        verify(dingtalkNotifier, times(1)).notify(eq(context), eq(rule), any());
        ArgumentCaptor<JobAlertLogDO> logCaptor = ArgumentCaptor.forClass(JobAlertLogDO.class);
        verify(jobAlertLogMapper, times(1)).insert(logCaptor.capture());
        assertEquals("SUCCESS", logCaptor.getValue().getStatus());
        assertNull(logCaptor.getValue().getErrorMessage());
    }

    @Test
    @DisplayName("dispatch: 多通道部分失败时 status=PARTIAL")
    void dispatch_partialFailure_statusPartial() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"EMAIL\",\"DINGTALK\"]");
        AlertContext context = buildContext();
        when(dingtalkNotifier.supportedChannel()).thenReturn(AlertChannel.DINGTALK);
        when(applicationContext.getBeansOfType(AlertNotifier.class))
                .thenReturn(Map.of("emailNotifier", emailNotifier,
                        "dingtalkNotifier", dingtalkNotifier));
        // 钉钉通道抛出 AlertSendException
        doThrow(new AlertSendException("dingtalk down"))
                .when(dingtalkNotifier).notify(eq(context), eq(rule), any());

        dispatcher.dispatch(context, rule);

        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), any());
        verify(dingtalkNotifier, times(1)).notify(eq(context), eq(rule), any());
        ArgumentCaptor<JobAlertLogDO> logCaptor = ArgumentCaptor.forClass(JobAlertLogDO.class);
        verify(jobAlertLogMapper, times(1)).insert(logCaptor.capture());
        assertEquals("PARTIAL", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("dispatch: 多通道全部失败时 status=FAILED")
    void dispatch_allChannelsFail_statusFailed() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"EMAIL\",\"DINGTALK\"]");
        AlertContext context = buildContext();
        when(dingtalkNotifier.supportedChannel()).thenReturn(AlertChannel.DINGTALK);
        when(applicationContext.getBeansOfType(AlertNotifier.class))
                .thenReturn(Map.of("emailNotifier", emailNotifier,
                        "dingtalkNotifier", dingtalkNotifier));
        // 两个通道都抛出异常
        doThrow(new AlertSendException("email down"))
                .when(emailNotifier).notify(eq(context), eq(rule), any());
        doThrow(new AlertSendException("dingtalk down"))
                .when(dingtalkNotifier).notify(eq(context), eq(rule), any());

        dispatcher.dispatch(context, rule);

        ArgumentCaptor<JobAlertLogDO> logCaptor = ArgumentCaptor.forClass(JobAlertLogDO.class);
        verify(jobAlertLogMapper, times(1)).insert(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("dispatch: 通道对应 Notifier 未注册时记录为失败通道")
    void dispatch_notifierNotRegistered_countsAsFailed() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"DINGTALK\"]");
        AlertContext context = buildContext();
        // 仅注册 EMAIL 通知器，DINGTALK 未注册
        when(applicationContext.getBeansOfType(AlertNotifier.class))
                .thenReturn(Map.of("emailNotifier", emailNotifier));

        dispatcher.dispatch(context, rule);

        verify(emailNotifier, never()).notify(any(), any(), any());
        ArgumentCaptor<JobAlertLogDO> logCaptor = ArgumentCaptor.forClass(JobAlertLogDO.class);
        verify(jobAlertLogMapper, times(1)).insert(logCaptor.capture());
        // 1 个通道全部失败 -> FAILED
        assertEquals("FAILED", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("dispatch: 告警日志写入失败不影响主流程")
    void dispatch_logPersistFails_doesNotPropagate() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"EMAIL\"]");
        AlertContext context = buildContext();
        doThrow(new RuntimeException("DB error"))
                .when(jobAlertLogMapper).insert(any(JobAlertLogDO.class));

        // 不应抛出异常
        dispatcher.dispatch(context, rule);

        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), any());
    }

    @Test
    @DisplayName("dispatch: 告警日志包含正确字段")
    void dispatch_alertLogContainsCorrectFields() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"EMAIL\"]");
        rule.setAlertType("FAIL");
        rule.setAlertLevel("ERROR");
        rule.setThreshold(5000L);
        AlertContext context = AlertContext.of(
                AlertType.FAIL, "job-1", "key-1", "name-1",
                "log-1", "5000", "NPE", "trace-1", "tenant-1");

        dispatcher.dispatch(context, rule);

        ArgumentCaptor<JobAlertLogDO> logCaptor = ArgumentCaptor.forClass(JobAlertLogDO.class);
        verify(jobAlertLogMapper, times(1)).insert(logCaptor.capture());
        JobAlertLogDO alertLog = logCaptor.getValue();
        assertEquals("rule-1", alertLog.getRuleId());
        assertEquals("job-1", alertLog.getJobId());
        assertEquals("FAIL", alertLog.getAlertType());
        assertEquals("ERROR", alertLog.getAlertLevel());
        assertEquals("5000", alertLog.getTriggerValue());
        assertEquals(5000L, alertLog.getThreshold());
        assertEquals("trace-1", alertLog.getTraceId());
        assertEquals("log-1", alertLog.getTriggerLogId());
        assertEquals("tenant-1", alertLog.getTenantId());
    }

    @Test
    @DisplayName("dispatch: 接收人列表正确解析并传入 notifier")
    void dispatch_receiversParsedCorrectly() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"EMAIL\"]");
        rule.setReceivers("[\"admin@test.com\",\"ops@test.com\"]");
        AlertContext context = buildContext();

        dispatcher.dispatch(context, rule);

        ArgumentCaptor<List<String>> receiversCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), receiversCaptor.capture());
        List<String> receivers = receiversCaptor.getValue();
        assertEquals(2, receivers.size());
        assertEquals("admin@test.com", receivers.get(0));
        assertEquals("ops@test.com", receivers.get(1));
    }

    @Test
    @DisplayName("dispatch: 接收人 JSON 格式错误时返回空列表")
    void dispatch_invalidReceiversJson_returnsEmptyList() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"EMAIL\"]");
        rule.setReceivers("not-a-json");
        AlertContext context = buildContext();

        dispatcher.dispatch(context, rule);

        ArgumentCaptor<List<String>> receiversCaptor = ArgumentCaptor.forClass(List.class);
        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), receiversCaptor.capture());
        assertEquals(0, receiversCaptor.getValue().size());
    }

    @Test
    @DisplayName("dispatch: 通道抛出 RuntimeException 时按失败处理")
    void dispatch_notifierThrowsRuntimeException_countsAsFailed() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 0);
        rule.setChannels("[\"EMAIL\"]");
        AlertContext context = buildContext();
        // 抛出非 AlertSendException 的 RuntimeException
        doThrow(new RuntimeException("unexpected error"))
                .when(emailNotifier).notify(any(), any(), any());

        dispatcher.dispatch(context, rule);

        ArgumentCaptor<JobAlertLogDO> logCaptor = ArgumentCaptor.forClass(JobAlertLogDO.class);
        verify(jobAlertLogMapper, times(1)).insert(logCaptor.capture());
        // 1 个通道失败 -> FAILED
        assertEquals("FAILED", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("onAlertEvent: 异常时不向外抛出")
    void onAlertEvent_dispatchThrows_doesNotPropagate() {
        JobAlertRuleDO rule = buildRule("rule-1", 10);
        AlertContext context = buildContext();
        // CAS 更新抛出异常
        when(jobAlertRuleMapper.updateLastAlertAtIfNotInCooldown(
                anyString(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        AlertEvent event = AlertEvent.of(context, rule);
        // 不应抛出异常
        dispatcher.onAlertEvent(event);
    }

    // ==================== P3-1: 恢复通知测试 ====================

    @Test
    @DisplayName("dispatch: 恢复通知跳过冷却窗口检查(不调用 CAS)")
    void dispatch_recovery_skipsCooldownCheck() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 10);
        rule.setChannels("[\"EMAIL\"]");
        AlertContext context = buildRecoveryContext();

        dispatcher.dispatch(context, rule);

        // 恢复通知不应调用 CAS 更新（跳过冷却窗口）
        verify(jobAlertRuleMapper, never()).updateLastAlertAtIfNotInCooldown(
                anyString(), any(), any());
        // 应当直接派发到通知器
        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), any());
    }

    @Test
    @DisplayName("dispatch: 恢复通知全部通道成功时 status=SUCCESS_RECOVERY")
    void dispatch_recoveryAllChannelsSucceed_statusSuccessRecovery() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 10);
        rule.setChannels("[\"EMAIL\"]");
        AlertContext context = buildRecoveryContext();

        dispatcher.dispatch(context, rule);

        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), any());
        ArgumentCaptor<JobAlertLogDO> logCaptor = ArgumentCaptor.forClass(JobAlertLogDO.class);
        verify(jobAlertLogMapper, times(1)).insert(logCaptor.capture());
        assertEquals("SUCCESS_RECOVERY", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("dispatch: 恢复通知部分通道失败时 status=PARTIAL_RECOVERY")
    void dispatch_recoveryPartialFailure_statusPartialRecovery() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 10);
        rule.setChannels("[\"EMAIL\",\"DINGTALK\"]");
        AlertContext context = buildRecoveryContext();
        when(dingtalkNotifier.supportedChannel()).thenReturn(AlertChannel.DINGTALK);
        when(applicationContext.getBeansOfType(AlertNotifier.class))
                .thenReturn(Map.of("emailNotifier", emailNotifier,
                        "dingtalkNotifier", dingtalkNotifier));
        // 钉钉通道抛出异常
        doThrow(new AlertSendException("dingtalk down"))
                .when(dingtalkNotifier).notify(eq(context), eq(rule), any());

        dispatcher.dispatch(context, rule);

        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), any());
        verify(dingtalkNotifier, times(1)).notify(eq(context), eq(rule), any());
        ArgumentCaptor<JobAlertLogDO> logCaptor = ArgumentCaptor.forClass(JobAlertLogDO.class);
        verify(jobAlertLogMapper, times(1)).insert(logCaptor.capture());
        assertEquals("PARTIAL_RECOVERY", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("dispatch: 恢复通知全部通道失败时 status=FAILED_RECOVERY")
    void dispatch_recoveryAllChannelsFail_statusFailedRecovery() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 10);
        rule.setChannels("[\"EMAIL\"]");
        AlertContext context = buildRecoveryContext();
        doThrow(new AlertSendException("email down"))
                .when(emailNotifier).notify(eq(context), eq(rule), any());

        dispatcher.dispatch(context, rule);

        ArgumentCaptor<JobAlertLogDO> logCaptor = ArgumentCaptor.forClass(JobAlertLogDO.class);
        verify(jobAlertLogMapper, times(1)).insert(logCaptor.capture());
        assertEquals("FAILED_RECOVERY", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("dispatch: 恢复通知 context.recovery()=true 传入 notifier")
    void dispatch_recovery_contextPassedToNotifierHasRecoveryFlag() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 10);
        rule.setChannels("[\"EMAIL\"]");
        AlertContext context = buildRecoveryContext();

        dispatcher.dispatch(context, rule);

        ArgumentCaptor<AlertContext> ctxCaptor = ArgumentCaptor.forClass(AlertContext.class);
        verify(emailNotifier, times(1)).notify(ctxCaptor.capture(), eq(rule), any());
        assertEquals(true, ctxCaptor.getValue().recovery());
    }

    @Test
    @DisplayName("onAlertEvent: 恢复事件触发派发且跳过冷却")
    void onAlertEvent_recoveryEvent_dispatchesWithoutCooldown() throws Exception {
        JobAlertRuleDO rule = buildRule("rule-1", 10);
        rule.setChannels("[\"EMAIL\"]");
        AlertContext context = buildRecoveryContext();
        AlertEvent event = AlertEvent.recovery(context, rule);

        dispatcher.onAlertEvent(event);

        verify(jobAlertRuleMapper, never()).updateLastAlertAtIfNotInCooldown(
                anyString(), any(), any());
        verify(emailNotifier, times(1)).notify(eq(context), eq(rule), any());
    }

    // ==================== 辅助方法 ====================

    private AlertContext buildContext() {
        return AlertContext.of(
                AlertType.FAIL, "job-1", "key-1", "name-1",
                "log-1", null, null, "trace-1", "tenant-1");
    }

    private AlertContext buildRecoveryContext() {
        return AlertContext.recovery(
                AlertType.FAIL, "job-1", "key-1", "name-1",
                "log-1", null, null, "trace-1", "tenant-1");
    }

    private JobAlertRuleDO buildRule(String id, int cooldownMinutes) {
        JobAlertRuleDO rule = new JobAlertRuleDO();
        rule.setId(id);
        rule.setRuleName("rule-" + id);
        rule.setAlertType("FAIL");
        rule.setAlertLevel("WARN");
        rule.setChannels("[\"EMAIL\"]");
        rule.setReceivers("[\"admin@test.com\"]");
        rule.setCooldownMinutes(cooldownMinutes);
        rule.setEnabled(1);
        return rule;
    }
}
