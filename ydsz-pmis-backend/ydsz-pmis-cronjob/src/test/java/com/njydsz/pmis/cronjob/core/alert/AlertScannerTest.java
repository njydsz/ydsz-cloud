package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobAlertRuleDO;
import com.njydsz.pmis.cronjob.mapper.JobAlertRuleMapper;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertScanner} 单元测试（P3-2 周期性告警扫描）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>Leader 禁用 / 非 Leader 时跳过扫描</li>
 *   <li>无规则时跳过</li>
 *   <li>FAIL_RATE: 失败率低于阈值 / 高于阈值 / 无执行记录</li>
 *   <li>DURATION_P95: P95 低于阈值 / 高于阈值 / 无成功记录</li>
 *   <li>全局规则（jobId=null）跳过周期性扫描</li>
 *   <li>阈值无效规则跳过</li>
 *   <li>单条规则异常不影响其他规则</li>
 *   <li>timeWindowMinutes 缺省时使用 30 分钟默认窗口</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AlertScanner 周期性告警扫描测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertScannerTest {

    @Mock
    private JobAlertRuleMapper jobAlertRuleMapper;
    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private AlertTrigger alertTrigger;
    @Mock
    private LeaderElector leaderElector;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private AlertScanner scanner;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@InjectMocks 不会自动创建配置对象）
        java.lang.reflect.Field f = AlertScanner.class.getDeclaredField("cronjobProperties");
        f.setAccessible(true);
        f.set(scanner, cronjobProperties);
        scanner.init();
        // 默认为 Leader，便于多数测试用例直接使用
        lenient().when(leaderElector.isLeader(anyString())).thenReturn(true);
        // 默认无规则
        lenient().when(jobAlertRuleMapper.selectByAlertType(anyString())).thenReturn(Collections.emptyList());
    }

    // ==================== scan 入口测试 ====================

    @Test
    @DisplayName("scan: 非 Leader 时跳过扫描")
    void scan_notLeader_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(false);

        scanner.scan();

        verify(jobAlertRuleMapper, never()).selectByAlertType(anyString());
        verify(jobLogMapper, never()).countByJobIdSince(anyString(), any());
        verify(jobLogMapper, never()).selectDurationP95(anyString(), any());
        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scan: 无规则时跳过")
    void scan_noRules_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        when(jobAlertRuleMapper.selectByAlertType(anyString())).thenReturn(Collections.emptyList());

        scanner.scan();

        verify(jobLogMapper, never()).countByJobIdSince(anyString(), any());
        verify(jobLogMapper, never()).selectDurationP95(anyString(), any());
        verify(alertTrigger, never()).trigger(any());
    }

    // ==================== FAIL_RATE 测试 ====================

    @Test
    @DisplayName("scanFailRateRules: 失败率低于阈值时不触发告警")
    void scanFailRate_belowThreshold_skip() {
        JobAlertRuleDO rule = buildFailRateRule("rule-1", "job-1", 80L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenReturn(List.of(rule));
        // total=10, failed=5 → failRate=50% < 80
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any(LocalDateTime.class)))
                .thenReturn(stats(10L, 5L));

        scanner.scanFailRateRules();

        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scanFailRateRules: 失败率超过阈值时触发告警")
    void scanFailRate_aboveThreshold_trigger() {
        JobAlertRuleDO rule = buildFailRateRule("rule-1", "job-1", 80L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenReturn(List.of(rule));
        // total=10, failed=9 → failRate=90% >= 80
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any(LocalDateTime.class)))
                .thenReturn(stats(10L, 9L));

        scanner.scanFailRateRules();

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    @Test
    @DisplayName("scanFailRateRules: 无执行记录时不触发告警")
    void scanFailRate_noExecutions_skip() {
        JobAlertRuleDO rule = buildFailRateRule("rule-1", "job-1", 80L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenReturn(List.of(rule));
        // total=0, failed=0 → 无执行记录，跳过
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any(LocalDateTime.class)))
                .thenReturn(stats(0L, 0L));

        scanner.scanFailRateRules();

        verify(alertTrigger, never()).trigger(any());
    }

    // ==================== DURATION_P95 测试 ====================

    @Test
    @DisplayName("scanDurationP95Rules: P95 低于阈值时不触发告警")
    void scanDurationP95_belowThreshold_skip() {
        JobAlertRuleDO rule = buildDurationP95Rule("rule-2", "job-2", 5000L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.DURATION_P95.name()))
                .thenReturn(List.of(rule));
        // P95=3000ms < 5000ms
        when(jobLogMapper.selectDurationP95(eq("job-2"), any(LocalDateTime.class)))
                .thenReturn(3000L);

        scanner.scanDurationP95Rules();

        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scanDurationP95Rules: P95 超过阈值时触发告警")
    void scanDurationP95_aboveThreshold_trigger() {
        JobAlertRuleDO rule = buildDurationP95Rule("rule-2", "job-2", 5000L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.DURATION_P95.name()))
                .thenReturn(List.of(rule));
        // P95=8000ms >= 5000ms
        when(jobLogMapper.selectDurationP95(eq("job-2"), any(LocalDateTime.class)))
                .thenReturn(8000L);

        scanner.scanDurationP95Rules();

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    @Test
    @DisplayName("scanDurationP95Rules: 无成功执行记录时不触发告警")
    void scanDurationP95_noSuccessExecutions_skip() {
        JobAlertRuleDO rule = buildDurationP95Rule("rule-2", "job-2", 5000L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.DURATION_P95.name()))
                .thenReturn(List.of(rule));
        // PERCENTILE_CONT 在无数据时返回 0
        when(jobLogMapper.selectDurationP95(eq("job-2"), any(LocalDateTime.class)))
                .thenReturn(0L);

        scanner.scanDurationP95Rules();

        verify(alertTrigger, never()).trigger(any());
    }

    // ==================== 边界场景测试 ====================

    @Test
    @DisplayName("scanFailRateRules: 全局规则(jobId=null)跳过周期性扫描")
    void scanFailRate_globalRule_skip() {
        JobAlertRuleDO rule = buildFailRateRule("rule-1", null, 80L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenReturn(List.of(rule));

        scanner.scanFailRateRules();

        verify(jobLogMapper, never()).countByJobIdSince(anyString(), any());
        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scanDurationP95Rules: 全局规则(jobId=null)跳过周期性扫描")
    void scanDurationP95_globalRule_skip() {
        JobAlertRuleDO rule = buildDurationP95Rule("rule-2", null, 5000L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.DURATION_P95.name()))
                .thenReturn(List.of(rule));

        scanner.scanDurationP95Rules();

        verify(jobLogMapper, never()).selectDurationP95(anyString(), any());
        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scanFailRateRules: 阈值无效规则跳过")
    void scanFailRate_invalidThreshold_skip() {
        JobAlertRuleDO rule = buildFailRateRule("rule-1", "job-1", null, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenReturn(List.of(rule));

        scanner.scanFailRateRules();

        verify(jobLogMapper, never()).countByJobIdSince(anyString(), any());
        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scanDurationP95Rules: 阈值无效规则跳过")
    void scanDurationP95_invalidThreshold_skip() {
        JobAlertRuleDO rule = buildDurationP95Rule("rule-2", "job-2", null, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.DURATION_P95.name()))
                .thenReturn(List.of(rule));

        scanner.scanDurationP95Rules();

        verify(jobLogMapper, never()).selectDurationP95(anyString(), any());
        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scanFailRateRules: 单条规则异常不影响其他规则")
    void scanFailRate_singleRuleException_continuesOthers() {
        JobAlertRuleDO rule1 = buildFailRateRule("rule-1", "job-1", 80L, 30);
        JobAlertRuleDO rule2 = buildFailRateRule("rule-2", "job-2", 80L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenReturn(List.of(rule1, rule2));
        // rule1 统计抛异常，rule2 正常且超过阈值
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB err"));
        when(jobLogMapper.countByJobIdSince(eq("job-2"), any(LocalDateTime.class)))
                .thenReturn(stats(10L, 9L));

        scanner.scanFailRateRules(); // 不应抛异常

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    @Test
    @DisplayName("scanDurationP95Rules: 单条规则异常不影响其他规则")
    void scanDurationP95_singleRuleException_continuesOthers() {
        JobAlertRuleDO rule1 = buildDurationP95Rule("rule-1", "job-1", 5000L, 30);
        JobAlertRuleDO rule2 = buildDurationP95Rule("rule-2", "job-2", 5000L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.DURATION_P95.name()))
                .thenReturn(List.of(rule1, rule2));
        when(jobLogMapper.selectDurationP95(eq("job-1"), any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB err"));
        when(jobLogMapper.selectDurationP95(eq("job-2"), any(LocalDateTime.class)))
                .thenReturn(8000L);

        scanner.scanDurationP95Rules(); // 不应抛异常

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    @Test
    @DisplayName("scanFailRateRules: timeWindowMinutes 缺省时使用 30 分钟默认窗口")
    void scanFailRate_defaultWindow_uses30Minutes() {
        JobAlertRuleDO rule = buildFailRateRule("rule-1", "job-1", 80L, null);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenReturn(List.of(rule));
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any(LocalDateTime.class)))
                .thenReturn(stats(10L, 9L));

        scanner.scanFailRateRules();

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    @Test
    @DisplayName("scanDurationP95Rules: timeWindowMinutes 缺省时使用 30 分钟默认窗口")
    void scanDurationP95_defaultWindow_uses30Minutes() {
        JobAlertRuleDO rule = buildDurationP95Rule("rule-2", "job-2", 5000L, null);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.DURATION_P95.name()))
                .thenReturn(List.of(rule));
        when(jobLogMapper.selectDurationP95(eq("job-2"), any(LocalDateTime.class)))
                .thenReturn(8000L);

        scanner.scanDurationP95Rules();

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    @Test
    @DisplayName("scanFailRateRules: 触发的 AlertContext 携带正确的告警类型与 jobId")
    void scanFailRate_triggerContextCorrect() {
        JobAlertRuleDO rule = buildFailRateRule("rule-1", "job-1", 80L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenReturn(List.of(rule));
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any(LocalDateTime.class)))
                .thenReturn(stats(10L, 9L));

        scanner.scanFailRateRules();

        org.mockito.ArgumentCaptor<AlertContext> captor =
                org.mockito.ArgumentCaptor.forClass(AlertContext.class);
        verify(alertTrigger).trigger(captor.capture());
        AlertContext ctx = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(AlertType.FAIL_RATE, ctx.alertType());
        org.junit.jupiter.api.Assertions.assertEquals("job-1", ctx.jobId());
        org.junit.jupiter.api.Assertions.assertEquals("key-job-1", ctx.jobKey());
        org.junit.jupiter.api.Assertions.assertEquals("1", ctx.tenantId());
        org.junit.jupiter.api.Assertions.assertNotNull(ctx.triggerValue());
    }

    @Test
    @DisplayName("scanDurationP95Rules: 触发的 AlertContext 携带正确的告警类型与 jobId")
    void scanDurationP95_triggerContextCorrect() {
        JobAlertRuleDO rule = buildDurationP95Rule("rule-2", "job-2", 5000L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.DURATION_P95.name()))
                .thenReturn(List.of(rule));
        when(jobLogMapper.selectDurationP95(eq("job-2"), any(LocalDateTime.class)))
                .thenReturn(8000L);

        scanner.scanDurationP95Rules();

        org.mockito.ArgumentCaptor<AlertContext> captor =
                org.mockito.ArgumentCaptor.forClass(AlertContext.class);
        verify(alertTrigger).trigger(captor.capture());
        AlertContext ctx = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(AlertType.DURATION_P95, ctx.alertType());
        org.junit.jupiter.api.Assertions.assertEquals("job-2", ctx.jobId());
        org.junit.jupiter.api.Assertions.assertEquals("key-job-2", ctx.jobKey());
        org.junit.jupiter.api.Assertions.assertEquals("8000", ctx.triggerValue());
    }

    @Test
    @DisplayName("scan: leader.enabled=false 时跳过扫描")
    void scan_leaderDisabled_skip() {
        cronjobProperties.getLeader().setEnabled(false);

        scanner.scan();

        verify(jobAlertRuleMapper, never()).selectByAlertType(anyString());
        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scan: doScan 异常被外层 try-catch 捕获不影响下次")
    void scan_doScanException_swallowed() {
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenThrow(new RuntimeException("scan err"));

        scanner.scan(); // 不应抛异常

        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scanFailRateRules: 失败率恰好等于阈值时触发告警（>= 触发）")
    void scanFailRate_equalThreshold_trigger() {
        JobAlertRuleDO rule = buildFailRateRule("rule-1", "job-1", 50L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.FAIL_RATE.name()))
                .thenReturn(List.of(rule));
        // total=10, failed=5 → failRate=50% == 50
        when(jobLogMapper.countByJobIdSince(eq("job-1"), any(LocalDateTime.class)))
                .thenReturn(stats(10L, 5L));

        scanner.scanFailRateRules();

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    @Test
    @DisplayName("scanDurationP95Rules: P95 恰好等于阈值时触发告警（>= 触发）")
    void scanDurationP95_equalThreshold_trigger() {
        JobAlertRuleDO rule = buildDurationP95Rule("rule-2", "job-2", 5000L, 30);
        when(jobAlertRuleMapper.selectByAlertType(AlertType.DURATION_P95.name()))
                .thenReturn(List.of(rule));
        // P95=5000ms == 5000ms
        when(jobLogMapper.selectDurationP95(eq("job-2"), any(LocalDateTime.class)))
                .thenReturn(5000L);

        scanner.scanDurationP95Rules();

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    // ==================== 辅助方法 ====================

    private JobAlertRuleDO buildFailRateRule(String id, String jobId, Long threshold, Integer windowMinutes) {
        JobAlertRuleDO rule = new JobAlertRuleDO();
        rule.setId(id);
        rule.setRuleName("rule-" + id);
        rule.setJobId(jobId);
        rule.setJobKey(jobId == null ? null : "key-" + jobId);
        rule.setAlertType(AlertType.FAIL_RATE.name());
        rule.setAlertLevel("WARN");
        rule.setThreshold(threshold);
        rule.setTimeWindowMinutes(windowMinutes);
        rule.setChannels("[\"EMAIL\"]");
        rule.setCooldownMinutes(10);
        rule.setEnabled(1);
        rule.setTenantId("1");
        return rule;
    }

    private JobAlertRuleDO buildDurationP95Rule(String id, String jobId, Long threshold, Integer windowMinutes) {
        JobAlertRuleDO rule = new JobAlertRuleDO();
        rule.setId(id);
        rule.setRuleName("rule-" + id);
        rule.setJobId(jobId);
        rule.setJobKey(jobId == null ? null : "key-" + jobId);
        rule.setAlertType(AlertType.DURATION_P95.name());
        rule.setAlertLevel("WARN");
        rule.setThreshold(threshold);
        rule.setTimeWindowMinutes(windowMinutes);
        rule.setChannels("[\"EMAIL\"]");
        rule.setCooldownMinutes(10);
        rule.setEnabled(1);
        rule.setTenantId("1");
        return rule;
    }

    private Map<String, Object> stats(long total, long failed) {
        Map<String, Object> map = new HashMap<>();
        map.put("total", total);
        map.put("failed", failed);
        return map;
    }
}
