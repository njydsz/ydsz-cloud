package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.enums.UtilizationGrade;
import com.njydsz.pmis.execution.mapper.BillableUtilizationSnapshotMapper;
import com.njydsz.pmis.execution.mapper.RateInternalMapper;
import com.njydsz.pmis.execution.mapper.TimeEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 可计费利用率服务测试
 */
@DisplayName("BillableUtilizationServiceImpl 可计费利用率")
class BillableUtilizationServiceImplTest {

    private TimeEntryMapper mapper;
    private BillableUtilizationSnapshotMapper snapshotMapper;
    private RateInternalMapper rateInternalMapper;
    private BillableUtilizationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(TimeEntryMapper.class);
        snapshotMapper = mock(BillableUtilizationSnapshotMapper.class);
        rateInternalMapper = mock(RateInternalMapper.class);
        service = new BillableUtilizationServiceImpl(mapper, snapshotMapper, rateInternalMapper);
    }

    @Test
    @DisplayName("evaluate 优秀：billable=170 / total=200 = 85%")
    void evaluate_excellent() {
        Map<String, Object> r = service.evaluate(200d, 170d);
        assertThat(r.get("utilizationPctDisplay").toString()).startsWith("85");
        assertThat(r.get("grade")).isEqualTo("EXCELLENT");
        assertThat(r.get("alert")).isEqualTo(false);
    }

    @Test
    @DisplayName("evaluate 良好：80%")
    void evaluate_good() {
        Map<String, Object> r = service.evaluate(100d, 80d);
        assertThat(r.get("grade")).isEqualTo("GOOD");
        assertThat(r.get("alert")).isEqualTo(false);
    }

    @Test
    @DisplayName("evaluate 合格：60%")
    void evaluate_normal() {
        Map<String, Object> r = service.evaluate(100d, 60d);
        assertThat(r.get("grade")).isEqualTo("NORMAL");
        assertThat(r.get("alert")).isEqualTo(false);
    }

    @Test
    @DisplayName("evaluate 黄色预警：40%")
    void evaluate_warn() {
        Map<String, Object> r = service.evaluate(100d, 40d);
        assertThat(r.get("grade")).isEqualTo("WARN");
        assertThat(r.get("alert")).isEqualTo(true);
    }

    @Test
    @DisplayName("evaluate 红色预警：20%")
    void evaluate_critical() {
        Map<String, Object> r = service.evaluate(100d, 20d);
        assertThat(r.get("grade")).isEqualTo("CRITICAL");
        assertThat(r.get("alert")).isEqualTo(true);
    }

    @Test
    @DisplayName("evaluate total=0 返回 0% / CRITICAL")
    void evaluate_zeroTotal() {
        Map<String, Object> r = service.evaluate(0d, 0d);
        assertThat(r.get("utilizationPctDisplay").toString()).isEqualTo("0.00");
        assertThat(r.get("grade")).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("evaluate billable > total 钳制为 100% / EXCELLENT")
    void evaluate_overflow() {
        Map<String, Object> r = service.evaluate(100d, 150d);
        assertThat(r.get("utilizationPctDisplay").toString()).isEqualTo("100.00");
        assertThat(r.get("grade")).isEqualTo("EXCELLENT");
    }

    @Test
    @DisplayName("personal 员工 ID 必填")
    void personal_nullEmployee() {
        assertThatThrownBy(() -> service.personal(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("personal 正常返回")
    void personal_ok() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("total_hours", 160d);
        raw.put("billable_hours", 120d);
        raw.put("overtime_hours", 10d);
        raw.put("leave_hours", 0d);
        raw.put("training_hours", 0d);
        when(mapper.aggregateBillableOne(ArgumentMatchers.eq(7L),
                ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(raw);
        Map<String, Object> r = service.personal(7L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        assertThat(r.get("employeeId")).isEqualTo(7L);
        assertThat(r.get("grade")).isEqualTo("GOOD");
    }

    @Test
    @DisplayName("aggregate 校验 from <= to")
    void aggregate_badRange() {
        assertThatThrownBy(() -> service.aggregate(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("aggregate 正常返回并 enrich")
    void aggregate_ok() {
        Map<String, Object> row = new HashMap<>();
        row.put("employee_id", 1L);
        row.put("employee_name", "张三");
        row.put("level_code", "L8");
        row.put("period", "2026-05");
        row.put("total_hours", 176d);
        row.put("billable_hours", 140d);
        row.put("overtime_hours", 0d);
        row.put("leave_hours", 0d);
        row.put("training_hours", 0d);
        when(mapper.aggregateBillableByEmployee(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(List.of(row));
        List<Map<String, Object>> out = service.aggregate(null, null);
        assertThat(out).hasSize(1);
        Map<String, Object> o = out.get(0);
        assertThat(o.get("grade")).isEqualTo("GOOD");
        assertThat(o.get("alert")).isEqualTo(false);
    }

    @Test
    @DisplayName("rank 跨月合并并按利用率倒序")
    void rank_ok() {
        Map<String, Object> may = new HashMap<>();
        may.put("employee_id", 1L);
        may.put("employee_name", "张三");
        may.put("level_code", "L8");
        may.put("period", "2026-05");
        may.put("total_hours", 176d);
        may.put("billable_hours", 100d);
        may.put("overtime_hours", 0d);
        may.put("leave_hours", 0d);
        may.put("training_hours", 0d);
        Map<String, Object> jun = new HashMap<>();
        jun.put("employee_id", 1L);
        jun.put("employee_name", "张三");
        jun.put("level_code", "L8");
        jun.put("period", "2026-06");
        jun.put("total_hours", 160d);
        jun.put("billable_hours", 140d);
        jun.put("overtime_hours", 0d);
        jun.put("leave_hours", 0d);
        jun.put("training_hours", 0d);
        Map<String, Object> other = new HashMap<>();
        other.put("employee_id", 2L);
        other.put("employee_name", "李四");
        other.put("level_code", "L5");
        other.put("period", "2026-05");
        other.put("total_hours", 176d);
        other.put("billable_hours", 170d);
        other.put("overtime_hours", 0d);
        other.put("leave_hours", 0d);
        other.put("training_hours", 0d);
        when(mapper.aggregateBillableByEmployee(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(List.of(may, jun, other));
        List<Map<String, Object>> out = service.rank(null, null, 10);
        // 张三合并 total=336 billable=240 => 71.43% GOOD
        // 李四 total=176 billable=170 => 96.59% EXCELLENT (排第一)
        assertThat(out.get(0).get("grade")).isEqualTo("EXCELLENT");
        assertThat(out.get(1).get("grade")).isEqualTo("GOOD");
    }

    @Test
    @DisplayName("scanAlerts 只返回 WARN/CRITICAL")
    void scanAlerts_onlyAlerts() {
        Map<String, Object> good = new HashMap<>();
        good.put("employee_id", 1L);
        good.put("employee_name", "甲");
        good.put("level_code", "L8");
        good.put("period", "2026-05");
        good.put("total_hours", 176d);
        good.put("billable_hours", 140d);
        good.put("overtime_hours", 0d);
        good.put("leave_hours", 0d);
        good.put("training_hours", 0d);
        Map<String, Object> bad = new HashMap<>();
        bad.put("employee_id", 2L);
        bad.put("employee_name", "乙");
        bad.put("level_code", "L5");
        bad.put("period", "2026-05");
        bad.put("total_hours", 176d);
        bad.put("billable_hours", 30d);
        bad.put("overtime_hours", 0d);
        bad.put("leave_hours", 0d);
        bad.put("training_hours", 0d);
        when(mapper.aggregateBillableByEmployee(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(List.of(good, bad));
        List<Map<String, Object>> out = service.scanAlerts(null, null);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).get("grade")).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("overall 汇总团队均值")
    void overall_ok() {
        Map<String, Object> a = new HashMap<>();
        a.put("employee_id", 1L);
        a.put("employee_name", "a");
        a.put("level_code", "L8");
        a.put("period", "2026-05");
        a.put("total_hours", 100d);
        a.put("billable_hours", 80d);
        a.put("overtime_hours", 0d);
        a.put("leave_hours", 0d);
        a.put("training_hours", 0d);
        Map<String, Object> b = new HashMap<>();
        b.put("employee_id", 2L);
        b.put("employee_name", "b");
        b.put("level_code", "L5");
        b.put("period", "2026-05");
        b.put("total_hours", 200d);
        b.put("billable_hours", 100d);
        b.put("overtime_hours", 0d);
        b.put("leave_hours", 0d);
        b.put("training_hours", 0d);
        when(mapper.aggregateBillableByEmployee(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(List.of(a, b));
        Map<String, Object> out = service.overall(null, null);
        assertThat(((Number) out.get("totalHours")).doubleValue()).isEqualTo(300d);
        assertThat(((Number) out.get("billableHours")).doubleValue()).isEqualTo(180d);
        assertThat(out.get("grade")).isEqualTo("NORMAL");
        assertThat(out.get("employeeCount")).isEqualTo(2L);
    }

    @Test
    @DisplayName("aggregate mapper 抛异常时返回空列表")
    void aggregate_dbDown() {
        when(mapper.aggregateBillableByEmployee(ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("DB down"));
        List<Map<String, Object>> out = service.aggregate(null, null);
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("personal mapper 抛异常时 enrich 后返回空记录")
    void personal_dbDown() {
        when(mapper.aggregateBillableOne(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("DB down"));
        Map<String, Object> r = service.personal(1L, null, null);
        assertThat(r.get("grade")).isEqualTo("CRITICAL");
        assertThat(r.get("employeeId")).isEqualTo(1L);
    }

    @Test
    @DisplayName("UtilizationGrade 边界值")
    void grade_boundaries() {
        assertThat(UtilizationGrade.of(85.0)).isEqualTo(UtilizationGrade.EXCELLENT);
        assertThat(UtilizationGrade.of(84.99)).isEqualTo(UtilizationGrade.GOOD);
        assertThat(UtilizationGrade.of(70.0)).isEqualTo(UtilizationGrade.GOOD);
        assertThat(UtilizationGrade.of(69.99)).isEqualTo(UtilizationGrade.NORMAL);
        assertThat(UtilizationGrade.of(50.0)).isEqualTo(UtilizationGrade.NORMAL);
        assertThat(UtilizationGrade.of(49.99)).isEqualTo(UtilizationGrade.WARN);
        assertThat(UtilizationGrade.of(30.0)).isEqualTo(UtilizationGrade.WARN);
        assertThat(UtilizationGrade.of(29.99)).isEqualTo(UtilizationGrade.CRITICAL);
        assertThat(UtilizationGrade.of(Double.NaN)).isEqualTo(UtilizationGrade.CRITICAL);
        assertThat(UtilizationGrade.fromCode("EXCELLENT")).isEqualTo(UtilizationGrade.EXCELLENT);
        assertThat(UtilizationGrade.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("recompute 正常路径：聚合 + UPSERT 快照")
    void recompute_ok() {
        Map<String, Object> row = new HashMap<>();
        row.put("employee_id", 1L);
        row.put("employee_name", "张三");
        row.put("level_code", "L8");
        row.put("period", "2026-06");
        row.put("total_hours", 176d);
        row.put("billable_hours", 140d);
        row.put("overtime_hours", 0d);
        row.put("leave_hours", 0d);
        row.put("training_hours", 0d);
        when(mapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(row));
        when(snapshotMapper.upsert(any())).thenReturn(1);
        when(rateInternalMapper.selectAll()).thenReturn(List.of());

        Map<String, Object> r = service.recompute("2026-06", false);
        assertThat(r.get("ok")).isEqualTo(true);
        assertThat(r.get("period")).isEqualTo("2026-06");
        assertThat(r.get("recomputeAll")).isEqualTo(false);
        assertThat(r.get("affectedCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("recompute recomputeAll=true 先软删再重算")
    void recompute_recomputeAll() {
        Map<String, Object> row = new HashMap<>();
        row.put("employee_id", 1L);
        row.put("employee_name", "张三");
        row.put("level_code", "L5");
        row.put("period", "2026-05");
        row.put("total_hours", 100d);
        row.put("billable_hours", 80d);
        when(mapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(row));
        when(snapshotMapper.deleteByPeriod("2026-05")).thenReturn(3);
        when(snapshotMapper.upsert(any())).thenReturn(1);
        when(rateInternalMapper.selectAll()).thenReturn(List.of());

        Map<String, Object> r = service.recompute("2026-05", true);
        assertThat(r.get("affectedCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("recompute 默认上一月")
    void recompute_defaultPeriod() {
        when(mapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of());
        when(rateInternalMapper.selectAll()).thenReturn(List.of());
        Map<String, Object> r = service.recompute(null, false);
        assertThat(r.get("ok")).isEqualTo(true);
        assertThat(r.get("period")).isNotNull();
    }

    @Test
    @DisplayName("recompute period 格式错误抛异常")
    void recompute_badPeriod() {
        assertThatThrownBy(() -> service.recompute("2026/06", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("recompute 写入失败时单条容错 不影响整体")
    void recompute_partialFailure() {
        Map<String, Object> good = new HashMap<>();
        good.put("employee_id", 1L);
        good.put("employee_name", "good");
        good.put("level_code", "L5");
        good.put("total_hours", 100d);
        good.put("billable_hours", 80d);
        Map<String, Object> bad = new HashMap<>();
        bad.put("employee_id", null); // 缺 employee_id
        bad.put("employee_name", "bad");
        when(mapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(good, bad));
        when(snapshotMapper.upsert(any())).thenReturn(1);
        when(rateInternalMapper.selectAll()).thenReturn(List.of());

        Map<String, Object> r = service.recompute("2026-04", false);
        assertThat(r.get("affectedCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("snapshotAverage 优先读快照表")
    void snapshotAverage_fromSnapshot() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("avg_pct", 0.82d);
        snap.put("headcount", 10L);
        when(snapshotMapper.averageByPeriod("2026-06")).thenReturn(snap);
        Map<String, Object> r = service.snapshotAverage("2026-06");
        assertThat(r.get("source")).isEqualTo("SNAPSHOT");
        assertThat(((Number) r.get("avg_pct")).doubleValue()).isEqualTo(0.82d);
    }

    @Test
    @DisplayName("snapshotAverage 快照为空时实时聚合兜底")
    void snapshotAverage_fallback() {
        when(snapshotMapper.averageByPeriod("2026-06")).thenReturn(new HashMap<>());
        Map<String, Object> row = new HashMap<>();
        row.put("employee_id", 1L);
        row.put("employee_name", "a");
        row.put("level_code", "L5");
        row.put("total_hours", 100d);
        row.put("billable_hours", 60d);
        when(mapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(row));

        Map<String, Object> r = service.snapshotAverage("2026-06");
        assertThat(r.get("source")).isEqualTo("REALTIME");
        assertThat(r.get("headcount")).isEqualTo(1L);
        assertThat(((Number) r.get("avg_pct")).doubleValue()).isEqualTo(0.6d);
    }

    @Test
    @DisplayName("snapshotAverage 默认当前月")
    void snapshotAverage_defaultPeriod() {
        when(snapshotMapper.averageByPeriod(any())).thenReturn(new HashMap<>());
        when(mapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of());
        Map<String, Object> r = service.snapshotAverage(null);
        assertThat(r.get("period")).isNotNull();
    }
}
