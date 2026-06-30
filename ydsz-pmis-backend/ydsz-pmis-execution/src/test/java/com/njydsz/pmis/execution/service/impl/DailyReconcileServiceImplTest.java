package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.entity.DailyReconcileDO;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import com.njydsz.pmis.execution.mapper.DailyReconcileMapper;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import com.njydsz.pmis.execution.mapper.PaymentMapper;
import com.njydsz.pmis.execution.mapper.ProfitSnapshotMapper;
import com.njydsz.pmis.execution.mapper.RevenueMapper;
import com.njydsz.pmis.execution.mapper.TimeEntryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DailyReconcileServiceImpl 每日对账服务测试
 */
@DisplayName("DailyReconcileServiceImpl 每日对账")
class DailyReconcileServiceImplTest {

    private DailyReconcileMapper reconcileMapper;
    private CostAllocationMapper costMapper;
    private RevenueMapper revenueMapper;
    private InvoiceMapper invoiceMapper;
    private PaymentMapper paymentMapper;
    private TimeEntryMapper timeEntryMapper;
    private ProfitSnapshotMapper profitSnapshotMapper;
    private DailyReconcileServiceImpl service;

    @BeforeEach
    void setUp() {
        reconcileMapper = mock(DailyReconcileMapper.class);
        costMapper = mock(CostAllocationMapper.class);
        revenueMapper = mock(RevenueMapper.class);
        invoiceMapper = mock(InvoiceMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        timeEntryMapper = mock(TimeEntryMapper.class);
        profitSnapshotMapper = mock(ProfitSnapshotMapper.class);
        service = new DailyReconcileServiceImpl(reconcileMapper, costMapper, revenueMapper,
                invoiceMapper, paymentMapper, timeEntryMapper, profitSnapshotMapper);
    }

    @Test
    @DisplayName("classify 差异 < 1% → OK")
    void classify_ok() {
        assertThat(service.classify(100d, 100.5d, 0.01, 0.05)).isEqualTo("OK");
    }

    @Test
    @DisplayName("classify 差异 1%-5% → WARN")
    void classify_warn() {
        assertThat(service.classify(100d, 103d, 0.01, 0.05)).isEqualTo("WARN");
    }

    @Test
    @DisplayName("classify 差异 >= 5% → ERROR")
    void classify_error() {
        assertThat(service.classify(100d, 110d, 0.01, 0.05)).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("classify expected=0 且 actual=0 → OK")
    void classify_zeroBoth() {
        assertThat(service.classify(0d, 0d, 0.01, 0.05)).isEqualTo("OK");
    }

    @Test
    @DisplayName("classify expected=0 且 actual>0 → WARN")
    void classify_zeroExpected() {
        assertThat(service.classify(0d, 0.5d, 0.01, 0.05)).isEqualTo("WARN");
    }

    @Test
    @DisplayName("upsert 新增")
    void upsert_insert() {
        when(reconcileMapper.selectUnique(any(), any(), any())).thenReturn(null);
        // expected=100, actual=103, 差异 3% → WARN
        service.upsert(LocalDate.of(2026, 6, 1), "COST", 1L, 100d, 103d, "test");
        ArgumentCaptor<DailyReconcileDO> cap = ArgumentCaptor.forClass(DailyReconcileDO.class);
        verify(reconcileMapper).insert(cap.capture());
        DailyReconcileDO saved = cap.getValue();
        assertThat(saved.getReconcileType()).isEqualTo("COST");
        assertThat(saved.getExpectedAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getActualAmount()).isEqualByComparingTo("103.00");
        assertThat(saved.getDiffAmount()).isEqualByComparingTo("3.00");
        assertThat(saved.getStatus()).isEqualTo("WARN");
        assertThat(saved.getInitiationId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("upsert 已存在 → updateById")
    void upsert_update() {
        DailyReconcileDO exist = new DailyReconcileDO();
        exist.setId(99L);
        when(reconcileMapper.selectUnique(any(), any(), any())).thenReturn(exist);
        service.upsert(LocalDate.of(2026, 6, 1), "COST", 1L, 100d, 100d, "test");
        ArgumentCaptor<DailyReconcileDO> cap = ArgumentCaptor.forClass(DailyReconcileDO.class);
        verify(reconcileMapper).updateById(cap.capture());
        verify(reconcileMapper, never()).insert(any(DailyReconcileDO.class));
        assertThat(cap.getValue().getId()).isEqualTo(99L);
        assertThat(cap.getValue().getStatus()).isEqualTo("OK");
    }

    @Test
    @DisplayName("upsert 缺日期 / 缺类型 → 不落库")
    void upsert_validation() {
        service.upsert(null, "COST", 1L, 100d, 100d, "x");
        service.upsert(LocalDate.now(), null, 1L, 100d, 100d, "x");
        verify(reconcileMapper, never()).insert(any(DailyReconcileDO.class));
        verify(reconcileMapper, never()).updateById(any(DailyReconcileDO.class));
    }

    @Test
    @DisplayName("queryByDateRange 异常时返回空列表")
    void queryByDateRange_exception() {
        when(reconcileMapper.selectByDateRange(any(), any(), any()))
                .thenThrow(new RuntimeException("DB down"));
        List<Map<String, Object>> out = service.queryByDateRange(null, null, null);
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("queryByDateRange 正常返回并 toMap")
    void queryByDateRange_ok() {
        DailyReconcileDO d = new DailyReconcileDO();
        d.setId(1L);
        d.setReconcileDate(LocalDate.of(2026, 6, 1));
        d.setReconcileType("COST");
        d.setExpectedAmount(new BigDecimal("100.00"));
        d.setActualAmount(new BigDecimal("105.00"));
        d.setDiffAmount(new BigDecimal("5.00"));
        d.setStatus("WARN");
        when(reconcileMapper.selectByDateRange(any(), any(), any())).thenReturn(List.of(d));
        List<Map<String, Object>> out = service.queryByDateRange(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "WARN");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).get("reconcileType")).isEqualTo("COST");
        assertThat(out.get(0).get("status")).isEqualTo("WARN");
    }

    @Test
    @DisplayName("aggregateStatus 异常时返回空列表")
    void aggregateStatus_exception() {
        when(reconcileMapper.aggregateByStatus(any(), any()))
                .thenThrow(new RuntimeException("DB down"));
        List<Map<String, Object>> out = service.aggregateStatus(null, null);
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("aggregateStatus 正常返回")
    void aggregateStatus_ok() {
        when(reconcileMapper.aggregateByStatus(any(), any())).thenReturn(List.of(
                Map.of("reconcile_date", LocalDate.of(2026, 6, 1), "status", "OK", "cnt", 5L)
        ));
        List<Map<String, Object>> out = service.aggregateStatus(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(out).hasSize(1);
    }

    @Test
    @DisplayName("runDaily 全部 OK")
    void runDaily_allOk() {
        when(costMapper.sumAllAmount()).thenReturn(new BigDecimal("1000"));
        when(revenueMapper.sumAll()).thenReturn(new BigDecimal("2000"));
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(new BigDecimal("800"));
        when(paymentMapper.sumAllocatedAmount()).thenReturn(new BigDecimal("600"));
        when(costMapper.sumByCostType("LABOR")).thenReturn(new BigDecimal("500"));
        when(timeEntryMapper.sumApprovedHours()).thenReturn(new BigDecimal("5"));
        when(profitSnapshotMapper.sumAll()).thenReturn(new BigDecimal("1000"));
        when(reconcileMapper.selectUnique(any(), any(), any())).thenReturn(null);
        int n = service.runDaily(LocalDate.of(2026, 6, 1));
        // 6 个维度：COST/REVENUE/INVOICE/PAYMENT/LABOR/PROFIT
        assertThat(n).isEqualTo(6);
        verify(reconcileMapper, org.mockito.Mockito.times(6))
                .insert(any(DailyReconcileDO.class));
    }

    @Test
    @DisplayName("runDaily 收入和成本差异大 → ERROR")
    void runDaily_profitError() {
        when(costMapper.sumAllAmount()).thenReturn(new BigDecimal("1000"));
        when(revenueMapper.sumAll()).thenReturn(new BigDecimal("2000"));
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(new BigDecimal("2000"));
        when(paymentMapper.sumAllocatedAmount()).thenReturn(new BigDecimal("2000"));
        when(costMapper.sumByCostType("LABOR")).thenReturn(new BigDecimal("1000"));
        when(timeEntryMapper.sumApprovedHours()).thenReturn(new BigDecimal("10"));
        // 利润快照: 现算=1000, 快照=0, 差异 100% → ERROR
        when(profitSnapshotMapper.sumAll()).thenReturn(new BigDecimal("0"));
        when(reconcileMapper.selectUnique(any(), any(), any())).thenReturn(null);
        ArgumentCaptor<DailyReconcileDO> cap = ArgumentCaptor.forClass(DailyReconcileDO.class);
        int n = service.runDaily(LocalDate.of(2026, 6, 1));
        assertThat(n).isEqualTo(6);
        verify(reconcileMapper, org.mockito.Mockito.times(6)).insert(cap.capture());
        // 找到一条 PROFIT 类型且 status=ERROR
        boolean hasError = cap.getAllValues().stream()
                .anyMatch(d -> "PROFIT".equals(d.getReconcileType()) && "ERROR".equals(d.getStatus()));
        assertThat(hasError).isTrue();
    }

    @Test
    @DisplayName("runDaily null 日期 默认今天")
    void runDaily_nullDate() {
        when(costMapper.sumAllAmount()).thenReturn(BigDecimal.ZERO);
        when(revenueMapper.sumAll()).thenReturn(BigDecimal.ZERO);
        when(invoiceMapper.sumInvoicedAmount()).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.sumAllocatedAmount()).thenReturn(BigDecimal.ZERO);
        when(costMapper.sumByCostType(eq("LABOR"))).thenReturn(BigDecimal.ZERO);
        when(timeEntryMapper.sumApprovedHours()).thenReturn(BigDecimal.ZERO);
        when(profitSnapshotMapper.sumAll()).thenReturn(BigDecimal.ZERO);
        when(reconcileMapper.selectUnique(any(), any(), any())).thenReturn(null);
        int n = service.runDaily(null);
        assertThat(n).isEqualTo(6);
    }

    @Test
    @DisplayName("runDaily mapper 异常被 safeSum 吞掉，不影响主流程")
    void runDaily_mapperException() {
        when(costMapper.sumAllAmount()).thenThrow(new RuntimeException("DB down"));
        when(revenueMapper.sumAll()).thenThrow(new RuntimeException("DB down"));
        when(invoiceMapper.sumInvoicedAmount()).thenThrow(new RuntimeException("DB down"));
        when(paymentMapper.sumAllocatedAmount()).thenThrow(new RuntimeException("DB down"));
        when(costMapper.sumByCostType("LABOR")).thenThrow(new RuntimeException("DB down"));
        when(timeEntryMapper.sumApprovedHours()).thenThrow(new RuntimeException("DB down"));
        when(profitSnapshotMapper.sumAll()).thenThrow(new RuntimeException("DB down"));
        when(reconcileMapper.selectUnique(any(), any(), any())).thenReturn(null);
        int n = service.runDaily(LocalDate.of(2026, 6, 1));
        assertThat(n).isEqualTo(6); // 6 个维度都跑完，没崩
    }
}
