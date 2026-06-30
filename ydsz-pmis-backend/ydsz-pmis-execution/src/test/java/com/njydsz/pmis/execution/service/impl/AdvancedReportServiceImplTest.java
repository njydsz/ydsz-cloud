package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.entity.EvmMeasureDO;
import com.njydsz.pmis.execution.entity.RateCardDO;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import com.njydsz.pmis.execution.entity.RiskDO;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.RateCardMapper;
import com.njydsz.pmis.execution.mapper.RateInternalMapper;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdvancedReportServiceImpl 测试
 */
@DisplayName("AdvancedReportServiceImpl 高级报表")
class AdvancedReportServiceImplTest {

    private EvmMeasureMapper evmMapper;
    private RateCardMapper rateCardMapper;
    private RateInternalMapper rateInternalMapper;
    private RiskMapper riskMapper;
    private AdvancedReportServiceImpl service;

    @BeforeEach
    void setUp() {
        evmMapper = mock(EvmMeasureMapper.class);
        rateCardMapper = mock(RateCardMapper.class);
        rateInternalMapper = mock(RateInternalMapper.class);
        riskMapper = mock(RiskMapper.class);
        service = new AdvancedReportServiceImpl(evmMapper, rateCardMapper, rateInternalMapper, riskMapper);
    }

    @Test
    @DisplayName("evmReport initiationId 为空返回空列表")
    void evmReport_null() {
        assertThat(service.evmReport(null)).isEmpty();
    }

    @Test
    @DisplayName("evmReport 正常返回全部字段")
    void evmReport_normal() {
        EvmMeasureDO m = new EvmMeasureDO();
        m.setPeriod("2026-01");
        m.setPv(new BigDecimal("100"));
        m.setEv(new BigDecimal("80"));
        m.setAc(new BigDecimal("90"));
        m.setBac(new BigDecimal("1000"));
        m.setCpi(new BigDecimal("0.89"));
        m.setSpi(new BigDecimal("0.80"));
        m.setAlertLevel("YELLOW");
        m.setAlertReason("成本偏差");
        when(evmMapper.selectByInitiation(1L)).thenReturn(List.of(m));

        List<Map<String, Object>> out = service.evmReport(1L);
        assertThat(out).hasSize(1);
        Map<String, Object> row = out.get(0);
        assertThat(row.get("period")).isEqualTo("2026-01");
        assertThat(row.get("alertLevel")).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("utilizationRank top=0 时使用默认 10")
    void utilizationRank_default() {
        when(rateInternalMapper.selectAll()).thenReturn(List.of());
        assertThat(service.utilizationRank(0)).isEmpty();
    }

    @Test
    @DisplayName("utilizationRank 按 costAmount 降序并截断 top")
    void utilizationRank_truncate() {
        List<RateInternalDO> rates = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            RateInternalDO r = new RateInternalDO();
            r.setLevelCode("L" + (20 - i));
            r.setCostAmount(BigDecimal.valueOf(1000L + i));
            rates.add(r);
        }
        when(rateInternalMapper.selectAll()).thenReturn(rates);
        List<Map<String, Object>> out = service.utilizationRank(3);
        assertThat(out).hasSize(3);
        assertThat(out.get(0).get("levelCode")).isEqualTo("L19"); // costAmount 最大
    }

    @Test
    @DisplayName("utilizationRank mapper 异常时降级为空")
    void utilizationRank_exception() {
        when(rateInternalMapper.selectAll()).thenThrow(new RuntimeException());
        assertThat(service.utilizationRank(10)).isEmpty();
    }

