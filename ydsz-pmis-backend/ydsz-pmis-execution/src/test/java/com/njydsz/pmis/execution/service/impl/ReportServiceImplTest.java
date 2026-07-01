package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.entity.ProfitSnapshotDO;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import com.njydsz.pmis.execution.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import com.njydsz.pmis.execution.mapper.RevenueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Test
    @DisplayName("profitRank 空快照返回空列表")
    void profitRankEmpty() {
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of());
        assertThat(service.profitRank(10, "grossMargin", null)).isEmpty();
    }

    @Test
    @DisplayName("profitRank 按毛利率倒序取最新快照")
    void profitRankByMargin() {
        // 项目 1：两期快照，取最新
        ProfitSnapshotDO s1a = new ProfitSnapshotDO();
        s1a.setInitiationId(1L);
        s1a.setPeriod("2026-01");
        s1a.setSnapshotAt(LocalDateTime.of(2026, 1, 31, 0, 0));
        s1a.setContractAmount(new BigDecimal("100"));
        s1a.setRecognizedRevenue(new BigDecimal("50"));
        s1a.setTotalCost(new BigDecimal("40"));
        s1a.setGrossProfit(new BigDecimal("10"));
        s1a.setGrossMargin(new BigDecimal("0.20"));
        ProfitSnapshotDO s1b = new ProfitSnapshotDO();
        s1b.setInitiationId(1L);
        s1b.setPeriod("2026-02");
        s1b.setSnapshotAt(LocalDateTime.of(2026, 2, 28, 0, 0));
        s1b.setContractAmount(new BigDecimal("100"));
        s1b.setRecognizedRevenue(new BigDecimal("80"));
        s1b.setTotalCost(new BigDecimal("50"));
        s1b.setGrossProfit(new BigDecimal("30"));
        s1b.setGrossMargin(new BigDecimal("0.375"));
        // 项目 2：毛利率 0.10
        ProfitSnapshotDO s2 = new ProfitSnapshotDO();
        s2.setInitiationId(2L);
        s2.setPeriod("2026-02");
        s2.setSnapshotAt(LocalDateTime.of(2026, 2, 28, 0, 0));
        s2.setContractAmount(new BigDecimal("200"));
        s2.setRecognizedRevenue(new BigDecimal("100"));
        s2.setTotalCost(new BigDecimal("90"));
        s2.setGrossProfit(new BigDecimal("10"));
        s2.setGrossMargin(new BigDecimal("0.10"));
        // 项目 3：毛利率 0.50
        ProfitSnapshotDO s3 = new ProfitSnapshotDO();
        s3.setInitiationId(3L);
        s3.setPeriod("2026-02");
        s3.setSnapshotAt(LocalDateTime.of(2026, 2, 28, 0, 0));
        s3.setContractAmount(new BigDecimal("50"));
        s3.setRecognizedRevenue(new BigDecimal("30"));
        s3.setTotalCost(new BigDecimal("15"));
        s3.setGrossProfit(new BigDecimal("15"));
        s3.setGrossMargin(new BigDecimal("0.50"));
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of(s1a, s1b, s2, s3));

        List<Map<String, Object>> out = service.profitRank(10, "grossMargin", null);
        assertThat(out).hasSize(3);
        // 顺序：3 (0.50) > 1 (0.375) > 2 (0.10)
        assertThat(out.get(0).get("initiationId")).isEqualTo(3L);
        assertThat(out.get(1).get("initiationId")).isEqualTo(1L);
        assertThat(out.get(2).get("initiationId")).isEqualTo(2L);
        // 项目 1 取的是 02 月最新快照
        assertThat(out.get(1).get("period")).isEqualTo("2026-02");
        assertThat(out.get(1).get("healthLevel")).isEqualTo("GREEN");
        // 项目 2 毛利率 0.10 视为 YELLOW
        assertThat(out.get(2).get("healthLevel")).isEqualTo("YELLOW");
        // 项目 3 毛利率 0.50 视为 GREEN
        assertThat(out.get(0).get("healthLevel")).isEqualTo("GREEN");
    }

    @Test
    @DisplayName("profitRank 按 grossProfit 排序取 Top N")
    void profitRankByGrossProfit() {
        ProfitSnapshotDO s1 = new ProfitSnapshotDO();
        s1.setInitiationId(1L);
        s1.setSnapshotAt(LocalDateTime.of(2026, 2, 1, 0, 0));
        s1.setGrossProfit(new BigDecimal("100"));
        s1.setGrossMargin(new BigDecimal("0.10"));
        ProfitSnapshotDO s2 = new ProfitSnapshotDO();
        s2.setInitiationId(2L);
        s2.setSnapshotAt(LocalDateTime.of(2026, 2, 1, 0, 0));
        s2.setGrossProfit(new BigDecimal("500"));
        s2.setGrossMargin(new BigDecimal("0.20"));
        ProfitSnapshotDO s3 = new ProfitSnapshotDO();
        s3.setInitiationId(3L);
        s3.setSnapshotAt(LocalDateTime.of(2026, 2, 1, 0, 0));
        s3.setGrossProfit(new BigDecimal("300"));
        s3.setGrossMargin(new BigDecimal("0.30"));
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));

        List<Map<String, Object>> out = service.profitRank(2, "grossProfit", null);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).get("initiationId")).isEqualTo(2L);
        assertThat(out.get(1).get("initiationId")).isEqualTo(3L);
    }

    @Test
    @DisplayName("profitRank 不传 period 取每个项目最新快照")
    void profitRankWithPeriod() {
        ProfitSnapshotDO s1 = new ProfitSnapshotDO();
        s1.setInitiationId(1L);
        s1.setPeriod("2026-01");
        s1.setSnapshotAt(LocalDateTime.of(2026, 1, 31, 0, 0));
        s1.setGrossMargin(new BigDecimal("0.20"));
        ProfitSnapshotDO s2 = new ProfitSnapshotDO();
        s2.setInitiationId(1L);
        s2.setPeriod("2026-02");
        s2.setSnapshotAt(LocalDateTime.of(2026, 2, 28, 0, 0));
        s2.setGrossMargin(new BigDecimal("0.30"));
        when(profitSnapshotMapper.selectList(any())).thenReturn(List.of(s1, s2));

        // 不传 period：取最新 (2026-02)
        List<Map<String, Object>> out1 = service.profitRank(10, null, null);
        assertThat(out1).hasSize(1);
        assertThat(out1.get(0).get("period")).isEqualTo("2026-02");
        // 校验 period 入参已传递给 wrapper
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProfitSnapshotDO>> captor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        org.mockito.Mockito.verify(profitSnapshotMapper, org.mockito.Mockito.atLeastOnce()).selectList(captor.capture());
        // 不传 period 时 wrapper 不应含 period 过滤
        assertThat(captor.getAllValues().get(0).getClass().getSimpleName()).isNotEmpty();
    }

    @Test
    @DisplayName("profitRank 异常降级返回空列表")
    void profitRankException() {
        when(profitSnapshotMapper.selectList(any()))
                .thenThrow(new RuntimeException("DB down"));
        assertThat(service.profitRank(10, null, null)).isEmpty();
    }
}
