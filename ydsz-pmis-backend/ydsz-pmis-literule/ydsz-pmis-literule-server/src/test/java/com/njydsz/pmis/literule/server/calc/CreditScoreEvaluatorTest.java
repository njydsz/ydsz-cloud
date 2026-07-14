package com.njydsz.pmis.literule.server.calc;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CreditScoreEvaluator} 单元测试：覆盖信用评分计算的正常值、零值、null 值与逾期扣分边界。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("客户信用评分引擎 CreditScoreEvaluator 测试")
class CreditScoreEvaluatorTest {

    @Nested
    @DisplayName("正常值场景")
    class NormalCases {

        @Test
        @DisplayName("满分场景：准时率100% + 大额合同 + 10份合同 + 无逾期 = 100分")
        void shouldReturnFullScoreWhenAllMetricsMax() {
            // 各项得分：及时 60 + 规模 25 + 次数 15 = 100
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("1.0"),
                    new BigDecimal("10000000"),
                    10,
                    0);

            assertThat(score).isEqualTo(100);
        }

        @Test
        @DisplayName("一般场景：准时率80% + 10万合同 + 5份合同 + 无逾期 = 64分")
        void shouldReturnGeneralScore() {
            // 及时 48 + 规模 8.68 + 次数 7.50 = 64.18 → 取整 64
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("0.8"),
                    new BigDecimal("100000"),
                    5,
                    0);

            assertThat(score).isEqualTo(64);
        }

        @Test
        @DisplayName("新客户基础分：无任何合作历史默认 30 分")
        void shouldReturnBaseScoreForNewCustomer() {
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("0"),
                    new BigDecimal("0"),
                    0,
                    0);

            assertThat(score).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("零值场景")
    class ZeroCases {

        @Test
        @DisplayName("零准时率但有合作历史：仅合同规模与合作次数得分")
        void shouldReturnScoreWithoutTimelyWhenHasHistory() {
            // 及时 0 + 规模 8.68 + 次数 7.50 = 16.18 → 取整 16
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("0"),
                    new BigDecimal("100000"),
                    5,
                    0);

            assertThat(score).isEqualTo(16);
        }

        @Test
        @DisplayName("有合同数但其余为零：基础分不生效，仅合作次数得分")
        void shouldNotApplyBaseWhenHasContractCount() {
            // 基础分仅对无任何历史的新客户生效；此处 contractCount=1 命中合作次数 1.50
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("0"),
                    new BigDecimal("0"),
                    1,
                    0);

            assertThat(score).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("null 值场景")
    class NullCases {

        @Test
        @DisplayName("onTimeRate 为 null 时按 0 处理")
        void shouldTreatNullOnTimeRateAsZero() {
            int score = CreditScoreEvaluator.score(
                    null,
                    new BigDecimal("0"),
                    0,
                    0);

            // 新客户基础分 30，及时率按 0 计算
            assertThat(score).isEqualTo(30);
        }

        @Test
        @DisplayName("totalContractAmount 为 null 时按 0 处理")
        void shouldTreatNullAmountAsZero() {
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("1.0"),
                    null,
                    10,
                    0);

            // 及时 60 + 规模 0 + 次数 15 = 75
            assertThat(score).isEqualTo(75);
        }
    }

    @Nested
    @DisplayName("逾期扣分与边界场景")
    class OverdueAndBoundaryCases {

        @Test
        @DisplayName("多次逾期扣分：3 次逾期扣 15 分")
        void shouldDeductByOverdueCount() {
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("1.0"),
                    new BigDecimal("10000000"),
                    10,
                    3);

            // 满分 100 - 扣分 15 = 85
            assertThat(score).isEqualTo(85);
        }

        @Test
        @DisplayName("逾期次数超过 12 次封顶扣 60 分")
        void shouldCapOverduePenaltyAt60() {
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("1.0"),
                    new BigDecimal("10000000"),
                    10,
                    20);

            // 满分 100 - 封顶扣分 60 = 40
            assertThat(score).isEqualTo(40);
        }

        @Test
        @DisplayName("信用分下限为 0：逾期过多不会返回负数")
        void shouldClampToZero() {
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("0"),
                    new BigDecimal("0"),
                    0,
                    20);

            // 总分 0 - 扣分 60 = -60 → 钳制为 0
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("信用分上限为 100：超出范围入参被钳制")
        void shouldClampToHundred() {
            // 准时率超出 1.0 属于非法入参，用于验证上限钳制逻辑
            int score = CreditScoreEvaluator.score(
                    new BigDecimal("1.5"),
                    new BigDecimal("10000000"),
                    10,
                    0);

            // 及时 90 + 规模 25 + 次数 15 = 130 → 钳制为 100
            assertThat(score).isEqualTo(100);
        }
    }
}
