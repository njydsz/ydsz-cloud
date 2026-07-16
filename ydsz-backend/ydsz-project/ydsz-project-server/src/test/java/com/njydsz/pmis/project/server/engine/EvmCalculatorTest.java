package com.njydsz.project.server.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.project.domain.enums.EvmAlertLevel;
import com.njydsz.project.server.engine.EvmCalculator.EVMResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvmCalculator 挣值计算引擎单元测试
 *
 * <p>覆盖范围：基础指标（CV/SV/CPI/SPI）、完工预测（EAC/VAC/ETC/TCPI）、
 * 边界值（零值/null/负值）、告警级别、预测完工日期、推荐操作、自定义阈值。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("EvmCalculator 挣值计算引擎单元测试")
class EvmCalculatorTest {

    /** 构造 BigDecimal 输入 */
    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** 使用四参数重载计算 */
    private static EVMResult calc(String pv, String ev, String ac, String bac) {
        return EvmCalculator.calculate(bd(pv), bd(ev), bd(ac), bd(bac));
    }

    /** 使用自定义阈值重载计算 */
    private static EVMResult calc(String pv, String ev, String ac, String bac,
                                  double cpiY, double cpiR, double spiY, double spiR) {
        return EvmCalculator.calculate(bd(pv), bd(ev), bd(ac), bd(bac), cpiY, cpiR, spiY, spiR);
    }

    @Nested
    @DisplayName("基础指标计算（CV/SV/CPI/SPI）")
    class BasicMetricsTests {

        @Test
        @DisplayName("正常值：CV=EV-AC，SV=EV-PV，CPI=EV/AC，SPI=EV/PV")
        void shouldCalculateBasicMetricsForNormalValues() {
            // PV=1000, EV=1200, AC=1000, BAC=2000
            EVMResult r = calc("1000", "1200", "1000", "2000");
            assertThat(r.cv).isEqualByComparingTo(bd("200"));   // 1200 - 1000
            assertThat(r.sv).isEqualByComparingTo(bd("200"));   // 1200 - 1000
            assertThat(r.cpi).isEqualByComparingTo(bd("1.2"));  // 1200 / 1000
            assertThat(r.spi).isEqualByComparingTo(bd("1.2"));  // 1200 / 1000
        }

        @Test
        @DisplayName("非整除：CPI/SPI 保留 4 位小数（HALF_UP）")
        void shouldRoundCpiAndSpiToFourDecimals() {
            // PV=1000, EV=800, AC=900 → CPI=0.8889, SPI=0.8
            EVMResult r = calc("1000", "800", "900", "2000");
            assertThat(r.cpi).isEqualByComparingTo(bd("0.8889")); // 800 / 900 ≈ 0.8889
            assertThat(r.spi).isEqualByComparingTo(bd("0.8"));    // 800 / 1000
            assertThat(r.cpi.scale()).isEqualTo(4);
            assertThat(r.spi.scale()).isEqualTo(4);
        }

        @Test
        @DisplayName("EV=0 时 CPI/SPI 为 0")
        void shouldReturnZeroCpiAndSpiWhenEvIsZero() {
            // PV=1000, EV=0, AC=1000 → CPI=0, SPI=0
            EVMResult r = calc("1000", "0", "1000", "2000");
            assertThat(r.cpi).isEqualByComparingTo(bd("0"));
            assertThat(r.spi).isEqualByComparingTo(bd("0"));
        }
    }

    @Nested
    @DisplayName("完工预测指标（EAC/VAC/ETC/TCPI）")
    class ForecastMetricsTests {

