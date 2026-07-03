package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.enums.ReconcileLevel;
import com.njydsz.pmis.project.enums.ReconcileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("对账结果测试")
class ReconcileResultTest {

    @Test
    @DisplayName("Builder 构建对账结果")
    void shouldBuildReconcileResult() {
        ReconcileResult result = ReconcileResult.builder()
                .type(ReconcileType.DAILY_HOURS_OVERFLOW)
                .level(ReconcileLevel.ERROR)
                .initiationId(1L)
                .employeeId(100L)
                .sourceId(200L)
                .sourceType("TIME_ENTRY")
                .description("工时超限")
                .actualValue(new BigDecimal("25"))
                .expectedValue(new BigDecimal("24"))
                .drift(new BigDecimal("1"))
                .suggestion("请检查工时填报")
                .build();

        assertNotNull(result);
        assertEquals(ReconcileType.DAILY_HOURS_OVERFLOW, result.getType());
        assertEquals(ReconcileLevel.ERROR, result.getLevel());
        assertEquals(1L, result.getInitiationId());
        assertEquals(100L, result.getEmployeeId());
        assertEquals(200L, result.getSourceId());
        assertEquals("TIME_ENTRY", result.getSourceType());
        assertEquals("工时超限", result.getDescription());
        assertEquals(new BigDecimal("25"), result.getActualValue());
        assertEquals(new BigDecimal("24"), result.getExpectedValue());
        assertEquals(new BigDecimal("1"), result.getDrift());
        assertEquals("请检查工时填报", result.getSuggestion());
    }

    @Test
    @DisplayName("info 工厂方法构建 INFO 级别结果")
    void shouldCreateInfoLevelResult() {
        ReconcileResult result = ReconcileResult.info(ReconcileType.AMOUNT_DRIFT, "金额偏差在容忍范围内");

        assertEquals(ReconcileLevel.INFO, result.getLevel());
        assertEquals(ReconcileType.AMOUNT_DRIFT, result.getType());
        assertEquals("金额偏差在容忍范围内", result.getDescription());
    }

    @Test
    @DisplayName("warn 工厂方法构建 WARN 级别结果")
    void shouldCreateWarnLevelResult() {
        ReconcileResult result = ReconcileResult.warn(ReconcileType.WEEKLY_HOURS_OVERLOAD, "周工时超限");

        assertEquals(ReconcileLevel.WARN, result.getLevel());
        assertEquals(ReconcileType.WEEKLY_HOURS_OVERLOAD, result.getType());
        assertEquals("周工时超限", result.getDescription());
    }

    @Test
    @DisplayName("warn 工厂方法构建 WARN 级别结果（含建议）")
    void shouldCreateWarnLevelResultWithSuggestion() {
        ReconcileResult result = ReconcileResult.warn(
                ReconcileType.CROSS_PROJECT_CONFLICT, "跨项目冲突", "检查工时分摊");

        assertEquals(ReconcileLevel.WARN, result.getLevel());
        assertEquals(ReconcileType.CROSS_PROJECT_CONFLICT, result.getType());
        assertEquals("跨项目冲突", result.getDescription());
        assertEquals("检查工时分摊", result.getSuggestion());
    }

    @Test
    @DisplayName("error 工厂方法构建 ERROR 级别结果")
    void shouldCreateErrorLevelResult() {
        ReconcileResult result = ReconcileResult.error(
                ReconcileType.MISSING_COST_FOR_APPROVED_TIME, "工时缺失成本归集");

        assertEquals(ReconcileLevel.ERROR, result.getLevel());
        assertEquals(ReconcileType.MISSING_COST_FOR_APPROVED_TIME, result.getType());
        assertEquals("工时缺失成本归集", result.getDescription());
    }

    @Test
    @DisplayName("error 工厂方法构建 ERROR 级别结果（含建议）")
    void shouldCreateErrorLevelResultWithSuggestion() {
        ReconcileResult result = ReconcileResult.error(
                ReconcileType.ALLOCATED_BEFORE_APPROVAL, "成本提前分配", "回滚分配状态");

        assertEquals(ReconcileLevel.ERROR, result.getLevel());
        assertEquals(ReconcileType.ALLOCATED_BEFORE_APPROVAL, result.getType());
        assertEquals("成本提前分配", result.getDescription());
        assertEquals("回滚分配状态", result.getSuggestion());
    }
}