package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.dto.CockpitKpiVO;
import com.njydsz.pmis.execution.mapper.BillableUtilizationSnapshotMapper;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import com.njydsz.pmis.execution.mapper.PaymentMapper;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import com.njydsz.pmis.execution.service.BillableUtilizationService;
import com.njydsz.pmis.literule.api.RuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CockpitReportServiceImpl 测试
 */
@DisplayName("CockpitReportServiceImpl 经营驾驶舱")
class CockpitReportServiceImplTest {

    private InvoiceMapper invoiceMapper;
    private PaymentMapper paymentMapper;
    private CostAllocationMapper costAllocationMapper;
    private PurchaseMapper purchaseMapper;
    private ExpenseMapper expenseMapper;
    private EvmMeasureMapper evmMeasureMapper;
    private RiskMapper riskMapper;
    private BillableUtilizationSnapshotMapper utilizationSnapshotMapper;
    private BillableUtilizationService billableUtilizationService;
    private RuleEngine liteRuleEngine;
    private CockpitReportServiceImpl service;

    @BeforeEach
    void setUp() {
        invoiceMapper = mock(InvoiceMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        costAllocationMapper = mock(CostAllocationMapper.class);
        purchaseMapper = mock(PurchaseMapper.class);
        expenseMapper = mock(ExpenseMapper.class);
        evmMeasureMapper = mock(EvmMeasureMapper.class);
        riskMapper = mock(RiskMapper.class);
        utilizationSnapshotMapper = mock(BillableUtilizationSnapshotMapper.class);
        billableUtilizationService = mock(BillableUtilizationService.class);
        liteRuleEngine = mock(RuleEngine.class);
        // 默认返回空规则列表，使 CockpitReportServiceImpl fallback 到 legacyAlertEngine
        when(liteRuleEngine.getRules()).thenReturn(List.of());
        service = new CockpitReportServiceImpl(invoiceMapper, paymentMapper, costAllocationMapper,
                purchaseMapper, expenseMapper, evmMeasureMapper, riskMapper,
                utilizationSnapshotMapper, billableUtilizationService, liteRuleEngine);
    }

    @Test
    @DisplayName("overview 收入/成本/毛利/毛利率计算")
    void overview_normal() {
        when(invoiceMapper.countDistinctInitiation()).thenReturn(5);
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(new BigDecimal("1000.00"));
        when(paymentMapper.sumAllocatedAmount()).thenReturn(new BigDecimal("800.00"));
        when(costAllocationMapper.sumAllAmount()).thenReturn(new BigDecimal("300.00"));
        when(purchaseMapper.sumAllAmount()).thenReturn(new BigDecimal("100.00"));
        when(expenseMapper.sumAllAmount()).thenReturn(new BigDecimal("100.00"));
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenReturn(List.of(
                Map.of("initiation_id", 1, "top_alert", "RED"),
                Map.of("initiation_id", 2, "top_alert", "YELLOW"),
                Map.of("initiation_id", 3, "top_alert", "NORMAL")
        ));
        Map<String, Object> snap = new HashMap<>();
        snap.put("avg_pct", 0.82);
        snap.put("headcount", 8L);
        snap.put("source", "SNAPSHOT");
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(snap);

        CockpitKpiVO kpi = service.overview(null, null);

        assertThat(kpi.getActiveProjects()).isEqualTo(5);
        assertThat(kpi.getTotalContractAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(kpi.getConfirmedRevenue()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(kpi.getTotalCost()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(kpi.getGrossProfit()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(kpi.getGrossMargin()).isEqualByComparingTo(new BigDecimal("0.3750"));
        assertThat(kpi.getEvmRedCount()).isEqualTo(1);
        assertThat(kpi.getEvmYellowCount()).isEqualTo(1);
        assertThat(kpi.getEvmGreenCount()).isEqualTo(1);
        assertThat(kpi.getAvgBillableUtilization()).isEqualByComparingTo(new BigDecimal("0.82"));
    }

    @Test
    @DisplayName("overview 收入为 0 时毛利率 0")
    void overview_zeroRevenue() {
        when(invoiceMapper.countDistinctInitiation()).thenReturn(0);
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.sumAllocatedAmount()).thenReturn(BigDecimal.ZERO);
        when(costAllocationMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(purchaseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenReturn(List.of());
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(new HashMap<>());

        CockpitKpiVO kpi = service.overview(null, null);

        assertThat(kpi.getGrossMargin()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(kpi.getGrossProfit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("overview mapper 异常时安全降级")
    void overview_mapperException() {
        when(invoiceMapper.countDistinctInitiation()).thenThrow(new RuntimeException("DB down"));
        when(invoiceMapper.sumInvoicedAmount()).thenThrow(new RuntimeException("DB down"));
        when(paymentMapper.sumAllocatedAmount()).thenThrow(new RuntimeException("DB down"));
        when(costAllocationMapper.sumAllAmount()).thenThrow(new RuntimeException("DB down"));
        when(purchaseMapper.sumAllAmount()).thenThrow(new RuntimeException("DB down"));
        when(expenseMapper.sumAllAmount()).thenThrow(new RuntimeException("DB down"));
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenThrow(new RuntimeException("DB down"));
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(new HashMap<>());

        CockpitKpiVO kpi = service.overview(null, null);

        assertThat(kpi.getActiveProjects()).isEqualTo(0);
        assertThat(kpi.getTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(kpi.getEvmRedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("evmHealthDistribution 空列表返回 0")
    void evmHealth_empty() {
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenReturn(List.of());
        Map<String, Integer> out = service.evmHealthDistribution(null, null);
        assertThat(out).containsEntry("RED", 0)
                .containsEntry("YELLOW", 0)
                .containsEntry("NORMAL", 0);
    }

    @Test
    @DisplayName("evmHealthDistribution 异常降级")
    void evmHealth_exception() {
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenThrow(new RuntimeException());
        Map<String, Integer> out = service.evmHealthDistribution(null, null);
        assertThat(out).containsEntry("RED", 0);
    }

    @Test
    @DisplayName("benchCostSummary 返回基础结构")
    void benchCost() {
        Map<String, Object> out = service.benchCostSummary(null);
        assertThat(out).containsKeys("totalIdleCost", "activeBench", "warningYellow", "warningRed");
        assertThat((BigDecimal) out.get("totalIdleCost")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("utilizationSummary 返回基础结构")
    void utilization() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("avg_pct", 0.75);
        snap.put("headcount", 5L);
        snap.put("source", "SNAPSHOT");
        snap.put("warn_count", 1L);
        snap.put("critical_count", 0L);
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(snap);
        when(utilizationSnapshotMapper.gradeDistribution(any())).thenReturn(List.of());
        when(utilizationSnapshotMapper.groupByDepartment(any())).thenReturn(List.of());

        Map<String, Object> out = service.utilizationSummary(null);
        assertThat(out).containsKeys("avgBillable", "source", "headcount", "gradeDistribution", "topDepartments");
        assertThat((BigDecimal) out.get("avgBillable")).isEqualByComparingTo(new BigDecimal("0.75"));
        assertThat(out.get("warnCount")).isEqualTo(1L);
        assertThat(out.get("criticalCount")).isEqualTo(0L);
    }

    @Test
    @DisplayName("utilizationSummary 部门聚合异常时安全降级")
    void utilization_deptException() {
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(new HashMap<>());
        when(utilizationSnapshotMapper.gradeDistribution(any())).thenThrow(new RuntimeException());
        when(utilizationSnapshotMapper.groupByDepartment(any())).thenThrow(new RuntimeException());

        Map<String, Object> out = service.utilizationSummary(null);
        assertThat(out).containsKeys("avgBillable");
    }

    @Test
    @DisplayName("drillByDept 委托 mapper 成功")
    void drillByDept_success() {
        when(invoiceMapper.sumByDepartment()).thenReturn(List.of(Map.of("department_id", 1)));
        assertThat(service.drillByDept(null)).hasSize(1);
    }

    @Test
    @DisplayName("drillByDept mapper 异常时返回空")
    void drillByDept_exception() {
        when(invoiceMapper.sumByDepartment()).thenThrow(new RuntimeException());
        assertThat(service.drillByDept(null)).isEmpty();
    }

    @Test
    @DisplayName("drillByCustomer 委托 mapper 成功")
    void drillByCustomer_success() {
        when(invoiceMapper.sumByCustomer()).thenReturn(List.of(Map.of("customer_id", 7)));
        assertThat(service.drillByCustomer(null)).hasSize(1);
    }

    @Test
    @DisplayName("drillByProjectType 当前为占位实现返回空")
    void drillByProjectType() {
        assertThat(service.drillByProjectType(null)).isEmpty();
    }

    // ----------------- P2-4 合同总额年度趋势 -----------------

    @Test
    @DisplayName("contractAmountYearlyTrend 空数据返回空结构")
    void contractYearlyTrend_empty() {
        when(invoiceMapper.sumByYear()).thenReturn(List.of());
        Map<String, Object> out = service.contractAmountYearlyTrend();
        @SuppressWarnings("unchecked")
        List<String> years = (List<String>) out.get("years");
        assertThat(years).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("yearCount")).intValue()).isZero();
    }

    @Test
    @DisplayName("contractAmountYearlyTrend 多年聚合含合同额、项目数、同比")
    void contractYearlyTrend_normal() {
        Map<String, Object> r2023 = new HashMap<>();
        r2023.put("year", "2023");
        r2023.put("total_amount", new BigDecimal("1000000"));
        r2023.put("invoice_count", 5);
        r2023.put("project_count", 3);
        Map<String, Object> r2024 = new HashMap<>();
        r2024.put("year", "2024");
        r2024.put("total_amount", new BigDecimal("1500000"));
        r2024.put("invoice_count", 8);
        r2024.put("project_count", 4);
        Map<String, Object> r2025 = new HashMap<>();
        r2025.put("year", "2025");
        r2025.put("total_amount", new BigDecimal("1200000"));
        r2025.put("invoice_count", 6);
        r2025.put("project_count", 5);
        when(invoiceMapper.sumByYear()).thenReturn(List.of(r2023, r2024, r2025));

        Map<String, Object> out = service.contractAmountYearlyTrend();
        @SuppressWarnings("unchecked")
        List<String> years = (List<String>) out.get("years");
        assertThat(years).containsExactly("2023", "2024", "2025");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) out.get("series");
        assertThat(series).hasSize(2);
        // 合同总额 series
        Map<String, Object> amountSeries = series.get(0);
        assertThat(amountSeries.get("type")).isEqualTo("bar");
        @SuppressWarnings("unchecked")
        List<BigDecimal> amountData = (List<BigDecimal>) amountSeries.get("data");
        assertThat(amountData).containsExactly(
                new BigDecimal("1000000"),
                new BigDecimal("1500000"),
                new BigDecimal("1200000"));

        // summary
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("yearCount")).intValue()).isEqualTo(3);
        assertThat(summary.get("peakYear")).isEqualTo("2024");
        assertThat(summary.get("peakAmount")).isEqualTo(new BigDecimal("1500000"));
        // 同比 = (1200000 - 1500000) / 1500000 = -0.20
        assertThat(((Number) summary.get("latestYoy")).doubleValue()).isEqualTo(-0.20);
        // 累计 = 3700000
        assertThat(summary.get("totalAmount")).isEqualTo(new BigDecimal("3700000"));
        // 累计项目数 3+4+5=12
        assertThat(((Number) summary.get("totalProjects")).intValue()).isEqualTo(12);
        // 累计发票数 5+8+6=19
        assertThat(((Number) summary.get("totalInvoices")).intValue()).isEqualTo(19);
    }

    @Test
    @DisplayName("contractAmountYearlyTrend 单年无同比")
    void contractYearlyTrend_singleYear() {
        Map<String, Object> r = new HashMap<>();
        r.put("year", "2024");
        r.put("total_amount", new BigDecimal("500000"));
        r.put("invoice_count", 3);
        r.put("project_count", 2);
        when(invoiceMapper.sumByYear()).thenReturn(List.of(r));
        Map<String, Object> out = service.contractAmountYearlyTrend();
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) out.get("summary");
        assertThat(((Number) summary.get("latestYoy")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("contractAmountYearlyTrend 异常降级")
    void contractYearlyTrend_exception() {
        when(invoiceMapper.sumByYear()).thenThrow(new RuntimeException("DB down"));
        Map<String, Object> out = service.contractAmountYearlyTrend();
        @SuppressWarnings("unchecked")
        List<String> years = (List<String>) out.get("years");
        assertThat(years).isEmpty();
    }

    // ============= 批次18 增量测试 =============

    @Test
    @DisplayName("alertSummary 健康快照：无任何告警")
    void alertSummary_healthy() {
        when(invoiceMapper.countDistinctInitiation()).thenReturn(5);
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(new BigDecimal("1000"));
        when(paymentMapper.sumAllocatedAmount()).thenReturn(new BigDecimal("800"));
        when(costAllocationMapper.sumAllAmount()).thenReturn(new BigDecimal("200"));
        when(purchaseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenReturn(List.of());
        Map<String, Object> util = new HashMap<>();
        util.put("avg_pct", 0.85);
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(util);

        com.njydsz.pmis.execution.dto.CockpitAlertSummaryVO out = service.alertSummary(null, null);
        assertThat(out.getTotalCount()).isZero();
        assertThat(out.getRedCount()).isZero();
        assertThat(out.getYellowCount()).isZero();
        assertThat(out.getEvents()).isEmpty();
        assertThat(out.getTopEvent()).isNull();
    }

    @Test
    @DisplayName("alertSummary 异常 KPI：触发 RED + YELLOW")
    void alertSummary_triggersMultiple() {
        when(invoiceMapper.countDistinctInitiation()).thenReturn(5);
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(new BigDecimal("1000"));
        when(paymentMapper.sumAllocatedAmount()).thenReturn(new BigDecimal("800"));
        when(costAllocationMapper.sumAllAmount()).thenReturn(new BigDecimal("700"));
        when(purchaseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenReturn(List.of(
                Map.of("initiation_id", 1, "top_alert", "RED"),
                Map.of("initiation_id", 2, "top_alert", "RED"),
                Map.of("initiation_id", 3, "top_alert", "RED"),
                Map.of("initiation_id", 4, "top_alert", "YELLOW")
        ));
        Map<String, Object> util = new HashMap<>();
        util.put("avg_pct", 0.40); // 触发 UTILIZATION_LOW RED
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(util);

        com.njydsz.pmis.execution.dto.CockpitAlertSummaryVO out = service.alertSummary(null, null);
        assertThat(out.getRedCount()).isGreaterThanOrEqualTo(2); // EVM_RED + UTILIZATION
        assertThat(out.getEvents().get(0).getSeverity().getWeight())
                .isGreaterThanOrEqualTo(out.getEvents().get(out.getEvents().size() - 1).getSeverity().getWeight());
        assertThat(out.getTopEvent()).isNotNull();
    }

    @Test
    @DisplayName("alertSummary mapper 异常时安全降级（无任何告警）")
    void alertSummary_mapperException() {
        when(invoiceMapper.countDistinctInitiation()).thenThrow(new RuntimeException("DB"));
        when(invoiceMapper.sumInvoicedAmount()).thenThrow(new RuntimeException("DB"));
        when(paymentMapper.sumAllocatedAmount()).thenThrow(new RuntimeException("DB"));
        when(costAllocationMapper.sumAllAmount()).thenThrow(new RuntimeException("DB"));
        when(purchaseMapper.sumAllAmount()).thenThrow(new RuntimeException("DB"));
        when(expenseMapper.sumAllAmount()).thenThrow(new RuntimeException("DB"));
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenThrow(new RuntimeException("DB"));
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(new HashMap<>());

        com.njydsz.pmis.execution.dto.CockpitAlertSummaryVO out = service.alertSummary(null, null);
        assertThat(out.getTotalCount()).isZero();
    }

    @Test
    @DisplayName("projectGroupOverview 按 levelCode 聚合并按合同额降序")
    void projectGroupOverview_normal() {
        Map<String, Object> r1 = new HashMap<>();
        r1.put("level_code", "L5");
        r1.put("total_amount", new BigDecimal("600"));
        r1.put("entry_count", 5);
        Map<String, Object> r2 = new HashMap<>();
        r2.put("level_code", "L10");
        r2.put("total_amount", new BigDecimal("300"));
        r2.put("entry_count", 3);
        Map<String, Object> r3 = new HashMap<>();
        r3.put("level_code", "UNKNOWN");
        r3.put("total_amount", new BigDecimal("100"));
        r3.put("entry_count", 1);
        when(costAllocationMapper.sumByLevelCode()).thenReturn(List.of(r1, r2, r3));
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(new BigDecimal("1000"));
        when(paymentMapper.sumAllocatedAmount()).thenReturn(new BigDecimal("800"));
        when(costAllocationMapper.sumAllAmount()).thenReturn(new BigDecimal("1000"));
        when(purchaseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);

        List<com.njydsz.pmis.execution.dto.ProjectGroupKpiDTO> out = service.projectGroupOverview(null, null);
        assertThat(out).hasSize(3);
        // 按 totalContractAmount 降序
        assertThat(out.get(0).getGroupCode()).isEqualTo("L5");
        assertThat(out.get(0).getTotalContractAmount()).isEqualByComparingTo(new BigDecimal("600.00"));
        // UNKNOWN 群名应回退到"未分类项目群"
        assertThat(out.get(2).getGroupName()).isEqualTo("未分类项目群");
    }

    @Test
    @DisplayName("projectGroupOverview mapper 异常时返回空")
    void projectGroupOverview_exception() {
        when(costAllocationMapper.sumByLevelCode()).thenThrow(new RuntimeException("DB"));
        List<com.njydsz.pmis.execution.dto.ProjectGroupKpiDTO> out = service.projectGroupOverview(null, null);
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("executiveOverview 健康快照：评分=0 等级 D")
    void executiveOverview_healthy() {
        when(invoiceMapper.countDistinctInitiation()).thenReturn(3);
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(new BigDecimal("1000"));
        when(paymentMapper.sumAllocatedAmount()).thenReturn(new BigDecimal("800"));
        when(costAllocationMapper.sumAllAmount()).thenReturn(new BigDecimal("300"));
        when(purchaseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenReturn(List.of(
                Map.of("initiation_id", 1, "top_alert", "NORMAL"),
                Map.of("initiation_id", 2, "top_alert", "NORMAL")
        ));
        when(riskMapper.countByRiskLevel()).thenReturn(List.of());
        when(costAllocationMapper.sumByLevelCode()).thenReturn(List.of());
        Map<String, Object> util = new HashMap<>();
        util.put("avg_pct", 0.85);
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(util);

        com.njydsz.pmis.execution.dto.ExecutiveOverviewVO out = service.executiveOverview(null, null);
        assertThat(out.getActiveProjects()).isEqualTo(3);
        assertThat(out.getEvmGreenCount()).isEqualTo(2);
        assertThat(out.getHealthGrade()).isIn("A", "B", "C", "D");
        assertThat(out.getHealthScore()).isNotNull();
        assertThat(out.getProjectGroups()).isNotNull();
    }

    @Test
    @DisplayName("executiveOverview 含风险时 riskProjectCount 正确")
    void executiveOverview_withRisk() {
        when(invoiceMapper.countDistinctInitiation()).thenReturn(10);
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(new BigDecimal("1000"));
        when(paymentMapper.sumAllocatedAmount()).thenReturn(new BigDecimal("800"));
        when(costAllocationMapper.sumAllAmount()).thenReturn(new BigDecimal("300"));
        when(purchaseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenReturn(List.of());
        when(riskMapper.countByRiskLevel()).thenReturn(List.of(
                Map.of("risk_level", "RED", "cnt", 2),
                Map.of("risk_level", "YELLOW", "cnt", 3)
        ));
        when(costAllocationMapper.sumByLevelCode()).thenReturn(List.of());
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(new HashMap<>());

        com.njydsz.pmis.execution.dto.ExecutiveOverviewVO out = service.executiveOverview(null, null);
        assertThat(out.getRiskProjectCount()).isEqualTo(5);
        assertThat(out.getRiskProjectRatio()).isEqualByComparingTo(new BigDecimal("0.5000"));
    }

    @Test
    @DisplayName("executiveOverview riskMapper 异常时风险计数 0 不抛错")
    void executiveOverview_riskException() {
        when(invoiceMapper.countDistinctInitiation()).thenReturn(3);
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.sumAllocatedAmount()).thenReturn(BigDecimal.ZERO);
        when(costAllocationMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(purchaseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(evmMeasureMapper.aggregateHealthByInitiation()).thenReturn(List.of());
        when(riskMapper.countByRiskLevel()).thenThrow(new RuntimeException("DB"));
        when(costAllocationMapper.sumByLevelCode()).thenThrow(new RuntimeException("DB"));
        when(billableUtilizationService.snapshotAverage(any())).thenReturn(new HashMap<>());

        com.njydsz.pmis.execution.dto.ExecutiveOverviewVO out = service.executiveOverview(null, null);
        assertThat(out.getRiskProjectCount()).isZero();
        assertThat(out.getHealthGrade()).isNotNull();
    }

    @Test
    @DisplayName("kpiTrend 正常：合同+回款序列对齐月份 + MTD 增长率")
    void kpiTrend_normal() {
        Map<String, Object> c1 = new HashMap<>();
        c1.put("month", "2025-08");
        c1.put("total_amount", new BigDecimal("100"));
        c1.put("invoice_count", 1);
        Map<String, Object> c2 = new HashMap<>();
        c2.put("month", "2025-09");
        c2.put("total_amount", new BigDecimal("120"));
        c2.put("invoice_count", 1);
        Map<String, Object> p1 = new HashMap<>();
        p1.put("month", "2025-08");
        p1.put("amount", new BigDecimal("80"));
        p1.put("cnt", 1);
        Map<String, Object> p2 = new HashMap<>();
        p2.put("month", "2025-09");
        p2.put("amount", new BigDecimal("100"));
        p2.put("cnt", 1);
        when(invoiceMapper.sumByRecentMonth(any())).thenReturn(List.of(c1, c2));
        when(paymentMapper.aggregateByRecentMonth(any())).thenReturn(List.of(p1, p2));

        com.njydsz.pmis.execution.dto.KpiTrendVO out = service.kpiTrend(12);
        assertThat(out.getPeriods()).hasSize(2);
        // 升序：8 月在 9 月前
        assertThat(out.getPeriods().get(0)).isEqualTo("2025-08");
        assertThat(out.getPeriods().get(1)).isEqualTo("2025-09");
        // MTD 增长 = (120-100)/100 = 0.20
        assertThat(out.getContractMtdGrowth()).isEqualByComparingTo(new BigDecimal("0.2000"));
        // 9 月合同 120，回款 100，毛利 100
        assertThat(out.getGrossProfitSeries().get(1)).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("kpiTrend 空数据：months 默认 12")
    void kpiTrend_empty() {
        when(invoiceMapper.sumByRecentMonth(any())).thenReturn(List.of());
        when(paymentMapper.aggregateByRecentMonth(any())).thenReturn(List.of());
        com.njydsz.pmis.execution.dto.KpiTrendVO out = service.kpiTrend(0);
        assertThat(out.getPeriods()).isEmpty();
        assertThat(out.getContractMtdGrowth()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("kpiTrend mapper 异常时不抛错")
    void kpiTrend_exception() {
        when(invoiceMapper.sumByRecentMonth(any())).thenThrow(new RuntimeException("DB"));
        when(paymentMapper.aggregateByRecentMonth(any())).thenThrow(new RuntimeException("DB"));
        com.njydsz.pmis.execution.dto.KpiTrendVO out = service.kpiTrend(12);
        assertThat(out.getPeriods()).isEmpty();
    }

    @Test
    @DisplayName("kpiTrend months 上限裁剪到 36")
    void kpiTrend_monthsClampedTo36() {
        when(invoiceMapper.sumByRecentMonth(any())).thenReturn(List.of());
        when(paymentMapper.aggregateByRecentMonth(any())).thenReturn(List.of());
        com.njydsz.pmis.execution.dto.KpiTrendVO out = service.kpiTrend(99);
        assertThat(out.getPeriods()).isEmpty();
        org.mockito.Mockito.verify(invoiceMapper).sumByRecentMonth(Integer.valueOf(36));
    }

    @Test
    @DisplayName("kpiTrend null months 回退默认 12")
    void kpiTrend_nullMonths() {
        when(invoiceMapper.sumByRecentMonth(any())).thenReturn(List.of());
        when(paymentMapper.aggregateByRecentMonth(any())).thenReturn(List.of());
        service.kpiTrend(null);
        org.mockito.Mockito.verify(invoiceMapper).sumByRecentMonth(Integer.valueOf(12));
    }

    @Test
    @DisplayName("kpiTrend 仅合同/仅回款单边数据仍能输出")
    void kpiTrend_oneSideData() {
        Map<String, Object> c1 = new HashMap<>();
        c1.put("month", "2025-09");
        c1.put("total_amount", new BigDecimal("100"));
        c1.put("invoice_count", 1);
        when(invoiceMapper.sumByRecentMonth(any())).thenReturn(List.of(c1));
        when(paymentMapper.aggregateByRecentMonth(any())).thenReturn(List.of());
        com.njydsz.pmis.execution.dto.KpiTrendVO out = service.kpiTrend(12);
        assertThat(out.getPeriods()).containsExactly("2025-09");
        assertThat(out.getConfirmedRevenueSeries().get(0)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
