package com.njydsz.pmis.cronjob.core.sla;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.alert.AlertContext;
import com.njydsz.pmis.cronjob.core.alert.AlertTrigger;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobSlaDO;
import com.njydsz.pmis.cronjob.mapper.JobSlaMapper;
import com.njydsz.pmis.cronjob.service.JobSlaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SlaScanner} 单元测试（P2-7 SLA 管理）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>scan: Leader 禁用 / 非 Leader 时跳过</li>
 *   <li>scanSlaViolations: 无规则时跳过</li>
 *   <li>scanSlaViolations: 有违约时触发告警</li>
 *   <li>scanSlaViolations: 无违约时不触发告警</li>
 *   <li>scanSlaViolations: 单条规则异常不影响其他规则</li>
 *   <li>scan: 扫描异常被外层捕获</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SlaScanner SLA 违约扫描测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlaScannerTest {

    @Mock
    private JobSlaMapper jobSlaMapper;
    @Mock
    private JobSlaService jobSlaService;
    @Mock
    private AlertTrigger alertTrigger;
    @Mock
    private LeaderElector leaderElector;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private SlaScanner scanner;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties
        java.lang.reflect.Field f = SlaScanner.class.getDeclaredField("cronjobProperties");
        f.setAccessible(true);
        f.set(scanner, cronjobProperties);
        scanner.init();
        // 默认为 Leader
        lenient().when(leaderElector.isLeader(anyString())).thenReturn(true);
        // 默认无规则
        lenient().when(jobSlaMapper.selectAllEnabled()).thenReturn(Collections.emptyList());
    }

    // ==================== scan 入口测试 ====================

    @Test
    @DisplayName("scan: leader.enabled=false 时跳过")
    void scan_leaderDisabled_skip() {
        cronjobProperties.getLeader().setEnabled(false);

        scanner.scan();

        verify(jobSlaMapper, never()).selectAllEnabled();
        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scan: 非 Leader 时跳过")
    void scan_notLeader_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        when(leaderElector.isLeader(anyString())).thenReturn(false);

        scanner.scan();

        verify(jobSlaMapper, never()).selectAllEnabled();
        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scan: 无规则时跳过")
    void scan_noRules_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        when(jobSlaMapper.selectAllEnabled()).thenReturn(Collections.emptyList());

        scanner.scan();

        verify(jobSlaService, never()).checkViolation(anyString());
        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scan: 查询异常被外层捕获，不抛出")
    void scan_queryException_swallowed() {
        cronjobProperties.getLeader().setEnabled(true);
        when(jobSlaMapper.selectAllEnabled()).thenThrow(new RuntimeException("DB err"));

        scanner.scan(); // 不应抛异常

        verify(alertTrigger, never()).trigger(any());
    }

    // ==================== scanSlaViolations 测试 ====================

    @Test
    @DisplayName("scanSlaViolations: 无违约时不触发告警")
    void scanSlaViolations_noViolation_skip() {
        JobSlaDO sla = buildSla("sla-1", "job-1", "key-1");
        when(jobSlaMapper.selectAllEnabled()).thenReturn(List.of(sla));
        when(jobSlaService.checkViolation("job-1")).thenReturn(Collections.emptyList());

        scanner.scanSlaViolations();

        verify(alertTrigger, never()).trigger(any());
    }

    @Test
    @DisplayName("scanSlaViolations: 有违约时触发告警")
    void scanSlaViolations_hasViolation_trigger() {
        JobSlaDO sla = buildSla("sla-1", "job-1", "key-1");
        when(jobSlaMapper.selectAllEnabled()).thenReturn(List.of(sla));
        JobSlaService.SlaViolation violation = new JobSlaService.SlaViolation(
                "sla-1", "job-1", "key-1", "FAIL_RATE", "50.00", "30", "WARNING");
        when(jobSlaService.checkViolation("job-1")).thenReturn(List.of(violation));

        scanner.scanSlaViolations();

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    @Test
    @DisplayName("scanSlaViolations: 多项违约时每项都触发告警")
    void scanSlaViolations_multipleViolations_triggerAll() {
        JobSlaDO sla = buildSla("sla-1", "job-1", "key-1");
        when(jobSlaMapper.selectAllEnabled()).thenReturn(List.of(sla));
        JobSlaService.SlaViolation v1 = new JobSlaService.SlaViolation(
                "sla-1", "job-1", "key-1", "MAX_DURATION", "5000", "3000", "WARNING");
        JobSlaService.SlaViolation v2 = new JobSlaService.SlaViolation(
                "sla-1", "job-1", "key-1", "FAIL_RATE", "50.00", "30", "WARNING");
        when(jobSlaService.checkViolation("job-1")).thenReturn(List.of(v1, v2));

        scanner.scanSlaViolations();

        verify(alertTrigger, times(2)).trigger(any(AlertContext.class));
    }

    @Test
    @DisplayName("scanSlaViolations: 单条规则异常不影响其他规则")
    void scanSlaViolations_singleRuleException_continuesOthers() {
        JobSlaDO sla1 = buildSla("sla-1", "job-1", "key-1");
        JobSlaDO sla2 = buildSla("sla-2", "job-2", "key-2");
        when(jobSlaMapper.selectAllEnabled()).thenReturn(List.of(sla1, sla2));
        // sla1 检查抛异常，sla2 有违约
        when(jobSlaService.checkViolation("job-1")).thenThrow(new RuntimeException("DB err"));
        JobSlaService.SlaViolation violation = new JobSlaService.SlaViolation(
                "sla-2", "job-2", "key-2", "FAIL_RATE", "50.00", "30", "WARNING");
        when(jobSlaService.checkViolation("job-2")).thenReturn(List.of(violation));

        scanner.scanSlaViolations(); // 不应抛异常

        verify(alertTrigger, times(1)).trigger(any(AlertContext.class));
    }

    // ==================== 辅助方法 ====================

    private JobSlaDO buildSla(String id, String jobId, String jobKey) {
        JobSlaDO sla = new JobSlaDO();
        sla.setId(id);
        sla.setJobId(jobId);
        sla.setJobKey(jobKey);
        sla.setAlertLevel("WARNING");
        sla.setEnabled(1);
        return sla;
    }
}