        @Test
        @DisplayName("正常值：EAC=BAC/CPI，VAC=BAC-EAC，ETC=EAC-AC，TCPI=(BAC-EV)/(BAC-AC)")
        void shouldCalculateForecastMetricsForNormalValues() {
            // PV=1000, EV=1200, AC=1000, BAC=2000
            // CPI=1.2 → EAC=2000/1.2=1666.67；VAC=333.33；ETC=666.67；TCPI=800/1000=0.8
            EVMResult r = calc("1000", "1200", "1000", "2000");
            assertThat(r.eac).isEqualByComparingTo(bd("1666.67"));
            assertThat(r.vac).isEqualByComparingTo(bd("333.33"));
            assertThat(r.etc).isEqualByComparingTo(bd("666.67"));
            assertThat(r.tcpi).isEqualByComparingTo(bd("0.8"));
            assertThat(r.eac.scale()).isEqualTo(2);
            assertThat(r.tcpi.scale()).isEqualTo(4);
        }

        @Test
        @DisplayName("CPI=0 时 EAC 回退为 BAC")
        void shouldFallbackEacToBacWhenCpiIsZero() {
            // PV=1000, EV=0, AC=1000 → CPI=0 → EAC=BAC=2000
            EVMResult r = calc("1000", "0", "1000", "2000");
            assertThat(r.eac).isEqualByComparingTo(bd("2000"));
            assertThat(r.vac).isEqualByComparingTo(bd("0"));      // 2000 - 2000
            assertThat(r.etc).isEqualByComparingTo(bd("1000"));   // 2000 - 1000
            assertThat(r.tcpi).isEqualByComparingTo(bd("2"));     // (2000-0)/(2000-1000)
        }

        @Test
        @DisplayName("BAC-AC=0 时 TCPI 回退为 1")
        void shouldFallbackTcpiToOneWhenBacEqualsAc() {
            // PV=1000, EV=500, AC=2000, BAC=2000 → BAC-AC=0 → TCPI=1
            EVMResult r = calc("1000", "500", "2000", "2000");
            assertThat(r.tcpi).isEqualByComparingTo(bd("1"));
        }
    }

    @Nested
    @DisplayName("边界值处理（零值/null/负值）")
    class BoundaryValueTests {

        @Test
        @DisplayName("全部入参为零：CV/SV=0，CPI/SPI 兜底为 1，TCPI 兜底为 1")
        void shouldHandleAllZeroInputs() {
            EVMResult r = calc("0", "0", "0", "0");
            assertThat(r.cv).isEqualByComparingTo(bd("0"));
            assertThat(r.sv).isEqualByComparingTo(bd("0"));
            assertThat(r.cpi).isEqualByComparingTo(bd("1"));  // AC=0 兜底
            assertThat(r.spi).isEqualByComparingTo(bd("1"));  // PV=0 兜底
            assertThat(r.tcpi).isEqualByComparingTo(bd("1")); // BAC-AC=0 兜底
            assertThat(r.eac).isEqualByComparingTo(bd("0"));
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.NORMAL);
        }

