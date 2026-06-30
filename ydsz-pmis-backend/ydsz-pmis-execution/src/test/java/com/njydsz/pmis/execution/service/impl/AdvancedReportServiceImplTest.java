package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.config.ThresholdProvider;
import com.njydsz.pmis.execution.entity.EvmMeasureDO;
import com.njydsz.pmis.execution.entity.RateCardDO;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import com.njydsz.pmis.execution.entity.RiskDO;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.RateCardMapper;
import com.njydsz.pmis.execution.mapper.RateInternalMapper;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import com.njydsz.pmis.execution.mapper.TimeEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdvancedReportServiceImpl 测试
 */
@DisplayName("AdvancedReportServiceImpl 高级报表")
class AdvancedReportServiceImplTest {

    private EvmMeasureMapper evmMapper;
    private RateCardMapper rateCardMapper;
    private RateInternalMapper rateInternalMapper;
    private RiskMapper riskMapper;
    private TimeEntryMapper timeEntryMapper;
    private ThresholdProvider thresholdProvider;
    private AdvancedReportServiceImpl service;

    @BeforeEach
    void setUp() {
        evmMapper = mock(EvmMeasureMapper.class);
        rateCardMapper = mock(RateCardMapper.class);
        rateInternalMapper = mock(RateInternalMapper.class);
        riskMapper = mock(RiskMapper.class);
        timeEntryMapper = mock(TimeEntryMapper.class);
        thresholdProvider = mock(ThresholdProvider.class);
        when(thresholdProvider.benchYellowDays()).thenReturn(7);
        when(thresholdProvider.benchRedDays()).thenReturn(15);
        service = new AdvancedReportServiceImpl(evmMapper, rateCardMapper,
                rateInternalMapper, riskMapper, timeEntryMapper, thresholdProvider);
    }

    @Test
    @DisplayName("evmReport initiationId 为空返回空列表")
    void evmReport_null() {
        assertThat(service.evmReport(null)).isEmpty();
    }

    @Test
    @DisplayName("evmReport 正常返回全部字段")
    void evmReport_normal() {
        EvmMeasureDO m = new EvmMeasureDO();
        m.setPeriod("2026-01");
        m.setPv(new BigDecimal("100"));
        m.setEv(new BigDecimal("80"));
        m.setAc(new BigDecimal("90"));
        m.setBac(new BigDecimal("1000"));
        m.setCpi(new BigDecimal("0.89"));
        m.setSpi(new BigDecimal("0.80"));
        m.setAlertLevel("YELLOW");
        m.setAlertReason("成本偏差");
        when(evmMapper.selectByInitiation(1L)).thenReturn(List.of(m));

        List<Map<String, Object>> out = service.evmReport(1L);
        assertThat(out).hasSize(1);
        Map<String, Object> row = out.get(0);
        assertThat(row.get("period")).isEqualTo("2026-01");
        assertThat(row.get("alertLevel")).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("utilizationRank 兼容旧版（默认近 3 个月）")
    void utilizationRank_default() {
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of());
        assertThat(service.utilizationRank(0)).isEmpty();
    }

