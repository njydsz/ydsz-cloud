package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.literule.api.RuleExecutionTrace;
import com.njydsz.pmis.project.entity.RuleExecutionTraceDO;
import com.njydsz.pmis.project.mapper.RuleExecutionTraceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DbTraceRecorder 单元测试
 *
 * <p>覆盖 record/recordBatch 写入转换、getByTraceId/getByRuleCode/getRecentTraces 查询转换、
 * 异常容错、空入参处理等场景。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("规则执行轨迹持久化实现测试")
class DbTraceRecorderTest {

    @Mock
    private RuleExecutionTraceMapper ruleExecutionTraceMapper;

    @InjectMocks
    private DbTraceRecorder recorder;

    // ---------- record 单条写入 ----------

    @Test
    @DisplayName("record 应正确转换并插入 DO")
    void recordShouldInsertConvertedDO() {
        RuleExecutionTrace trace = buildTrace("T-001", "R_001", "金额规则", "RISK",
                true, "RED", "amount > 1000", 15L);

        recorder.record(trace);

        ArgumentCaptor<RuleExecutionTraceDO> captor = ArgumentCaptor.forClass(RuleExecutionTraceDO.class);
        verify(ruleExecutionTraceMapper).insert(captor.capture());
        RuleExecutionTraceDO d = captor.getValue();
        assertThat(d.getTraceId()).isEqualTo("T-001");
        assertThat(d.getRuleCode()).isEqualTo("R_001");
        assertThat(d.getRuleName()).isEqualTo("金额规则");
        assertThat(d.getScenario()).isEqualTo("RISK");
        assertThat(d.getTriggered()).isTrue();
        assertThat(d.getSeverity()).isEqualTo("RED");
        assertThat(d.getConditionResult()).isEqualTo("amount > 1000");
        assertThat(d.getElapsedMs()).isEqualTo(15L);
        assertThat(d.getFactsSnapshot()).containsEntry("amount", 2000);
        assertThat(d.getResultSnapshot()).containsEntry("severity", "RED");
        assertThat(d.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("record 传入 null 应直接返回不调用 mapper")
    void recordNullShouldDoNothing() {
        recorder.record(null);
        verifyNoInteractions(ruleExecutionTraceMapper);
    }

    @Test
    @DisplayName("record 写入异常应被吞掉并打日志（不抛出）")
    void recordShouldSwallowException() {
        RuleExecutionTrace trace = buildTrace("T-002", "R_002", "测试", "RISK",
                false, "INFO", "1 > 0", 1L);
        when(ruleExecutionTraceMapper.insert(any(RuleExecutionTraceDO.class)))
                .thenThrow(new RuntimeException("模拟 DB 异常"));

        recorder.record(trace); // 不应抛出异常

        verify(ruleExecutionTraceMapper).insert(any(RuleExecutionTraceDO.class));
    }

    // ---------- recordBatch 批量写入 ----------

    @Test
    @DisplayName("recordBatch 应逐条插入")
    void recordBatchShouldInsertEach() {
        RuleExecutionTrace t1 = buildTrace("T-003", "R_001", "规则A", "RISK",
                true, "RED", "a > 1", 5L);
        RuleExecutionTrace t2 = buildTrace("T-003", "R_002", "规则B", "RISK",
                false, "INFO", "b < 1", 3L);

        recorder.recordBatch(List.of(t1, t2));

        verify(ruleExecutionTraceMapper, times(2)).insert(any(RuleExecutionTraceDO.class));
    }

    @Test
    @DisplayName("recordBatch 空列表应直接返回")
    void recordBatchEmptyShouldDoNothing() {
        recorder.recordBatch(List.of());
        verifyNoInteractions(ruleExecutionTraceMapper);
    }

    @Test
    @DisplayName("recordBatch null 应直接返回")
    void recordBatchNullShouldDoNothing() {
        recorder.recordBatch(null);
        verifyNoInteractions(ruleExecutionTraceMapper);
    }

    @Test
    @DisplayName("recordBatch 部分失败不应影响其余写入")
    void recordBatchPartialFailureShouldContinue() {
        RuleExecutionTrace t1 = buildTrace("T-004", "R_001", "规则A", "RISK",
                true, "RED", "a > 1", 5L);
        RuleExecutionTrace t2 = buildTrace("T-004", "R_002", "规则B", "RISK",
                false, "INFO", "b < 1", 3L);

        when(ruleExecutionTraceMapper.insert(any(RuleExecutionTraceDO.class)))
                .thenThrow(new RuntimeException("模拟第一条失败"))
                .thenReturn(1); // 第二条成功

        recorder.recordBatch(List.of(t1, t2)); // 不应抛出

        verify(ruleExecutionTraceMapper, times(2)).insert(any(RuleExecutionTraceDO.class));
    }

    // ---------- getByTraceId 查询 ----------

    @Test
    @DisplayName("getByTraceId 应按 createdAt 升序返回转换后的 Trace")
    void getByTraceIdShouldReturnConvertedTraces() {
        RuleExecutionTraceDO d1 = buildDO("T-005", "R_001", "规则A", 1L);
        RuleExecutionTraceDO d2 = buildDO("T-005", "R_002", "规则B", 2L);
        when(ruleExecutionTraceMapper.selectList(any()))
                .thenReturn(List.of(d1, d2));

        List<RuleExecutionTrace> result = recorder.getByTraceId("T-005");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRuleCode()).isEqualTo("R_001");
        assertThat(result.get(1).getRuleCode()).isEqualTo("R_002");
        assertThat(result.get(0).isTriggered()).isTrue();
        assertThat(result.get(0).getElapsedMs()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getByTraceId 无数据应返回空列表")
    void getByTraceIdEmptyShouldReturnEmptyList() {
        when(ruleExecutionTraceMapper.selectList(any()))
                .thenReturn(List.of());

        List<RuleExecutionTrace> result = recorder.getByTraceId("NOT_EXIST");

        assertThat(result).isEmpty();
    }

    // ---------- getByRuleCode 查询 ----------

    @Test
    @DisplayName("getByRuleCode 应按 createdAt 降序返回并限制 limit")
    void getByRuleCodeShouldReturnLimitedTraces() {
        RuleExecutionTraceDO d1 = buildDO("T-006", "R_001", "规则A", 10L);
        when(ruleExecutionTraceMapper.selectList(any()))
                .thenReturn(List.of(d1));

        List<RuleExecutionTrace> result = recorder.getByRuleCode("R_001", 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTraceId()).isEqualTo("T-006");
    }

    // ---------- getRecentTraces 查询 ----------

    @Test
    @DisplayName("getRecentTraces 应按 createdAt 降序返回最近轨迹")
    void getRecentTracesShouldReturnRecentTraces() {
        RuleExecutionTraceDO d1 = buildDO("T-007", "R_001", "规则A", 5L);
        RuleExecutionTraceDO d2 = buildDO("T-008", "R_002", "规则B", 8L);
        when(ruleExecutionTraceMapper.selectList(any()))
                .thenReturn(List.of(d1, d2));

        List<RuleExecutionTrace> result = recorder.getRecentTraces(10);

        assertThat(result).hasSize(2);
    }

    // ---------- DO → Trace 转换边界（null 字段处理） ----------

    @Test
    @DisplayName("DO 中 triggered/elapsedMs 为 null 时应安全转换")
    void toTraceShouldHandleNullFields() {
        RuleExecutionTraceDO d = new RuleExecutionTraceDO();
        d.setTraceId("T-009");
        d.setRuleCode("R_001");
        d.setTriggered(null);
        d.setElapsedMs(null);

        when(ruleExecutionTraceMapper.selectList(any()))
                .thenReturn(List.of(d));

        List<RuleExecutionTrace> result = recorder.getByTraceId("T-009");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isTriggered()).isFalse();
        assertThat(result.get(0).getElapsedMs()).isEqualTo(0L);
    }

    // ---------- 辅助方法 ----------

    private RuleExecutionTrace buildTrace(String traceId, String ruleCode, String ruleName,
                                          String scenario, boolean triggered, String severity,
                                          String conditionResult, long elapsedMs) {
        RuleExecutionTrace t = new RuleExecutionTrace();
        t.setTraceId(traceId);
        t.setRuleCode(ruleCode);
        t.setRuleName(ruleName);
        t.setScenario(scenario);
        t.setTriggered(triggered);
        t.setSeverity(severity);
        t.setConditionResult(conditionResult);
        t.setElapsedMs(elapsedMs);
        t.setFactsSnapshot(Map.of("amount", 2000));
        t.setResultSnapshot(Map.of("severity", severity));
        t.setCreatedAt(LocalDateTime.now());
        return t;
    }

    private RuleExecutionTraceDO buildDO(String traceId, String ruleCode, String ruleName, long elapsedMs) {
        RuleExecutionTraceDO d = new RuleExecutionTraceDO();
        d.setId(1L);
        d.setTraceId(traceId);
        d.setRuleCode(ruleCode);
        d.setRuleName(ruleName);
        d.setScenario("RISK");
        d.setTriggered(true);
        d.setSeverity("RED");
        d.setConditionResult("amount > 1000");
        d.setElapsedMs(elapsedMs);
        d.setFactsSnapshot(Map.of("amount", 2000));
        d.setResultSnapshot(Map.of("severity", "RED"));
        d.setCreatedAt(LocalDateTime.now());
        return d;
    }
}
