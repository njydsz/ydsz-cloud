package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.entity.CostAllocationDO;
import com.njydsz.pmis.execution.entity.TimeEntryDO;
import com.njydsz.pmis.execution.enums.CostType;
import com.njydsz.pmis.execution.enums.ReconcileLevel;
import com.njydsz.pmis.execution.enums.ReconcileType;
import com.njydsz.pmis.execution.enums.TimeEntryStatus;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ReconcileHandler 单元测试
 */
@DisplayName("ReconcileHandler 对账引擎测试")
class ReconcileHandlerTest {

    private TimeEntryMapper timeMapper;
    private CostAllocationMapper costMapper;
    private ReconcileHandler handler;

    @BeforeEach
    void setUp() {
        timeMapper = mock(TimeEntryMapper.class);
        costMapper = mock(CostAllocationMapper.class);
        handler = new ReconcileHandler(timeMapper, costMapper);
    }

    // ----------------------------------------------------------------
    // 1. 漏算
    // ----------------------------------------------------------------

    @Test
    @DisplayName("APPROVED 工时无对应成本 -> 漏算 ERROR")
    void missingCost_detect() {
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "APPROVED");
        when(timeMapper.selectByInitiationAndDateRange(eq(1L), any(), any()))
                .thenReturn(List.of(e));
        when(costMapper.selectByInitiationAndPeriod(eq(1L), any())).thenReturn(List.of());

