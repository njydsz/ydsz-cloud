package com.njydsz.pmis.execution.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CockpitKpiVO DTO 测试
 */
@DisplayName("CockpitKpiVO 经营驾驶舱 KPI VO")
class CockpitKpiVOTest {

    @Test
    @DisplayName("基础属性读写")
    void getterSetter() {
        CockpitKpiVO v = new CockpitKpiVO();
        v.setActiveProjects(8);
        v.setTotalContractAmount(new BigDecimal("1000"));
        v.setConfirmedRevenue(new BigDecimal("800"));
        v.setTotalCost(new BigDecimal("500"));
        v.setGrossProfit(new BigDecimal("300"));
        v.setGrossMargin(new BigDecimal("0.3750"));
        v.setEvmRedCount(1);
        v.setEvmYellowCount(2);
        v.setEvmGreenCount(5);
        v.setBenchIdleCost(new BigDecimal("50"));
        v.setAvgBillableUtilization(new BigDecimal("0.78"));
        v.setDimensionBreakdown(List.of(Map.of("type", "DEPT")));

        assertThat(v.getActiveProjects()).isEqualTo(8);
        assertThat(v.getTotalContractAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(v.getGrossMargin()).isEqualByComparingTo(new BigDecimal("0.3750"));
        assertThat(v.getDimensionBreakdown()).hasSize(1);
    }

    @Test
    @DisplayName("默认值为 null / 0")
    void defaults() {
        CockpitKpiVO v = new CockpitKpiVO();
        assertThat(v.getActiveProjects()).isNull();
        assertThat(v.getTotalContractAmount()).isNull();
        assertThat(v.getDimensionBreakdown()).isNull();
    }

    @Test
    @DisplayName("DrillDownDTO 基本属性")
    void drillDownDto() {
        CockpitDrillDownDTO d = new CockpitDrillDownDTO();
        d.setDimension("DEPT");
        d.setValue("10");
        assertThat(d.getDimension()).isEqualTo("DEPT");
        assertThat(d.getValue()).isEqualTo("10");
    }
}
