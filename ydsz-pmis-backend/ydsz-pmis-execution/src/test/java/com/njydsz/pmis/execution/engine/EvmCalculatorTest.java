package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.enums.EvmAlertLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvmCalculator EVM 挣值计算")
class EvmCalculatorTest {

    @Test
    @DisplayName("理想状态 CPI=1 SPI=1 NORMAL")
    void ideal() {
        EvmCalculator.EVMResult r = EvmCalculator.calculate(
                new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("100"), new BigDecimal("1000"));
        assertThat(r.cpi.doubleValue()).isEqualTo(1.0);
        assertThat(r.spi.doubleValue()).isEqualTo(1.0);
        assertThat(r.cv.signum()).isZero();
        assertThat(r.sv.signum()).isZero();
        assertThat(r.eac).isEqualByComparingTo("1000");
        assertThat(r.vac.signum()).isZero();
        assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.NORMAL);
    }

    @Test
    @DisplayName("超支 CPI<1 红色阈值")
    void overrun() {
        // AC=120, EV=100 → CPI=0.833 < 0.85
        EvmCalculator.EVMResult r = EvmCalculator.calculate(
                new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("120"), new BigDecimal("1000"));
        assertThat(r.cpi.doubleValue()).isLessThan(0.85);
        assertThat(r.cv).isEqualByComparingTo("-20");
        assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.RED);
        assertThat(r.alertReason).contains("CPI");
    }

    @Test
    @DisplayName("进度滞后 SPI<0.95 黄色")
    void scheduleDelay() {
        // PV=120, EV=100 → SPI=0.833 < 0.85 → 实际触发 RED
        EvmCalculator.EVMResult r = EvmCalculator.calculate(
                new BigDecimal("120"), new BigDecimal("100"),
                new BigDecimal("100"), new BigDecimal("1000"));
        assertThat(r.spi.doubleValue()).isLessThan(0.85);
        assertThat(r.sv).isEqualByComparingTo("-20");
        assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.RED);
    }

    @Test
    @DisplayName("EAC BAC/CPI 完工估算")
    void eacCalc() {
        // CPI=0.8 → EAC=1250, VAC=-250
        EvmCalculator.EVMResult r = EvmCalculator.calculate(
                new BigDecimal("100"), new BigDecimal("80"),
                new BigDecimal("100"), new BigDecimal("1000"));
        assertThat(r.cpi.doubleValue()).isEqualTo(0.8);
        assertThat(r.eac).isEqualByComparingTo("1250");
        assertThat(r.vac).isEqualByComparingTo("-250");
    }

    @Test
    @DisplayName("ETC=EAC-AC 完工尚需")
    void etcCalc() {
        EvmCalculator.EVMResult r = EvmCalculator.calculate(
                new BigDecimal("100"), new BigDecimal("50"),
                new BigDecimal("100"), new BigDecimal("1000"));
        // CPI=0.5 → EAC=2000, ETC=2000-100=1900
        assertThat(r.eac).isEqualByComparingTo("2000");
        assertThat(r.etc).isEqualByComparingTo("1900");
    }

    @Test
    @DisplayName("零除保护 AC=0 不报错")
    void zeroDivSafe() {
        EvmCalculator.EVMResult r = EvmCalculator.calculate(
                new BigDecimal("0"), new BigDecimal("0"),
                new BigDecimal("0"), new BigDecimal("0"));
        assertThat(r.cpi.doubleValue()).isEqualTo(1.0);
        assertThat(r.spi.doubleValue()).isEqualTo(1.0);
        assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.NORMAL);
    }

    @Test
    @DisplayName("自定义阈值")
    void customThreshold() {
        // 严格阈值：cpiY=0.99, cpiR=0.95, spiY=0.95, spiR=0.85
        // CPI=0.97 → 介于 0.95~0.99 → YELLOW
        EvmCalculator.EVMResult r = EvmCalculator.calculate(
                new BigDecimal("100"), new BigDecimal("97"),
                new BigDecimal("100"), new BigDecimal("1000"),
                0.99, 0.95, 0.95, 0.85);
        assertThat(r.cpi.doubleValue()).isEqualTo(0.97);
        assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.YELLOW);
    }

    @Test
    @DisplayName("TCPI 计算")
    void tcpiCalc() {
        // BAC=1000, EV=500, AC=400 → TCPI = (1000-500)/(1000-400) = 500/600 = 0.833
        EvmCalculator.EVMResult r = EvmCalculator.calculate(
                new BigDecimal("600"), new BigDecimal("500"),
                new BigDecimal("400"), new BigDecimal("1000"));
        assertThat(r.tcpi.doubleValue()).isEqualTo(0.8333, org.assertj.core.api.Assertions.within(0.001));
    }
}