        @Test
        @DisplayName("全部入参为 null：按零值处理，结果与全零一致")
        void shouldHandleAllNullInputs() {
            EVMResult r = EvmCalculator.calculate(null, null, null, null);
            assertThat(r.pv).isEqualByComparingTo(bd("0"));
            assertThat(r.ev).isEqualByComparingTo(bd("0"));
            assertThat(r.ac).isEqualByComparingTo(bd("0"));
            assertThat(r.bac).isEqualByComparingTo(bd("0"));
            assertThat(r.cpi).isEqualByComparingTo(bd("1"));
            assertThat(r.spi).isEqualByComparingTo(bd("1"));
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.NORMAL);
        }

        @Test
        @DisplayName("负值入参：公式仍按数学定义计算，告警为 RED")
        void shouldCalculateWithNegativeInputs() {
            // PV=1000, EV=-100, AC=500, BAC=2000
            // CV=-600, SV=-1100, CPI=-0.2, SPI=-0.1
            EVMResult r = calc("1000", "-100", "500", "2000");
            assertThat(r.cv).isEqualByComparingTo(bd("-600"));   // -100 - 500
            assertThat(r.sv).isEqualByComparingTo(bd("-1100"));  // -100 - 1000
            assertThat(r.cpi).isEqualByComparingTo(bd("-0.2"));  // -100 / 500
            assertThat(r.spi).isEqualByComparingTo(bd("-0.1"));  // -100 / 1000
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.RED);
            assertThat(r.forecastCompletionDate).isNull(); // SPI<=0 不预测
        }
    }

    @Nested
    @DisplayName("告警级别判断（CPI/SPI 阈值）")
    class AlertLevelTests {

        @Test
        @DisplayName("CPI/SPI 均达标 → NORMAL，无告警原因")
        void shouldReturnNormalWhenCpiAndSpiAboveThreshold() {
            // EV=AC=PV=1000 → CPI=1.0, SPI=1.0
            EVMResult r = calc("1000", "1000", "1000", "2000");
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.NORMAL);
            assertThat(r.alertReason).isNull();
        }

        @Test
        @DisplayName("CPI 跌破黄色阈值（0.9）→ YELLOW")
        void shouldReturnYellowWhenCpiBelowYellowThreshold() {
            // EV=900, AC=1000, PV=900 → CPI=0.9, SPI=1.0
            EVMResult r = calc("900", "900", "1000", "2000");
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.YELLOW);
            assertThat(r.alertReason).contains("CPI").contains("黄色阈值");
        }

        @Test
        @DisplayName("SPI 跌破黄色阈值（0.9）→ YELLOW")
        void shouldReturnYellowWhenSpiBelowYellowThreshold() {
            // EV=900, AC=900, PV=1000 → CPI=1.0, SPI=0.9
            EVMResult r = calc("1000", "900", "900", "2000");
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.YELLOW);
            assertThat(r.alertReason).contains("SPI").contains("黄色阈值");
        }

        @Test
        @DisplayName("CPI 跌破红色阈值（0.8）→ RED")
        void shouldReturnRedWhenCpiBelowRedThreshold() {
            // EV=800, AC=1000, PV=800 → CPI=0.8, SPI=1.0
            EVMResult r = calc("800", "800", "1000", "2000");
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.RED);
            assertThat(r.alertReason).contains("CPI").contains("红色阈值");
        }

        @Test
        @DisplayName("SPI 跌破红色阈值（0.8）→ RED（CPI 正常仍触发）")
        void shouldReturnRedWhenSpiBelowRedThreshold() {
            // EV=800, AC=800, PV=1000 → CPI=1.0, SPI=0.8
            EVMResult r = calc("1000", "800", "800", "2000");
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.RED);
            assertThat(r.alertReason).contains("SPI").contains("红色阈值");
        }

        @Test
        @DisplayName("CPI/SPI 同时跌破红色阈值 → RED，原因优先描述 CPI")
        void shouldReturnRedWhenBothBelowRedThreshold() {
            // EV=800, AC=1000, PV=1000 → CPI=0.8, SPI=0.8
            EVMResult r = calc("1000", "800", "1000", "2000");
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.RED);
            assertThat(r.alertReason).contains("CPI").contains("红色阈值");
        }
    }

    @Nested
    @DisplayName("预测完工日期（基于 SPI）")
    class ForecastDateTests {

        @Test
        @DisplayName("SPI>=1.0 时不预测，返回 null")
        void shouldReturnNullWhenSpiAboveOrEqualOne() {
            // EV=1200, PV=1000 → SPI=1.2
            EVMResult r = calc("1000", "1200", "1000", "2000");
            assertThat(r.forecastCompletionDate).isNull();
        }

        @Test
        @DisplayName("SPI<=0 时不预测，返回 null")
        void shouldReturnNullWhenSpiBelowOrEqualZero() {
            // EV=0, PV=1000 → SPI=0
            EVMResult r = calc("1000", "0", "500", "2000");
            assertThat(r.forecastCompletionDate).isNull();
        }

        @Test
        @DisplayName("0<SPI<1 且有剩余工作量 → 返回延后的预测日期")
        void shouldReturnForecastDateWhenSpiBetweenZeroAndOneWithRemainingWork() {
            // EV=500, PV=1000 → SPI=0.5；剩余=500 → 预计延后约 30 天
            LocalDate today = LocalDate.now();
            EVMResult r = calc("1000", "500", "500", "2000");
            assertThat(r.forecastCompletionDate)
                    .isNotNull()
                    .isBetween(today.plusDays(29), today.plusDays(31));
        }

        @Test
        @DisplayName("0<SPI<1 但无剩余工作量 → 返回 null")
        void shouldReturnNullWhenNoRemainingWork() {
            // PV=-1000, EV=-500 → SPI=0.5；剩余=PV-EV=-500<=0 → null
            EVMResult r = calc("-1000", "-500", "100", "2000");
            assertThat(r.forecastCompletionDate).isNull();
        }
    }

    @Nested
    @DisplayName("推荐操作")
    class RecommendedActionTests {

        @Test
        @DisplayName("NORMAL → 建议保持")
        void shouldRecommendKeepGoingWhenNormal() {
            EVMResult r = calc("1000", "1000", "1000", "2000");
            assertThat(r.recommendedAction).isEqualTo("项目运行正常，继续保持");
        }

        @Test
        @DisplayName("CPI/SPI 严重告警 → 建议审查成本并加快进度")
        void shouldRecommendCostAndScheduleActionsWhenRed() {
            // EV=800, AC=1000, PV=1000 → CPI=0.8, SPI=0.8
            EVMResult r = calc("1000", "800", "1000", "2000");
            assertThat(r.recommendedAction)
                    .contains("成本严重超支")
                    .contains("进度严重滞后");
        }

        @Test
        @DisplayName("CPI 略低 → 建议关注成本趋势")
        void shouldRecommendCostTrendActionWhenYellow() {
            // EV=900, AC=1000, PV=900 → CPI=0.9, SPI=1.0
            EVMResult r = calc("900", "900", "1000", "2000");
            assertThat(r.recommendedAction).contains("成本略有超支");
        }

        @Test
        @DisplayName("SPI 略低 → 建议加快关键路径")
        void shouldRecommendCriticalPathActionWhenYellow() {
            // EV=900, AC=900, PV=1000 → CPI=1.0, SPI=0.9
            EVMResult r = calc("1000", "900", "900", "2000");
            assertThat(r.recommendedAction).contains("进度略有滞后");
        }
    }

    @Nested
    @DisplayName("自定义阈值")
    class CustomThresholdTests {

        @Test
        @DisplayName("默认阈值为 NORMAL 的值，使用更严格阈值后变为 YELLOW")
        void shouldUseCustomThresholds() {
            // EV=950, AC=1000, PV=1000 → CPI=0.95, SPI=0.95
            // 默认阈值 0.95 → NORMAL；自定义 cpiY=0.99, spiY=0.99 → YELLOW
            EVMResult defaultR = calc("1000", "950", "1000", "2000");
            assertThat(defaultR.alertLevel).isEqualTo(EvmAlertLevel.NORMAL);

            EVMResult customR = calc("1000", "950", "1000", "2000",
                    0.99, 0.90, 0.99, 0.90);
            assertThat(customR.alertLevel).isEqualTo(EvmAlertLevel.YELLOW);
            assertThat(customR.alertReason).contains("CPI").contains("0.99");
        }

        @Test
        @DisplayName("自定义红色阈值：CPI 跌破自定义红色阈值 → RED")
        void shouldReturnRedWithCustomRedThreshold() {
            // EV=950, AC=1000 → CPI=0.95；PV=900 → SPI≈1.06（正常）
            // 自定义 cpiR=0.96 → CPI=0.95 < 0.96 → RED
            EVMResult r = calc("900", "950", "1000", "2000",
                    0.99, 0.96, 0.99, 0.96);
            assertThat(r.alertLevel).isEqualTo(EvmAlertLevel.RED);
            assertThat(r.alertReason).contains("CPI").contains("红色阈值");
        }
    }
}
