package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import com.njydsz.pmis.execution.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import com.njydsz.pmis.execution.mapper.RevenueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ReportServiceImpl 报表服务测试")
class ReportServiceImplTest {

    private ProfitSnapshotMapper profitSnapshotMapper;
    private CostAllocationMapper costAllocationMapper;
    private ExpenseMapper expenseMapper;
    private PurchaseMapper purchaseMapper;
    private RevenueMapper revenueMapper;
    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        profitSnapshotMapper = mock(ProfitSnapshotMapper.class);
        costAllocationMapper = mock(CostAllocationMapper.class);
        expenseMapper = mock(ExpenseMapper.class);
        purchaseMapper = mock(PurchaseMapper.class);
        revenueMapper = mock(RevenueMapper.class);
        service = new ReportServiceImpl(profitSnapshotMapper, costAllocationMapper,
                expenseMapper, purchaseMapper, revenueMapper);
    }

    @Test
    @DisplayName("projectProfitReport initiationId 为空")
    void profitNull() {
        Map<String, Object> r = service.projectProfitReport(null, null);
        assertThat(r).containsKey("error");
    }

    @Test
    @DisplayName("projectProfitReport 累计")
    void profitAggregate() {
        when(costAllocationMapper.selectByInitiationAndPeriod(1L, null)).thenReturn(List.of());
        when(revenueMapper.selectByInitiation(1L)).thenReturn(List.of());
        when(purchaseMapper.selectList(any())).thenReturn(List.of());
        when(expenseMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> r = service.projectProfitReport(1L, null);
        assertThat(r).containsKeys("revenue", "totalCost", "grossProfit", "grossMargin");
    }

    @Test
    @DisplayName("costDetailReport 人员维度")
    void costByEmployee() {
        when(costAllocationMapper.selectByInitiationAndPeriod(1L, null)).thenReturn(List.of());
        Map<String, Object> r = service.costDetailReport(1L, null);
        assertThat(r).containsKeys("breakdown", "ratio", "byEmployee");
        assertThat((List<?>) r.get("byEmployee")).isEmpty();
    }

    @Test
    @DisplayName("paymentLedgerReport 累计收入")
    void paymentLedger() {
        when(revenueMapper.selectByInitiation(1L)).thenReturn(List.of());
        when(revenueMapper.sumByPeriod(1L)).thenReturn(List.of());
        Map<String, Object> r = service.paymentLedgerReport(1L);
        assertThat(r.get("totalRevenue")).isEqualTo(java.math.BigDecimal.ZERO);
    }

    @Test
    @DisplayName("projectLifecycleReport")
    void lifecycle() {
        when(costAllocationMapper.selectByInitiationAndPeriod(1L, null)).thenReturn(List.of());
        when(revenueMapper.selectByInitiation(1L)).thenReturn(List.of());
        when(purchaseMapper.selectList(any())).thenReturn(List.of());
        when(expenseMapper.selectList(any())).thenReturn(List.of());
        Map<String, Object> r = service.projectLifecycleReport(1L);
        assertThat(r).containsKeys("costSummary", "revenueSummary");
    }

    @Test
    @DisplayName("profitSummaryAll 空")
    void profitSummaryEmpty() {
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of());
        assertThat(service.profitSummaryAll()).isEmpty();
    }
}
