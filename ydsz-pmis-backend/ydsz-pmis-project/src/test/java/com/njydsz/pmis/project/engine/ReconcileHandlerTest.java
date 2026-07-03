package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.CostAllocationDO;
import com.njydsz.pmis.project.entity.TimeEntryDO;
import com.njydsz.pmis.project.enums.CostType;
import com.njydsz.pmis.project.enums.ReconcileLevel;
import com.njydsz.pmis.project.enums.ReconcileType;
import com.njydsz.pmis.project.enums.TimeEntryStatus;
import com.njydsz.pmis.project.mapper.CostAllocationMapper;
import com.njydsz.pmis.project.mapper.TimeEntryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("财务-工时数据交叉对账引擎测试")
class ReconcileHandlerTest {

    @Mock
    private TimeEntryMapper timeEntryMapper;

    @Mock
    private CostAllocationMapper costAllocationMapper;

    @InjectMocks
    private ReconcileHandler reconcileHandler;

    @Test
    @DisplayName("缺成本归集检查 - 工时已 APPROVED 但缺失成本归集")
    void shouldDetectMissingCost() {
        TimeEntryDO entry = new TimeEntryDO();
        entry.setId(1L);
        entry.setStatus(TimeEntryStatus.APPROVED.getCode());
        entry.setHours(new BigDecimal("8"));
        entry.setEmployeeId(100L);
        entry.setEmployeeName("张三");

        when(timeEntryMapper.selectByInitiationAndDateRange(eq(1L), any(), any()))
                .thenReturn(List.of(entry));
        when(costAllocationMapper.selectByInitiationAndPeriod(eq(1L), any()))
                .thenReturn(List.of());

        List<ReconcileResult> results = reconcileHandler.reconcileMissingCost(1L);
        assertFalse(results.isEmpty());
        assertEquals(ReconcileType.MISSING_COST_FOR_APPROVED_TIME, results.get(0).getType());
        assertEquals(ReconcileLevel.ERROR, results.get(0).getLevel());
    }

    @Test
    @DisplayName("缺成本归集检查 - 已存在成本归集时不报错")
    void shouldNotDetectMissingCostWhenCostExists() {
        TimeEntryDO entry = new TimeEntryDO();
        entry.setId(1L);
        entry.setStatus(TimeEntryStatus.APPROVED.getCode());
        entry.setHours(new BigDecimal("8"));
        entry.setEmployeeId(100L);

        CostAllocationDO cost = new CostAllocationDO();
        cost.setSourceId(1L);
        cost.setCostType(CostType.LABOR.getCode());

        when(timeEntryMapper.selectByInitiationAndDateRange(eq(1L), any(), any()))
                .thenReturn(List.of(entry));
        when(costAllocationMapper.selectByInitiationAndPeriod(eq(1L), any()))
                .thenReturn(List.of(cost));

        List<ReconcileResult> results = reconcileHandler.reconcileMissingCost(1L);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("幽灵成本检查 - 工时已 REJECTED 但存在成本归集")
    void shouldDetectGhostCost() {
        CostAllocationDO cost = new CostAllocationDO();
        cost.setId(10L);
        cost.setSourceId(1L);
        cost.setCostType(CostType.LABOR.getCode());
        cost.setAmount(new BigDecimal("800"));

        TimeEntryDO entry = new TimeEntryDO();
        entry.setId(1L);
        entry.setStatus(TimeEntryStatus.REJECTED.getCode());
        entry.setEmployeeId(100L);

        when(costAllocationMapper.selectByInitiationAndPeriod(eq(1L), any()))
                .thenReturn(List.of(cost));
        when(timeEntryMapper.selectById(1L)).thenReturn(entry);

        List<ReconcileResult> results = reconcileHandler.reconcileGhostCost(1L);
        assertFalse(results.isEmpty());
        assertEquals(ReconcileType.GHOST_COST_FOR_REJECTED_TIME, results.get(0).getType());
        assertEquals(ReconcileLevel.ERROR, results.get(0).getLevel());
    }

    @Test
    @DisplayName("buildReport - 构建对账报告")
    void shouldBuildReport() {
        ReconcileResult r1 = ReconcileResult.error(
                ReconcileType.MISSING_COST_FOR_APPROVED_TIME, "缺失成本");
        ReconcileResult r2 = ReconcileResult.warn(
                ReconcileType.WEEKLY_HOURS_OVERLOAD, "周工时超限");
        ReconcileResult r3 = ReconcileResult.info(
                ReconcileType.AMOUNT_DRIFT, "金额偏差");

        ReconcileReport report = reconcileHandler.buildReport(1L, List.of(r1, r2, r3));

        assertNotNull(report);
        assertEquals(1L, report.getInitiationId());
        assertEquals(3, report.getTotal());
        assertEquals(1, report.getInfoCount());
        assertEquals(1, report.getWarnCount());
        assertEquals(1, report.getErrorCount());
        assertNotNull(report.getCountByType());
        assertNotNull(report.getCheckAt());
    }

    @Test
    @DisplayName("buildReport - null 结果列表")
    void shouldBuildReportWithNullResults() {
        ReconcileReport report = reconcileHandler.buildReport(1L, null);

        assertNotNull(report);
        assertEquals(0, report.getTotal());
        assertEquals(0, report.getInfoCount());
        assertEquals(0, report.getWarnCount());
        assertEquals(0, report.getErrorCount());
    }

    @Test
    @DisplayName("safeAdd - 安全相加")
    void shouldSafelyAddBigDecimals() {
        assertEquals(new BigDecimal("30"), ReconcileHandler.safeAdd(new BigDecimal("10"), new BigDecimal("20")));
        assertEquals(new BigDecimal("10"), ReconcileHandler.safeAdd(new BigDecimal("10"), null));
        assertEquals(new BigDecimal("20"), ReconcileHandler.safeAdd(null, new BigDecimal("20")));
    }

    @Test
    @DisplayName("缺成本归集检查 - initiationId 为 null 返回空列表")
    void shouldReturnEmptyWhenInitiationIdIsNull() {
        List<ReconcileResult> results = reconcileHandler.reconcileMissingCost(null);
        assertTrue(results.isEmpty());
    }
}