        List<ReconcileResult> rs = handler.reconcileMissingCost(1L);
        assertThat(rs).hasSize(1);
        assertThat(rs.get(0).getType()).isEqualTo(ReconcileType.MISSING_COST_FOR_APPROVED_TIME);
        assertThat(rs.get(0).getLevel()).isEqualTo(ReconcileLevel.ERROR);
    }

    @Test
    @DisplayName("APPROVED 工时存在成本 -> 无告警")
    void missingCost_none() {
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "APPROVED");
        CostAllocationDO c = cost(99L, 10L, "LABOR", new BigDecimal("800"));
        when(timeMapper.selectByInitiationAndDateRange(eq(1L), any(), any()))
                .thenReturn(List.of(e));
        when(costMapper.selectByInitiationAndPeriod(eq(1L), any())).thenReturn(List.of(c));

        assertThat(handler.reconcileMissingCost(1L)).isEmpty();
    }

    // ----------------------------------------------------------------
    // 2. 幽灵成本
    // ----------------------------------------------------------------

    @Test
    @DisplayName("REJECTED 工时存在成本 -> 幽灵 ERROR")
    void ghostCost_detect() {
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "REJECTED");
        CostAllocationDO c = cost(99L, 10L, "LABOR", new BigDecimal("800"));
        when(costMapper.selectByInitiationAndPeriod(eq(1L), any())).thenReturn(List.of(c));
        when(timeMapper.selectById(10L)).thenReturn(e);

        List<ReconcileResult> rs = handler.reconcileGhostCost(1L);
        assertThat(rs).hasSize(1);
        assertThat(rs.get(0).getType()).isEqualTo(ReconcileType.GHOST_COST_FOR_REJECTED_TIME);
    }

    @Test
    @DisplayName("APPROVED 工时存在成本 -> 不算幽灵")
    void ghostCost_none() {
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "APPROVED");
        CostAllocationDO c = cost(99L, 10L, "LABOR", new BigDecimal("800"));
        when(costMapper.selectByInitiationAndPeriod(eq(1L), any())).thenReturn(List.of(c));
        when(timeMapper.selectById(10L)).thenReturn(e);

        assertThat(handler.reconcileGhostCost(1L)).isEmpty();
    }

    // ----------------------------------------------------------------
    // 3. 单日工时超 24h
    // ----------------------------------------------------------------

    @Test
    @DisplayName("单人单日 25h -> ERROR")
    void dailyOverflow_detect() {
        TimeEntryDO e1 = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("15"), "APPROVED");
        TimeEntryDO e2 = entry(11L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("10"), "APPROVED");
        when(timeMapper.selectByInitiationAndDateRange(eq(1L), any(), any()))
                .thenReturn(List.of(e1, e2));

        List<ReconcileResult> rs = handler.reconcileDailyOverflow(1L, null, null);
        assertThat(rs).hasSize(1);
        assertThat(rs.get(0).getType()).isEqualTo(ReconcileType.DAILY_HOURS_OVERFLOW);
        assertThat(rs.get(0).getActualValue()).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("单人单日 24h -> 边界不触发(使用 > 比较)")
    void dailyOverflow_boundaryOk() {
        TimeEntryDO e1 = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("12"), "APPROVED");
        TimeEntryDO e2 = entry(11L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("12"), "APPROVED");
        when(timeMapper.selectByInitiationAndDateRange(eq(1L), any(), any()))
                .thenReturn(List.of(e1, e2));

        assertThat(handler.reconcileDailyOverflow(1L, null, null)).isEmpty();
    }

    // ----------------------------------------------------------------
    // 4. 单周工时超 60h
    // ----------------------------------------------------------------

    @Test
    @DisplayName("单周 65h -> WARN")
    void weeklyOverload_detect() {
        // 同一周 7 天各 10h = 70h
        List<TimeEntryDO> list = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            list.add(entry(100L + i, 200L,
                    LocalDate.of(2026, 1, 5 + i), new BigDecimal("10"), "APPROVED"));
        }
        when(timeMapper.selectByInitiationAndDateRange(eq(1L), any(), any())).thenReturn(list);

        List<ReconcileResult> rs = handler.reconcileWeeklyOverload(1L, null, null);
        assertThat(rs).hasSize(1);
        assertThat(rs.get(0).getType()).isEqualTo(ReconcileType.WEEKLY_HOURS_OVERLOAD);
        assertThat(rs.get(0).getLevel()).isEqualTo(ReconcileLevel.WARN);
    }

    // ----------------------------------------------------------------
    // 5. 跨项目冲突
    // ----------------------------------------------------------------

    @Test
    @DisplayName("同员工同日跨 2 项目 -> WARN")
    void crossProject_detect() {
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "APPROVED");
        when(timeMapper.selectByInitiationAndDateRange(eq(1L), any(), any())).thenReturn(List.of(e));
        Map<String, Object> m1 = new HashMap<>();
        m1.put("initiationId", 1L);
        Map<String, Object> m2 = new HashMap<>();
        m2.put("initiationId", 2L);
        when(timeMapper.detectCrossProject(eq(100L), eq(LocalDate.of(2026, 1, 1))))
                .thenReturn(List.of(m1, m2));

        List<ReconcileResult> rs = handler.reconcileCrossProject(1L, null, null);
        assertThat(rs).hasSize(1);
        assertThat(rs.get(0).getType()).isEqualTo(ReconcileType.CROSS_PROJECT_CONFLICT);
    }

    @Test
    @DisplayName("同员工同日单项目 -> 无告警")
    void crossProject_none() {
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "APPROVED");
        when(timeMapper.selectByInitiationAndDateRange(eq(1L), any(), any())).thenReturn(List.of(e));
        Map<String, Object> m1 = new HashMap<>();
        m1.put("initiationId", 1L);
        when(timeMapper.detectCrossProject(eq(100L), eq(LocalDate.of(2026, 1, 1))))
                .thenReturn(List.of(m1));

        assertThat(handler.reconcileCrossProject(1L, null, null)).isEmpty();
    }

    // ----------------------------------------------------------------
    // 6. 金额漂移
    // ----------------------------------------------------------------

    @Test
    @DisplayName("成本金额与工时×费率偏差 > 1元 -> WARN")
    void amountDrift_detect() {
        // 8h = 1 人天 × 800 = 800 期望
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "APPROVED");
        e.setDays(new BigDecimal("1"));
        CostAllocationDO c = cost(99L, 10L, "LABOR", new BigDecimal("500")); // 偏差 300
        when(timeMapper.selectByInitiationAndDateRange(eq(1L), any(), any())).thenReturn(List.of(e));
        when(costMapper.selectByInitiationAndPeriod(eq(1L), any())).thenReturn(List.of(c));

        List<ReconcileResult> rs = handler.reconcileAmountDrift(1L, null, null);
        assertThat(rs).hasSize(1);
        assertThat(rs.get(0).getType()).isEqualTo(ReconcileType.AMOUNT_DRIFT);
    }

    @Test
    @DisplayName("成本金额与工时×费率偏差 ≤ 1元 -> 无告警")
    void amountDrift_none() {
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "APPROVED");
        e.setDays(new BigDecimal("1"));
        CostAllocationDO c = cost(99L, 10L, "LABOR", new BigDecimal("800.5")); // 偏差 0.5
        when(timeMapper.selectByInitiationAndDateRange(eq(1L), any(), any())).thenReturn(List.of(e));
        when(costMapper.selectByInitiationAndPeriod(eq(1L), any())).thenReturn(List.of(c));

        assertThat(handler.reconcileAmountDrift(1L, null, null)).isEmpty();
    }

    // ----------------------------------------------------------------
    // 7. 分配超前
    // ----------------------------------------------------------------

    @Test
    @DisplayName("allocated=1 但工时未 APPROVED -> ERROR")
    void allocatedBeforeApproval_detect() {
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "SUBMITTED");
        CostAllocationDO c = cost(99L, 10L, "LABOR", new BigDecimal("800"));
        c.setAllocated(1);
        c.setSourceType("TIME_ENTRY");
        when(costMapper.selectByInitiationAndPeriod(eq(1L), any())).thenReturn(List.of(c));
        when(timeMapper.selectById(10L)).thenReturn(e);

        List<ReconcileResult> rs = handler.reconcileAllocatedBeforeApproval(1L);
        assertThat(rs).hasSize(1);
        assertThat(rs.get(0).getType()).isEqualTo(ReconcileType.ALLOCATED_BEFORE_APPROVAL);
    }

    @Test
    @DisplayName("allocated=1 且工时 APPROVED -> 无告警")
    void allocatedBeforeApproval_none() {
        TimeEntryDO e = entry(10L, 100L, LocalDate.of(2026, 1, 1), new BigDecimal("8"), "APPROVED");
        CostAllocationDO c = cost(99L, 10L, "LABOR", new BigDecimal("800"));
        c.setAllocated(1);
        c.setSourceType("TIME_ENTRY");
        when(costMapper.selectByInitiationAndPeriod(eq(1L), any())).thenReturn(List.of(c));
        when(timeMapper.selectById(10L)).thenReturn(e);

        assertThat(handler.reconcileAllocatedBeforeApproval(1L)).isEmpty();
    }

    // ----------------------------------------------------------------
    // Report 汇总
    // ----------------------------------------------------------------

    @Test
    @DisplayName("buildReport 汇总各等级计数")
    void buildReport_aggregates() {
        List<ReconcileResult> rs = List.of(
                ReconcileResult.info(ReconcileType.MISSING_COST_FOR_APPROVED_TIME, "i"),
                ReconcileResult.warn(ReconcileType.DAILY_HOURS_OVERFLOW, "w"),
                ReconcileResult.warn(ReconcileType.WEEKLY_HOURS_OVERLOAD, "w"),
                ReconcileResult.error(ReconcileType.GHOST_COST_FOR_REJECTED_TIME, "e")
        );
        ReconcileReport r = handler.buildReport(1L, rs);
        assertThat(r.getTotal()).isEqualTo(4);
        assertThat(r.getInfoCount()).isEqualTo(1);
        assertThat(r.getWarnCount()).isEqualTo(2);
        assertThat(r.getErrorCount()).isEqualTo(1);
        assertThat(r.getCountByType()).containsKey("DAILY_HOURS_OVERFLOW");
    }

    @Test
    @DisplayName("initiationId 为空时返回空结果")
    void nullInitiation() {
        assertThat(handler.reconcileMissingCost(null)).isEmpty();
        assertThat(handler.reconcileGhostCost(null)).isEmpty();
        assertThat(handler.reconcileDailyOverflow(null, null, null)).isEmpty();
        assertThat(handler.reconcileWeeklyOverload(null, null, null)).isEmpty();
        assertThat(handler.reconcileCrossProject(null, null, null)).isEmpty();
        assertThat(handler.reconcileAmountDrift(null, null, null)).isEmpty();
        assertThat(handler.reconcileAllocatedBeforeApproval(null)).isEmpty();
    }

    // ----------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------

    private TimeEntryDO entry(Long id, Long empId, LocalDate date, BigDecimal hours, String status) {
        TimeEntryDO e = new TimeEntryDO();
        e.setId(id);
        e.setEmployeeId(empId);
        e.setEmployeeName("员工" + empId);
        e.setEntryDate(date);
        e.setInitiationId(1L);
        e.setHours(hours);
        e.setStatus(status);
        return e;
    }

    private CostAllocationDO cost(Long id, Long sourceId, String type, BigDecimal amount) {
        CostAllocationDO c = new CostAllocationDO();
        c.setId(id);
        c.setSourceId(sourceId);
        c.setSourceType("TIME_ENTRY");
        c.setCostType(type);
        c.setInitiationId(1L);
        c.setAmount(amount);
        c.setAllocated(0);
        return c;
    }
}
