package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.dto.CockpitDrillDownDTO;
import com.njydsz.pmis.execution.dto.CockpitKpiVO;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import com.njydsz.pmis.execution.mapper.PaymentMapper;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

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
        service = new CockpitReportServiceImpl(invoiceMapper, paymentMapper, costAllocationMapper,
                purchaseMapper, expenseMapper, evmMeasureMapper, riskMapper);
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
        Map<String, Object> out = service.utilizationSummary(null);
        assertThat(out).containsKeys("avgBillable", "overloaded", "underutilized", "normal");
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
}