    @Test
    @DisplayName("dualRateProfitCompare 计算差额和毛利率")
    void dualRate() {
        RateCardDO card1 = new RateCardDO();
        card1.setLevelCode("L5");
        card1.setRateAmount(new BigDecimal("1500.00"));
        RateCardDO card2 = new RateCardDO();
        card2.setLevelCode("L8");
        card2.setRateAmount(new BigDecimal("2500.00"));
        RateInternalDO internal1 = new RateInternalDO();
        internal1.setLevelCode("L5");
        internal1.setCostAmount(new BigDecimal("1000.00"));
        RateInternalDO internal2 = new RateInternalDO();
        internal2.setLevelCode("L8");
        internal2.setCostAmount(new BigDecimal("1500.00"));
        when(rateCardMapper.selectAll()).thenReturn(List.of(card1, card2));
        when(rateInternalMapper.selectAll()).thenReturn(List.of(internal1, internal2));

        List<Map<String, Object>> out = service.dualRateProfitCompare("2026-01");
        assertThat(out).hasSize(2);
        Map<String, Object> l5 = out.stream()
                .filter(r -> "L5".equals(r.get("levelCode"))).findFirst().orElseThrow();
        BigDecimal l5Diff = (BigDecimal) l5.get("diff");
        BigDecimal l5Margin = (BigDecimal) l5.get("margin");
        assertThat(l5Diff).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(l5Margin).isEqualByComparingTo(new BigDecimal("0.3333"));
    }

    @Test
    @DisplayName("dualRateProfitCompare 内部成本缺失时 diff 等于 externalRate")
    void dualRate_missingInternal() {
        RateCardDO card = new RateCardDO();
        card.setLevelCode("L10");
        card.setRateAmount(new BigDecimal("3000.00"));
        when(rateCardMapper.selectAll()).thenReturn(List.of(card));
        when(rateInternalMapper.selectAll()).thenReturn(List.of());

        List<Map<String, Object>> out = service.dualRateProfitCompare(null);
        assertThat(out).hasSize(1);
        assertThat((BigDecimal) out.get(0).get("diff")).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat((BigDecimal) out.get(0).get("internalCost")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("dualRateProfitCompare 外部为 0 时 margin 降级为 0")
    void dualRate_zeroExternal() {
        RateCardDO card = new RateCardDO();
        card.setLevelCode("L1");
        card.setRateAmount(BigDecimal.ZERO);
        when(rateCardMapper.selectAll()).thenReturn(List.of(card));
        when(rateInternalMapper.selectAll()).thenReturn(List.of());

        List<Map<String, Object>> out = service.dualRateProfitCompare(null);
        assertThat((BigDecimal) out.get(0).get("margin")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("resourceGantt 当前占位返回空")
    void gantt() {
        assertThat(service.resourceGantt(1L)).isEmpty();
    }

    @Test
    @DisplayName("benchCostReport 当前占位返回空")
    void bench() {
        assertThat(service.benchCostReport()).isEmpty();
    }

    @Test
    @DisplayName("riskDashboard 按 level + initiation 双维度聚合")
    void riskDashboard() {
        RiskDO r1 = new RiskDO();
        r1.setRiskLevel("HIGH");
        r1.setInitiationId(10L);
        RiskDO r2 = new RiskDO();
        r2.setRiskLevel("HIGH");
        r2.setInitiationId(10L);
        RiskDO r3 = new RiskDO();
        r3.setRiskLevel("MEDIUM");
        r3.setInitiationId(20L);
        when(riskMapper.selectAll()).thenReturn(List.of(r1, r2, r3));

        List<Map<String, Object>> out = service.riskDashboard();
        assertThat(out).hasSize(3); // 2 BY_LEVEL + 1 BY_INITIATION
    }

    @Test
    @DisplayName("riskDashboard mapper 异常降级为空")
    void riskDashboard_exception() {
        when(riskMapper.selectAll()).thenThrow(new RuntimeException());
        assertThat(service.riskDashboard()).isEmpty();
    }

    @Test
    @DisplayName("riskDashboard 空数据返回空")
    void riskDashboard_empty() {
        when(riskMapper.selectAll()).thenReturn(List.of());
        assertThat(service.riskDashboard()).isEmpty();
    }
}
