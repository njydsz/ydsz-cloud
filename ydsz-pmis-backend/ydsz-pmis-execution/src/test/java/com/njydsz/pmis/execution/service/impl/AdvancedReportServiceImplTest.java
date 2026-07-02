package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.config.ThresholdProvider;
import com.njydsz.pmis.execution.entity.EvmMeasureDO;
import com.njydsz.pmis.execution.entity.ProfitSnapshotDO;
import com.njydsz.pmis.execution.entity.RateCardDO;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import com.njydsz.pmis.execution.entity.RiskDO;
import com.njydsz.pmis.execution.feign.BenchResourceClient;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.execution.mapper.RateCardMapper;
import com.njydsz.pmis.execution.mapper.RateInternalMapper;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import com.njydsz.pmis.execution.mapper.TimeEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
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
    private BenchResourceClient benchResourceClient;
    private ProfitSnapshotMapper profitSnapshotMapper;
    private AdvancedReportServiceImpl service;

    @BeforeEach
    void setUp() {
        evmMapper = mock(EvmMeasureMapper.class);
        rateCardMapper = mock(RateCardMapper.class);
        rateInternalMapper = mock(RateInternalMapper.class);
        riskMapper = mock(RiskMapper.class);
        timeEntryMapper = mock(TimeEntryMapper.class);
        thresholdProvider = mock(ThresholdProvider.class);
        benchResourceClient = mock(BenchResourceClient.class);
        profitSnapshotMapper = mock(ProfitSnapshotMapper.class);
        when(thresholdProvider.benchYellowDays()).thenReturn(7);
        when(thresholdProvider.benchRedDays()).thenReturn(15);
        // 默认 user 服务降级：返回空数据，不让现有测试受 Feign 影响
        when(benchResourceClient.getBenchDashboard()).thenReturn(Result.ok(Map.of(
                "totalIdleCost", BigDecimal.ZERO,
                "activePools", Collections.emptyList(),
                "source", "DOWN")));
        when(benchResourceClient.listResourceAssignmentsByInitiation(any()))
                .thenReturn(Result.ok(Collections.emptyList()));
        service = new AdvancedReportServiceImpl(evmMapper, rateCardMapper,
                rateInternalMapper, riskMapper, timeEntryMapper, thresholdProvider,
                benchResourceClient, profitSnapshotMapper);
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
        // 1 POOL_SUMMARY + 1 员工行
        assertThat(out).hasSize(2);
        // 第一个是 POOL_SUMMARY
        assertThat(out.get(0).get("type")).isEqualTo("POOL_SUMMARY");
        assertThat(out.get(0).get("source")).isEqualTo("LOCAL_AGG");
        Map<String, Object> row = out.get(1);
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
        // 索引 0 = POOL_SUMMARY, 索引 1 = 员工行
        assertThat(out.get(1).get("alertLevel")).isEqualTo("RED");
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
        assertThat(out.get(1).get("alertLevel")).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("benchCostReport 闲置=0 时不返回员工行但仍附加 POOL_SUMMARY")
    void bench_zero() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-06",
                "176", "176", "0", "0", "0"); // 100% billable
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(r1));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internal("L5", "1000")));

        List<Map<String, Object>> out = service.benchCostReport(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        // 仅 POOL_SUMMARY，无员工行
        assertThat(out).hasSize(1);
        assertThat(out.get(0).get("type")).isEqualTo("POOL_SUMMARY");
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
    @DisplayName("resourceGantt initiationId 为空返回空列表")
    void gantt_null() {
        assertThat(service.resourceGantt(null)).isEmpty();
    }

    @Test
    @DisplayName("resourceGantt Feign 返回空数据时返回空列表")
    void gantt_empty() {
        when(benchResourceClient.listResourceAssignmentsByInitiation(any()))
                .thenReturn(Result.ok(Collections.emptyList()));
        assertThat(service.resourceGantt(1L)).isEmpty();
    }

    @Test
    @DisplayName("resourceGantt 真实聚合：转换分配记录为甘特图数据")
    void gantt_normal() {
        Map<String, Object> a1 = new HashMap<>();
        a1.put("id", 101L);
        a1.put("employeeId", 11L);
        a1.put("employeeName", "张三");
        a1.put("levelCode", "L5");
        a1.put("poolType", "DIVISION");
        a1.put("allocation", new BigDecimal("0.5"));
        a1.put("status", "ACTIVE");
        a1.put("billable", 1);
        a1.put("dailyHours", new BigDecimal("4"));
        a1.put("plannedStartDate", "2026-06-01");
        a1.put("plannedEndDate", "2026-08-31");
        when(benchResourceClient.listResourceAssignmentsByInitiation(1L))
                .thenReturn(Result.ok(List.of(a1)));

        List<Map<String, Object>> out = service.resourceGantt(1L);
        assertThat(out).hasSize(1);
        Map<String, Object> row = out.get(0);
        assertThat(row.get("employeeId")).isEqualTo(11L);
        assertThat(row.get("employeeName")).isEqualTo("张三");
        assertThat(row.get("startDate")).isEqualTo("2026-06-01");
        assertThat(row.get("endDate")).isEqualTo("2026-08-31");
        assertThat(row.get("poolType")).isEqualTo("DIVISION");
    }

    @Test
    @DisplayName("resourceGantt 优先使用 actualStartDate/EndDate")
    void gantt_actualPreferred() {
        Map<String, Object> a1 = new HashMap<>();
        a1.put("id", 1L);
        a1.put("employeeId", 1L);
        a1.put("employeeName", "李四");
        a1.put("plannedStartDate", "2026-06-01");
        a1.put("plannedEndDate", "2026-12-31");
        a1.put("actualStartDate", "2026-07-01");
        a1.put("actualEndDate", "2026-11-30");
        when(benchResourceClient.listResourceAssignmentsByInitiation(any()))
                .thenReturn(Result.ok(List.of(a1)));
        List<Map<String, Object>> out = service.resourceGantt(2L);
        assertThat(out.get(0).get("startDate")).isEqualTo("2026-07-01");
        assertThat(out.get(0).get("endDate")).isEqualTo("2026-11-30");
    }

    @Test
    @DisplayName("resourceGantt Feign 异常降级为空")
    void gantt_feignException() {
        when(benchResourceClient.listResourceAssignmentsByInitiation(any()))
                .thenThrow(new RuntimeException("user service down"));
        assertThat(service.resourceGantt(1L)).isEmpty();
    }

    @Test
    @DisplayName("benchCostReport Feign 拉到 totalIdleCost 时 source=USER_FEIGN")
    void bench_feignEnrich() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-06",
                "176", "88", "0", "8", "16");
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(r1));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internal("L5", "1000")));
        when(benchResourceClient.getBenchDashboard()).thenReturn(Result.ok(Map.of(
                "totalIdleCost", new BigDecimal("12345.67"),
                "activePools", List.of(),
                "source", "USER")));

        List<Map<String, Object>> out = service.benchCostReport(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        Map<String, Object> summary = out.get(0);
        assertThat(summary.get("type")).isEqualTo("POOL_SUMMARY");
        assertThat(summary.get("source")).isEqualTo("USER_FEIGN");
        assertThat((BigDecimal) summary.get("totalIdleCost"))
                .isEqualByComparingTo(new BigDecimal("12345.67"));
    }

    @Test
    @DisplayName("benchCostReport Feign 异常降级为 LOCAL_AGG")
    void bench_feignException() {
        Map<String, Object> r1 = rowOf(1L, "张三", "L5", "2026-06",
                "176", "88", "0", "8", "16");
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(r1));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internal("L5", "1000")));
        when(benchResourceClient.getBenchDashboard())
                .thenThrow(new RuntimeException("user service down"));

        List<Map<String, Object>> out = service.benchCostReport(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(out.get(0).get("source")).isEqualTo("LOCAL_AGG");
        assertThat((BigDecimal) out.get(0).get("totalIdleCost"))
                .isEqualByComparingTo(BigDecimal.ZERO);
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

    // ----------------- P2-2 风险矩阵热力图 -----------------

    @Test
    @DisplayName("riskMatrix 空数据返回 9 宫格 + 零计数")
    void riskMatrix_empty() {
        when(riskMapper.selectAll()).thenReturn(List.of());
        Map<String, Object> out = service.riskMatrix(null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) out.get("matrix");
        assertThat(matrix).hasSize(9);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("totalCount")).intValue()).isZero();
        assertThat(((Number) summary.get("highCount")).intValue()).isZero();
        // 轴定义
        @SuppressWarnings("unchecked")
        List<String> axisX = (List<String>) out.get("axisX");
        @SuppressWarnings("unchecked")
        List<String> axisY = (List<String>) out.get("axisY");
        assertThat(axisX).containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(axisY).containsExactly("HIGH", "MEDIUM", "LOW");
    }

    @Test
    @DisplayName("riskMatrix 按 probability × impact 落入正确格子")
    void riskMatrix_classify() {
        RiskDO r1 = new RiskDO(); // LOW*LOW -> LOW
        r1.setInitiationId(1L);
        r1.setProbability("LOW");
        r1.setImpact("LOW");
        r1.setRiskType("SCOPE");
        r1.setStatus("OPEN");
        RiskDO r2 = new RiskDO(); // HIGH*HIGH -> HIGH
        r2.setInitiationId(2L);
        r2.setProbability("HIGH");
        r2.setImpact("HIGH");
        r2.setRiskType("COST");
        r2.setStatus("OPEN");
        RiskDO r3 = new RiskDO(); // MEDIUM*MEDIUM -> MEDIUM
        r3.setInitiationId(3L);
        r3.setProbability("MEDIUM");
        r3.setImpact("MEDIUM");
        r3.setRiskType("SCHEDULE");
        r3.setStatus("CLOSED");
        RiskDO r4 = new RiskDO(); // LOW*MEDIUM -> MEDIUM
        r4.setInitiationId(1L);
        r4.setProbability("LOW");
        r4.setImpact("MEDIUM");
        r4.setRiskType("QUALITY");
        r4.setStatus("OPEN");
        when(riskMapper.selectAll()).thenReturn(List.of(r1, r2, r3, r4));

        Map<String, Object> out = service.riskMatrix(null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) out.get("matrix");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("totalCount")).intValue()).isEqualTo(4);
        assertThat(((Number) summary.get("highCount")).intValue()).isEqualTo(1);
        assertThat(((Number) summary.get("mediumCount")).intValue()).isEqualTo(2);
        assertThat(((Number) summary.get("lowCount")).intValue()).isEqualTo(1);
        assertThat(((Number) summary.get("projectCount")).intValue()).isEqualTo(3);

        // 验证 LOW*LOW = 1, HIGH*HIGH = 1
        Map<String, Object> lowLow = findCell(matrix, "LOW", "LOW");
        assertThat(((Number) lowLow.get("count")).intValue()).isEqualTo(1);
        assertThat(((List<?>) lowLow.get("cellProjectIds"))).hasSize(1);
        Map<String, Object> highHigh = findCell(matrix, "HIGH", "HIGH");
        assertThat(((Number) highHigh.get("count")).intValue()).isEqualTo(1);
        assertThat(highHigh.get("level")).isEqualTo("HIGH");

        // byType 排序
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) out.get("byType");
        assertThat(byType).hasSize(4);
        assertThat(byType.get(0).get("riskType")).isIn("SCOPE", "COST", "SCHEDULE", "QUALITY");
    }

    @Test
    @DisplayName("riskMatrix 按 initiationId 过滤")
    void riskMatrix_filterInitiation() {
        RiskDO r1 = new RiskDO();
        r1.setInitiationId(1L);
        r1.setProbability("HIGH");
        r1.setImpact("HIGH");
        r1.setRiskType("COST");
        RiskDO r2 = new RiskDO();
        r2.setInitiationId(2L);
        r2.setProbability("HIGH");
        r2.setImpact("HIGH");
        r2.setRiskType("COST");
        when(riskMapper.selectAll()).thenReturn(List.of(r1, r2));

        Map<String, Object> out = service.riskMatrix(1L, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("totalCount")).intValue()).isEqualTo(1);
        assertThat(((Number) summary.get("projectCount")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("riskMatrix 按 riskType 过滤")
    void riskMatrix_filterType() {
        RiskDO r1 = new RiskDO();
        r1.setInitiationId(1L);
        r1.setProbability("HIGH");
        r1.setImpact("HIGH");
        r1.setRiskType("COST");
        RiskDO r2 = new RiskDO();
        r2.setInitiationId(2L);
        r2.setProbability("LOW");
        r2.setImpact("LOW");
        r2.setRiskType("SCOPE");
        when(riskMapper.selectAll()).thenReturn(List.of(r1, r2));

        Map<String, Object> out = service.riskMatrix(null, "COST", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("totalCount")).intValue()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byType = (List<Map<String, Object>>) out.get("byType");
        assertThat(byType).hasSize(1);
        assertThat(byType.get(0).get("riskType")).isEqualTo("COST");
    }

    @Test
    @DisplayName("riskMatrix 异常降级返回空矩阵")
    void riskMatrix_exception() {
        when(riskMapper.selectAll()).thenThrow(new RuntimeException("DB down"));
        Map<String, Object> out = service.riskMatrix(null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) out.get("matrix");
        assertThat(matrix).hasSize(9);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("totalCount")).intValue()).isZero();
    }

    @Test
    @DisplayName("riskMatrix 容错处理 null/异常概率与影响")
    void riskMatrix_nullProbImpact() {
        RiskDO r = new RiskDO();
        r.setInitiationId(1L);
        r.setProbability(null);
        r.setImpact(null);
        r.setRiskType("OTHER");
        when(riskMapper.selectAll()).thenReturn(List.of(r));
        Map<String, Object> out = service.riskMatrix(null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) out.get("matrix");
        Map<String, Object> medMed = findCell(matrix, "MEDIUM", "MEDIUM");
        assertThat(((Number) medMed.get("count")).intValue()).isEqualTo(1);
    }

    // ----------------- P2-3 资源占用趋势图 -----------------

    @Test
    @DisplayName("utilizationTrend 空数据返回空结构")
    void utilizationTrend_empty() {
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of());
        Map<String, Object> out = service.resourceUtilizationTrend(null, null, null);
        @SuppressWarnings("unchecked")
        List<String> periods = (List<String>) out.get("periods");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) out.get("series");
        assertThat(periods).isEmpty();
        assertThat(series).isEmpty();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> yAxis = (List<Map<String, Object>>) out.get("yAxisConfig");
        assertThat(yAxis).hasSize(2);
        assertThat(yAxis.get(0).get("position")).isEqualTo("left");
        assertThat(yAxis.get(1).get("position")).isEqualTo("right");
    }

    @Test
    @DisplayName("utilizationTrend 按月聚合总工时/可计费工时/加班/利用率")
    void utilizationTrend_normal() {
        // 准备 2 个月数据
        Map<String, Object> r1 = rowOf(1L, "Alice", "L5", "2026-01", "200", "180", "10", "8", "5");
        Map<String, Object> r2 = rowOf(2L, "Bob", "L6", "2026-01", "190", "150", "20", "10", "0");
        Map<String, Object> r3 = rowOf(1L, "Alice", "L5", "2026-02", "210", "190", "15", "5", "0");
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(r1, r2, r3));
        when(rateInternalMapper.selectAll()).thenReturn(List.of());

        Map<String, Object> out = service.resourceUtilizationTrend(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), null);
        @SuppressWarnings("unchecked")
        List<String> periods = (List<String>) out.get("periods");
        assertThat(periods).containsExactly("2026-01", "2026-02");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) out.get("series");
        assertThat(series).hasSize(4);
        // 总工时 series
        Map<String, Object> totalSeries = series.get(0);
        assertThat(totalSeries.get("name")).isEqualTo("总工时");
        assertThat(totalSeries.get("type")).isEqualTo("bar");
        assertThat(totalSeries.get("yAxisIndex")).isEqualTo(0);
        // 折线 series
        Map<String, Object> utilSeries = series.get(3);
        assertThat(utilSeries.get("name")).isEqualTo("可计费利用率");
        assertThat(utilSeries.get("type")).isEqualTo("line");
        assertThat(utilSeries.get("yAxisIndex")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("monthCount")).intValue()).isEqualTo(2);
        assertThat(summary.get("peakPeriod").toString()).isEqualTo("2026-02");
        assertThat(summary.get("totalBillableHours")).isNotNull();
    }

    @Test
    @DisplayName("utilizationTrend 按部门过滤")
    void utilizationTrend_filterDept() {
        Map<String, Object> r1 = rowOf(1L, "Alice", "L5", "2026-01", "200", "180", "10", "8", "5");
        Map<String, Object> r2 = rowOf(2L, "Bob", "L6", "2026-01", "190", "150", "20", "10", "0");
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of(r1, r2));
        RateInternalDO in5 = new RateInternalDO();
        in5.setLevelCode("L5");
        in5.setDepartmentName("数字一部");
        RateInternalDO in6 = new RateInternalDO();
        in6.setLevelCode("L6");
        in6.setDepartmentName("数字二部");
        when(rateInternalMapper.selectAll()).thenReturn(List.of(in5, in6));

        Map<String, Object> out = service.resourceUtilizationTrend(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), "数字一部");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        // 只有 Alice (L5) 进入"数字一部"
        assertThat(summary.get("totalBillableHours")).isEqualTo(new BigDecimal("180.00"));
    }

    @Test
    @DisplayName("utilizationTrend 异常降级返回空结构")
    void utilizationTrend_exception() {
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any()))
                .thenThrow(new RuntimeException("DB down"));
        Map<String, Object> out = service.resourceUtilizationTrend(null, null, null);
        @SuppressWarnings("unchecked")
        List<String> periods = (List<String>) out.get("periods");
        assertThat(periods).isEmpty();
    }

    @Test
    @DisplayName("utilizationTrend 起始日期大于结束日期自动交换")
    void utilizationTrend_swapRange() {
        when(timeEntryMapper.aggregateBillableByEmployee(any(), any())).thenReturn(List.of());
        Map<String, Object> out = service.resourceUtilizationTrend(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) out.get("filter");
        // 起始 > 结束 已被交换为 from <= to
        assertThat(filter.get("from").toString()).isEqualTo("2026-01-01");
        assertThat(filter.get("to").toString()).isEqualTo("2026-06-01");
    }

    // ----------------- P2-5 项目健康仪表盘 -----------------

    @Test
    @DisplayName("projectHealthDashboard 空数据返回空结构")
    void projectHealth_empty() {
        when(evmMapper.aggregateHealthByInitiation()).thenReturn(List.of());
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> out = service.projectHealthDashboard(null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) out.get("projects");
        assertThat(projects).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("totalCount")).intValue()).isZero();
    }

    @Test
    @DisplayName("projectHealthDashboard 综合 CPI/SPI/毛利率评分 GREEN")
    void projectHealth_green() {
        Map<String, Object> evm = new HashMap<>();
        evm.put("initiation_id", 1L);
        evm.put("cpi", new BigDecimal("1.10"));
        evm.put("spi", new BigDecimal("1.05"));
        evm.put("eac", new BigDecimal("90000"));
        evm.put("vac", new BigDecimal("10000"));
        evm.put("top_alert", "NORMAL");
        when(evmMapper.aggregateHealthByInitiation()).thenReturn(List.of(evm));
        ProfitSnapshotDO snap = new ProfitSnapshotDO();
        snap.setInitiationId(1L);
        snap.setPeriod("2026-Q1");
        snap.setSnapshotAt(LocalDateTime.of(2026, 3, 31, 0, 0));
        snap.setGrossMargin(new BigDecimal("0.30"));
        snap.setTotalCost(new BigDecimal("70000"));
        snap.setGrossProfit(new BigDecimal("30000"));
        snap.setContractAmount(new BigDecimal("100000"));
        snap.setRecognizedRevenue(new BigDecimal("100000"));
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of(snap));

        Map<String, Object> out = service.projectHealthDashboard(null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) out.get("projects");
        assertThat(projects).hasSize(1);
        Map<String, Object> p = projects.get(0);
        // cpi=1.10*50=55, spi=1.05*30=31.5, margin=0.30*200=60 -> cap 20
        // score = 55 + 31.5 + 20 = 106.5 -> clamp... 实际 cap 60 for cpi part, cap 30 for spi
        // 我们的实现：cpiPart = min(1.10*50, 60) = 55; spiPart = min(1.05*30, 30) = 31.5; marginScore = min(0.30*200, 20) = 20
        // score = 55 + 31.5 + 20 = 106.5
        assertThat(p.get("healthLevel")).isEqualTo("GREEN");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("greenCount")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("projectHealthDashboard CPI 极低时强制 0 分 + RED")
    void projectHealth_redByCpi() {
        Map<String, Object> evm = new HashMap<>();
        evm.put("initiation_id", 2L);
        evm.put("cpi", new BigDecimal("0.50"));   // 极低 CPI
        evm.put("spi", new BigDecimal("1.20"));
        evm.put("eac", new BigDecimal("200000"));
        evm.put("vac", new BigDecimal("-50000"));
        evm.put("top_alert", "RED");
        when(evmMapper.aggregateHealthByInitiation()).thenReturn(List.of(evm));
        ProfitSnapshotDO snap = new ProfitSnapshotDO();
        snap.setInitiationId(2L);
        snap.setPeriod("2026-Q1");
        snap.setSnapshotAt(LocalDateTime.of(2026, 3, 31, 0, 0));
        snap.setGrossMargin(new BigDecimal("0.10"));
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of(snap));

        Map<String, Object> out = service.projectHealthDashboard(null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) out.get("projects");
        assertThat(projects).hasSize(1);
        Map<String, Object> p = projects.get(0);
        // cpi=0.5 < 0.8 -> cpiPart = 0
        // spi=1.2*30 = 36 -> cap 30
        // margin=0.10*200=20 -> cap 20
        // score = 0 + 30 + 20 = 50 -> RED
        assertThat(p.get("healthLevel")).isEqualTo("RED");
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("redCount")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("projectHealthDashboard 按 health=GREEN 过滤")
    void projectHealth_filterByHealth() {
        Map<String, Object> evm1 = new HashMap<>();
        evm1.put("initiation_id", 1L);
        evm1.put("cpi", new BigDecimal("1.10"));
        evm1.put("spi", new BigDecimal("1.05"));
        evm1.put("eac", BigDecimal.ZERO);
        evm1.put("vac", BigDecimal.ZERO);
        evm1.put("top_alert", "NORMAL");
        Map<String, Object> evm2 = new HashMap<>();
        evm2.put("initiation_id", 2L);
        evm2.put("cpi", new BigDecimal("0.50"));
        evm2.put("spi", new BigDecimal("0.50"));
        evm2.put("eac", BigDecimal.ZERO);
        evm2.put("vac", BigDecimal.ZERO);
        evm2.put("top_alert", "RED");
        when(evmMapper.aggregateHealthByInitiation()).thenReturn(List.of(evm1, evm2));
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> out = service.projectHealthDashboard(null, "GREEN");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) out.get("projects");
        // 项目 1 cpi=1.10*50=55, spi=1.05*30=31.5, margin=0 -> score=86.5 -> GREEN
        // 项目 2 cpi<0.8 -> 0; spi<0.8 -> 0; margin=0 -> score=0 -> UNKNOWN
        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).get("initiationId")).isEqualTo(1L);
    }

    @Test
    @DisplayName("projectHealthDashboard 按 initiationIds 过滤")
    void projectHealth_filterByIds() {
        Map<String, Object> evm1 = new HashMap<>();
        evm1.put("initiation_id", 1L);
        evm1.put("cpi", new BigDecimal("1.10"));
        evm1.put("spi", new BigDecimal("1.05"));
        evm1.put("eac", BigDecimal.ZERO);
        evm1.put("vac", BigDecimal.ZERO);
        evm1.put("top_alert", "NORMAL");
        Map<String, Object> evm2 = new HashMap<>();
        evm2.put("initiation_id", 2L);
        evm2.put("cpi", new BigDecimal("1.10"));
        evm2.put("spi", new BigDecimal("1.05"));
        evm2.put("eac", BigDecimal.ZERO);
        evm2.put("vac", BigDecimal.ZERO);
        evm2.put("top_alend", "NORMAL");
        evm2.put("top_alert", "NORMAL");
        when(evmMapper.aggregateHealthByInitiation()).thenReturn(List.of(evm1, evm2));
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> out = service.projectHealthDashboard(List.of(1L), null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) out.get("projects");
        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).get("initiationId")).isEqualTo(1L);
    }

    @Test
    @DisplayName("projectHealthDashboard 全部数据缺失时为 UNKNOWN")
    void projectHealth_unknown() {
        when(evmMapper.aggregateHealthByInitiation()).thenReturn(List.of());
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> out = service.projectHealthDashboard(null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("unknownCount")).intValue()).isZero();
        assertThat(((Number) summary.get("totalCount")).intValue()).isZero();
    }

    @Test
    @DisplayName("projectHealthDashboard 异常降级返回空")
    void projectHealth_exception() {
        when(evmMapper.aggregateHealthByInitiation()).thenThrow(new RuntimeException("DB down"));
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> out = service.projectHealthDashboard(null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) out.get("projects");
        assertThat(projects).isEmpty();
    }

    private static Map<String, Object> findCell(List<Map<String, Object>> matrix,
                                                 String probability, String impact) {
        for (Map<String, Object> cell : matrix) {
            if (probability.equals(cell.get("probability")) && impact.equals(cell.get("impact"))) {
                return cell;
            }
        }
        throw new AssertionError("cell not found: " + probability + " * " + impact);
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
