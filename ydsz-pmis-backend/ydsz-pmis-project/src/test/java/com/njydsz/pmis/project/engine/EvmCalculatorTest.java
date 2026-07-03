package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.enums.EvmAlertLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EVM 挣值计算引擎测试")
class EvmCalculatorTest {

    @Test
    @DisplayName("计算完整 EVM 指标 - 正常场景")
    void shouldCalculateAllEvmMetrics() {
        BigDecimal pv = new BigDecimal("100000");
        BigDecimal ev = new BigDecimal("95000");
        BigDecimal ac = new BigDecimal("90000");
        BigDecimal bac = new BigDecimal("200000");

        EvmCalculator.EVMResult result = EvmCalculator.calculate(pv, ev, ac, bac);

        assertNotNull(result);
        assertEquals(new BigDecimal("5000"), result.cv); // EV - AC = 95000 - 90000
        assertEquals(new BigDecimal("-5000"), result.sv); // EV - PV = 95000 - 100000
        assertTrue(result.cpi.compareTo(BigDecimal.ONE) > 0, "CPI > 1 表示节约");
        assertTrue(result.spi.compareTo(BigDecimal.ONE) < 0, "SPI < 1 表示滞后");
        assertNotNull(result.eac);
        assertNotNull(result.vac);
        assertNotNull(result.etc);
        assertNotNull(result.tcpi);
    }

    @Test
    @DisplayName("CPI 跌破红色阈值触发 RED 告警")
    void shouldTriggerRedAlertWhenCpiBelowRedThreshold() {
        BigDecimal pv = new BigDecimal("100000");
        BigDecimal ev = new BigDecimal("80000"); // CPI = 0.8 < 0.85
        BigDecimal ac = new BigDecimal("100000");
        BigDecimal bac = new BigDecimal("200000");

        EvmCalculator.EVMResult result = EvmCalculator.calculate(pv, ev, ac, bac);

        assertEquals(EvmAlertLevel.RED, result.alertLevel);
        assertNotNull(result.alertReason);
        assertTrue(result.alertReason.contains("CPI"));
    }

    @Test
    @DisplayName("CPI 跌破黄色阈值触发 YELLOW 告警")
    void shouldTriggerYellowAlertWhenCpiBelowYellowThreshold() {
        BigDecimal pv = new BigDecimal("100000");
        BigDecimal ev = new BigDecimal("90000"); // CPI = 0.9 < 0.95
        BigDecimal ac = new BigDecimal("100000");
        BigDecimal bac = new BigDecimal("200000");

        EvmCalculator.EVMResult result = EvmCalculator.calculate(pv, ev, ac, bac);

        assertEquals(EvmAlertLevel.YELLOW, result.alertLevel);
    }

    @Test
    @DisplayName("CPI/SPI 均在阈值以上为 NORMAL")
    void shouldBeNormalWhenAllIndicesAboveThresholds() {
        BigDecimal pv = new BigDecimal("100000");
        BigDecimal ev = new BigDecimal("100000");
        BigDecimal ac = new BigDecimal("100000");
        BigDecimal bac = new BigDecimal("200000");

        EvmCalculator.EVMResult result = EvmCalculator.calculate(pv, ev, ac, bac);

        assertEquals(EvmAlertLevel.NORMAL, result.alertLevel);
    }

    @Test
    @DisplayName("null 参数应转为 0 处理")
    void shouldHandleNullParameters() {
        EvmCalculator.EVMResult result = EvmCalculator.calculate(null, null, null, null);

        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.pv);
        assertEquals(BigDecimal.ZERO, result.ev);
        assertEquals(BigDecimal.ZERO, result.ac);
        assertEquals(BigDecimal.ZERO, result.bac);
    }

    @Test
    @DisplayName("AC 为 0 时 CPI 兜底为 1.0")
    void shouldDefaultCpiToOneWhenAcIsZero() {
        BigDecimal pv = new BigDecimal("100000");
        BigDecimal ev = new BigDecimal("50000");
        BigDecimal ac = BigDecimal.ZERO;
        BigDecimal bac = new BigDecimal("200000");

        EvmCalculator.EVMResult result = EvmCalculator.calculate(pv, ev, ac, bac);

        assertEquals(new BigDecimal("1.0000"), result.cpi);
    }

    @Test
    @DisplayName("自定义阈值计算")
    void shouldUseCustomThresholds() {
        BigDecimal pv = new BigDecimal("100000");
        BigDecimal ev = new BigDecimal("90000");
        BigDecimal ac = new BigDecimal("100000");
        BigDecimal bac = new BigDecimal("200000");

        EvmCalculator.EVMResult result = EvmCalculator.calculate(
                pv, ev, ac, bac, 0.80, 0.70, 0.80, 0.70);

        // CPI = 0.9, SPI = 0.9, 自定义阈值 0.80/0.70，所以应该是 NORMAL
        assertEquals(EvmAlertLevel.NORMAL, result.alertLevel);
    }
}