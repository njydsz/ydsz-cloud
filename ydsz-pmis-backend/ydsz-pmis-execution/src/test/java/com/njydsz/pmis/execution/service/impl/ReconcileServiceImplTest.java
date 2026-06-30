package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.engine.ReconcileHandler;
import com.njydsz.pmis.execution.engine.ReconcileReport;
import com.njydsz.pmis.execution.engine.ReconcileResult;
import com.njydsz.pmis.execution.enums.ReconcileLevel;
import com.njydsz.pmis.execution.enums.ReconcileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ReconcileServiceImpl 单元测试
 */
@DisplayName("ReconcileServiceImpl 对账服务测试")
class ReconcileServiceImplTest {

    private ReconcileHandler handler;
    private ReconcileServiceImpl service;

    @BeforeEach
    void setUp() {
        handler = mock(ReconcileHandler.class);
        service = new ReconcileServiceImpl(handler);
    }

    @Test
    @DisplayName("reconcileAll 应返回 handler 汇总的报告")
    void reconcileAll_aggregates() {
        List<ReconcileResult> mockResults = List.of(
                ReconcileResult.info(ReconcileType.MISSING_COST_FOR_APPROVED_TIME, "i"),
                ReconcileResult.warn(ReconcileType.DAILY_HOURS_OVERFLOW, "w")
        );
        when(handler.reconcile(eq(1L), any(), any())).thenReturn(mockResults);
        when(handler.buildReport(eq(1L), eq(mockResults))).thenAnswer(inv -> {
            ReconcileReport r = new ReconcileReport();
            r.setInitiationId(inv.getArgument(0));
            @SuppressWarnings("unchecked")
            List<ReconcileResult> arg = (List<ReconcileResult>) inv.getArgument(1);
            r.setTotal(arg.size());
            int info = 0, warn = 0, err = 0;
            for (ReconcileResult x : arg) {
                if (x.getLevel() == ReconcileLevel.INFO) info++;
                else if (x.getLevel() == ReconcileLevel.WARN) warn++;
                else if (x.getLevel() == ReconcileLevel.ERROR) err++;
            }
            r.setInfoCount(info);
            r.setWarnCount(warn);
            r.setErrorCount(err);
            r.setResults(arg);
            return r;
        });

        ReconcileReport report = service.reconcileAll(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(report.getTotal()).isEqualTo(2);
        assertThat(report.getInfoCount()).isEqualTo(1);
        assertThat(report.getWarnCount()).isEqualTo(1);
        assertThat(report.getErrorCount()).isZero();
    }

    @Test
    @DisplayName("reconcileAll 无结果应返回空报告")
    void reconcileAll_empty() {
        when(handler.reconcile(any(), any(), any())).thenReturn(List.of());
        when(handler.buildReport(any(), any())).thenAnswer(inv -> {
            ReconcileReport r = new ReconcileReport();
            r.setInitiationId(inv.getArgument(0));
            r.setTotal(0);
            r.setResults(inv.getArgument(1));
            return r;
        });

        ReconcileReport report = service.reconcileAll(1L, null, null);
        assertThat(report.getTotal()).isZero();
        assertThat(report.getResults()).isEmpty();
    }

    @Test
    @DisplayName("reconcileAll initiationId 为 null 也应正常处理")
    void reconcileAll_nullInitiation() {
        when(handler.reconcile(eq(null), any(), any())).thenReturn(List.of());
        when(handler.buildReport(eq(null), any())).thenAnswer(inv -> {
            ReconcileReport r = new ReconcileReport();
            r.setInitiationId(inv.getArgument(0));
            r.setTotal(0);
            r.setResults(inv.getArgument(1));
            return r;
        });

        ReconcileReport report = service.reconcileAll(null, null, null);
        assertThat(report.getInitiationId()).isNull();
        assertThat(report.getTotal()).isZero();
    }

    @Test
    @DisplayName("reconcileAll 应正确汇总 ERROR 等级")
    void reconcileAll_errorLevel() {
        List<ReconcileResult> mockResults = List.of(
                ReconcileResult.error(ReconcileType.GHOST_COST_FOR_REJECTED_TIME, "e"),
                ReconcileResult.error(ReconcileType.MISSING_COST_FOR_APPROVED_TIME, "e2")
        );
        when(handler.reconcile(any(), any(), any())).thenReturn(mockResults);
        when(handler.buildReport(any(), any())).thenAnswer(inv -> {
            ReconcileReport r = new ReconcileReport();
            r.setInitiationId(inv.getArgument(0));
            @SuppressWarnings("unchecked")
            List<ReconcileResult> arg = (List<ReconcileResult>) inv.getArgument(1);
            r.setTotal(arg.size());
            int err = 0;
            for (ReconcileResult x : arg) {
                if (x.getLevel() == ReconcileLevel.ERROR) err++;
            }
            r.setErrorCount(err);
            r.setResults(arg);
            return r;
        });

        ReconcileReport report = service.reconcileAll(1L, null, null);
        assertThat(report.getErrorCount()).isEqualTo(2);
        assertThat(report.getWarnCount()).isZero();
    }

    @Test
    @DisplayName("checkMissingCost 应合并 missing + ghost 校验")
    void checkMissingCost_combines() {
        List<ReconcileResult> missing = new ArrayList<>();
        missing.add(ReconcileResult.error(ReconcileType.MISSING_COST_FOR_APPROVED_TIME, "m1"));
        when(handler.reconcileMissingCost(1L)).thenReturn(missing);

        List<ReconcileResult> ghost = new ArrayList<>();
        ghost.add(ReconcileResult.error(ReconcileType.GHOST_COST_FOR_REJECTED_TIME, "g1"));
        when(handler.reconcileGhostCost(1L)).thenReturn(ghost);

        List<ReconcileResult> rs = service.checkMissingCost(1L);
        assertThat(rs).hasSize(2);
        assertThat(rs).extracting(r -> r.getType().getCode())
                .containsExactlyInAnyOrder("MISSING_COST_FOR_APPROVED_TIME", "GHOST_COST_FOR_REJECTED_TIME");
    }

    @Test
    @DisplayName("checkMissingCost 两项都为空应返回空列表")
    void checkMissingCost_empty() {
        when(handler.reconcileMissingCost(any())).thenReturn(List.of());
        when(handler.reconcileGhostCost(any())).thenReturn(List.of());

        assertThat(service.checkMissingCost(1L)).isEmpty();
    }

    @Test
    @DisplayName("checkMissingCost initiationId 为 null 也应可调用")
    void checkMissingCost_null() {
        when(handler.reconcileMissingCost(null)).thenReturn(List.of());
        when(handler.reconcileGhostCost(null)).thenReturn(List.of());

        assertThat(service.checkMissingCost(null)).isEmpty();
    }

    @Test
    @DisplayName("checkTimeEntryAnomaly 应合并 daily + weekly + cross-project 校验")
    void checkTimeEntryAnomaly_combines() {
        when(handler.reconcileDailyOverflow(eq(1L), any(), any()))
                .thenReturn(List.of(ReconcileResult.error(ReconcileType.DAILY_HOURS_OVERFLOW, "d1")));
        when(handler.reconcileWeeklyOverload(eq(1L), any(), any()))
                .thenReturn(List.of(ReconcileResult.warn(ReconcileType.WEEKLY_HOURS_OVERLOAD, "w1")));
        when(handler.reconcileCrossProject(eq(1L), any(), any()))
                .thenReturn(List.of(ReconcileResult.warn(ReconcileType.CROSS_PROJECT_CONFLICT, "c1")));

        List<ReconcileResult> rs = service.checkTimeEntryAnomaly(1L, null, null);
        assertThat(rs).hasSize(3);
        long errorCount = rs.stream().filter(r -> r.getLevel() == ReconcileLevel.ERROR).count();
        long warnCount = rs.stream().filter(r -> r.getLevel() == ReconcileLevel.WARN).count();
        assertThat(errorCount).isEqualTo(1);
        assertThat(warnCount).isEqualTo(2);
    }

    @Test
    @DisplayName("checkTimeEntryAnomaly 全空应返回空列表")
    void checkTimeEntryAnomaly_empty() {
        when(handler.reconcileDailyOverflow(any(), any(), any())).thenReturn(List.of());
        when(handler.reconcileWeeklyOverload(any(), any(), any())).thenReturn(List.of());
        when(handler.reconcileCrossProject(any(), any(), any())).thenReturn(List.of());

        assertThat(service.checkTimeEntryAnomaly(1L, null, null)).isEmpty();
    }

    @Test
    @DisplayName("checkTimeEntryAnomaly 应正确传递 from / to")
    void checkTimeEntryAnomaly_passesDateRange() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        when(handler.reconcileDailyOverflow(eq(1L), eq(from), eq(to))).thenReturn(List.of());
        when(handler.reconcileWeeklyOverload(eq(1L), eq(from), eq(to))).thenReturn(List.of());
        when(handler.reconcileCrossProject(eq(1L), eq(from), eq(to))).thenReturn(List.of());

        assertThat(service.checkTimeEntryAnomaly(1L, from, to)).isEmpty();
    }
}