    @Test
    @DisplayName("utilizationRank 基于工时计算可计费利用率")
    void utilizationRank_byTimeEntry() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-04",
                "160", "120", "0", "16", "0"); // working=144, util=120/144=83.33%
        Map<String, Object> r2 = rowOf(2L, "李四", "L8", "2026-04",
                "176", "168", "0", "8", "0"); // working=168, util=168/168=100%
        Map<String, Object> r3 = rowOf(3L, "王五", "L8", "2026-04",
                "168", "84", "0", "0", "0");  // working=168, util=84/168=50%
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any()))
                .thenReturn(List.of(r1, r2, r3));

        RateInternalDO l5 = internal("L5", "1000");
        RateInternalDO l8 = internal("L8", "1500");
        when(rateInternalMapper.selectAll()).thenReturn(List.of(l5, l8));

        List<Map<String, Object>> out = service.utilizationRank(10,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null);
        assertThat(out).hasSize(3);
        // 100% 排在最前 (李四)
        assertThat(out.get(0).get("employeeName")).isEqualTo("李四");
        assertThat((BigDecimal) out.get(0).get("utilizationPct"))
                .isEqualByComparingTo(new BigDecimal("100.00"));
        // 50% 排第三
        assertThat(((BigDecimal) out.get(2).get("utilizationPct"))
                .compareTo(new BigDecimal("50.00"))).isEqualTo(0);
    }

    @Test
    @DisplayName("utilizationRank 部门过滤（无 Feign 数据时不过滤）")
    void utilizationRank_deptFilter_noop() {
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of());
        assertThat(service.utilizationRank(5, LocalDate.now(), LocalDate.now(), "云数一部")).isEmpty();
    }

    @Test
    @DisplayName("utilizationRank mapper 异常时降级为空")
    void utilizationRank_exception() {
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any()))
                .thenThrow(new RuntimeException());
        assertThat(service.utilizationRank(10)).isEmpty();
    }

    @Test
    @DisplayName("utilizationOf 计算单员工利用率")
    void utilizationOf_normal() {
        Map<String, Object> agg = new HashMap<>();
        agg.put("total_hours", new BigDecimal("176"));
        agg.put("billable_hours", new BigDecimal("160"));
        agg.put("overtime_hours", new BigDecimal("10"));
        agg.put("leave_hours", new BigDecimal("8"));
        agg.put("training_hours", new BigDecimal("8"));
        when(timeEntryMapper.aggregateBillableOne(any(), any(), any())).thenReturn(agg);

        Map<String, Object> out = service.utilizationOf(1L,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertThat(out.get("employeeId")).isEqualTo(1L);
        assertThat((BigDecimal) out.get("utilizationPct"))
                .isEqualByComparingTo(new BigDecimal("95.24"));
    }

    @Test
    @DisplayName("utilizationOf null employee 返回空 map")
    void utilizationOf_null() {
        assertThat(service.utilizationOf(null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("utilizationByDepartment 按部门聚合")
    void utilizationByDepartment() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-04",
                "160", "120", "0", "16", "0");
        Map<String, Object> r2 = rowOf(2L, "李四", "L5", "2026-04",
                "176", "176", "0", "8", "0");
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any()))
                .thenReturn(List.of(r1, r2));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internalWithDept("L5", "1000", "云数一部")));

        List<Map<String, Object>> out = service.utilizationByDepartment(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).get("department")).isEqualTo("云数一部");
        assertThat(out.get(0).get("headcount")).isEqualTo(2L);
    }

    @Test
    @DisplayName("utilizationByDepartment 多个部门")
    void utilizationByDepartment_multi() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-04", "160", "100", "0", "0", "0");
        Map<String, Object> r2 = rowOf(2L, "李四", "L8", "2026-04", "176", "160", "0", "0", "0");
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any()))
                .thenReturn(List.of(r1, r2));
        when(rateInternalMapper.selectAll())
                .thenReturn(List.of(internalWithDept("L5", "1000", "云数一部"),
                        internalWithDept("L8", "1500", "云数二部")));

        List<Map<String, Object>> out = service.utilizationByDepartment(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        assertThat(out).hasSize(2);
        assertThat(out.get(0).get("department")).isEqualTo("云数二部");
    }

    @Test
    @DisplayName("benchCostReport 兼容旧版")
    void bench_default() {
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of());
        assertThat(service.benchCostReport()).isEmpty();
    }

    @Test
    @DisplayName("benchCostReport 计算闲置工时与成本")
    void bench_normal() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-06",
                "176", "88", "0", "8", "16"); // billable=88, leave=8, training=16, bench=64
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(r1));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internal("L5", "1000")));

        List<Map<String, Object>> out = service.benchCostReport(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(out).hasSize(1);
        Map<String, Object> row = out.get(0);
        // bench=176-88-8-16=64h = 8天
        assertThat((BigDecimal) row.get("benchHours")).isEqualByComparingTo(new BigDecimal("64"));
        assertThat((BigDecimal) row.get("benchDays")).isEqualByComparingTo(new BigDecimal("8.00"));
        // 8 days * 1000 = 8000
        assertThat((BigDecimal) row.get("benchCost")).isEqualByComparingTo(new BigDecimal("8000.00"));
        // 64 / (64+88) = 42.11%
        assertThat(row.get("alertLevel")).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("benchCostReport RED 告警")
    void bench_red() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-06",
                "200", "0", "0", "0", "0"); // 200h bench = 25 天
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(r1));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internal("L5", "1000")));

        List<Map<String, Object>> out = service.benchCostReport(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(out.get(0).get("alertLevel")).isEqualTo("RED");
    }

    @Test
    @DisplayName("benchCostReport GREEN 告警")
    void bench_green() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-06",
                "176", "160", "0", "0", "0"); // bench=16h = 2天
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(r1));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internal("L5", "1000")));

        List<Map<String, Object>> out = service.benchCostReport(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(out.get(0).get("alertLevel")).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("benchCostReport 闲置=0 时不返回该员工")
    void bench_zero() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-06",
                "176", "176", "0", "0", "0"); // 100% billable
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(r1));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internal("L5", "1000")));

        List<Map<String, Object>> out = service.benchCostReport(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("benchCostReport mapper 异常降级为空")
    void bench_exception() {
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any()))
                .thenThrow(new RuntimeException());
        assertThat(service.benchCostReport()).isEmpty();
    }

    @Test
    @DisplayName("dualRateProfitCompare 计算差额和毛利率")
    void dualRate() {
        RateCardDO card1 = new RateCardDO();
        card1.setLevelCode("L5");
        card1.setRateAmount(new BigDecimal("1500.00"));
        RateCardDO card2 = new RateCardDO();
        card2.setLevelCode("L8");
        card2.setRateAmount(new BigDecimal("2500.00"));
        RateInternalDO internal1 = new RateInternalDO();
        internal1.setLevelCode("L5");
        internal1.setCostAmount(new BigDecimal("1000.00"));
        RateInternalDO internal2 = new RateInternalDO();
        internal2.setLevelCode("L8");
        internal2.setCostAmount(new BigDecimal("1500.00"));
        when(rateCardMapper.selectAll()).thenReturn(List.of(card1, card2));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internal1, internal2));

        List<Map<String, Object>> out = service.dualRateProfitCompare("2026-01");
        assertThat(out).hasSize(2);
        Map<String, Object> l5 = out.stream()
                .filter(r -> "L5".equals(r.get("levelCode"))).findFirst().orElseThrow();
        BigDecimal l5Diff = (BigDecimal) l5.get("diff");
        BigDecimal l5Margin = (BigDecimal) l5.get("margin");
        assertThat(l5Diff).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(l5Margin).isEqualByComparingTo(new BigDecimal("0.3333"));
    }

    @Test
    @DisplayName("dualRateProfitCompare 内部成本缺失时 diff 等于 externalRate")
    void dualRate_missingInternal() {
        RateCardDO card = new RateCardDO();
        card.setLevelCode("L10");
        card.setRateAmount(new BigDecimal("3000.00"));
        when(rateCardMapper.selectAll()).thenReturn(List.of(card));
        when(rateInternalMapper.selectAll()).thenReturn(List.of());

        List<Map<String, Object>> out = service.dualRateProfitCompare(null);
        assertThat(out).hasSize(1);
        assertThat((BigDecimal) out.get(0).get("diff")).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat((BigDecimal) out.get(0).get("internalCost")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("dualRateProfitCompare 外部为 0 时 margin 降级为 0")
    void dualRate_zeroExternal() {
        RateCardDO card = new RateCardDO();
        card.setLevelCode("L1");
        card.setRateAmount(BigDecimal.ZERO);
        when(rateCardMapper.selectAll()).thenReturn(List.of(card));
        when(rateInternalMapper.selectAll()).thenReturn(List.of());

        List<Map<String, Object>> out = service.dualRateProfitCompare(null);
        assertThat((BigDecimal) out.get(0).get("margin")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("resourceGantt 当前占位返回空")
    void gantt() {
        assertThat(service.resourceGantt(1L)).isEmpty();
    }

    @Test
    @DisplayName("riskDashboard 按 level + initiation 双维度聚合")
    void riskDashboard() {
        RiskDO r1 = new RiskDO();
        r1.setRiskLevel("HIGH");
        r1.setInitiationId(10L);
        RiskDO r2 = new RiskDO();
        r2.setRiskLevel("HIGH");
        r2.setInitiationId(20L);
        RiskDO r3 = new RiskDO();
        r3.setRiskLevel("MEDIUM");
        r3.setInitiationId(20L);
        when(riskMapper.selectAll()).thenReturn(List.of(r1, r2, r3));

        List<Map<String, Object>> out = service.riskDashboard();
        // 2 BY_LEVEL (HIGH=2, MEDIUM=1) + 2 BY_INITIATION (10=1, 20=2) = 4
        assertThat(out).hasSize(4);
    }

    @Test
    @DisplayName("riskDashboard mapper 异常降级为空")
    void riskDashboard_exception() {
        when(riskMapper.selectAll()).thenThrow(new RuntimeException());
        assertThat(service.riskDashboard()).isEmpty();
    }

    @Test
    @DisplayName("riskDashboard 空数据返回空")
    void riskDashboard_empty() {
        when(riskMapper.selectAll()).thenReturn(List.of());
        assertThat(service.riskDashboard()).isEmpty();
    }

    // ----------------- helpers -----------------

    private Map<String, Object> rowOf(Long empId, String name, String level, String period,
                                       String total, String billable, String overtime,
                                       String leave, String training) {
        Map<String, Object> row = new HashMap<>();
        row.put("employee_id", empId);
        row.put("employee_name", name);
        row.put("level_code", level);
        row.put("period", period);
        row.put("total_hours", new BigDecimal(total));
        row.put("billable_hours", new BigDecimal(billable));
        row.put("overtime_hours", new BigDecimal(overtime));
        row.put("leave_hours", new BigDecimal(leave));
        row.put("training_hours", new BigDecimal(training));
        return row;
    }

    private RateInternalDO internal(String level, String cost) {
        RateInternalDO r = new RateInternalDO();
        r.setLevelCode(level);
        r.setCostAmount(new BigDecimal(cost));
        r.setDepartmentName(level);
        return r;
    }

    private RateInternalDO internalWithDept(String level, String cost, String dept) {
        RateInternalDO r = new RateInternalDO();
        r.setLevelCode(level);
        r.setCostAmount(new BigDecimal(cost));
        r.setDepartmentName(dept);
        return r;
    }
}
