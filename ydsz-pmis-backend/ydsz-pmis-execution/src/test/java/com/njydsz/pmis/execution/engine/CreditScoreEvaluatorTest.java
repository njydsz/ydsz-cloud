package com.njydsz.pmis.execution.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreditScoreEvaluator 信用评分引擎")
class CreditScoreEvaluatorTest {

    @Test
    @DisplayName("理想客户 100 分")
    void ideal() {
        int s = CreditScoreEvaluator.score(BigDecimal.ONE, new BigDecimal("100000000"), 20, 0);
        assertThat(s).isGreaterThanOrEqualTo(95);
        assertThat(s).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("新客户 默认满分（无历史）")
    void newCustomer() {
        int s = CreditScoreEvaluator.score(BigDecimal.ONE, BigDecimal.ZERO, 0, 0);
        assertThat(s).isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("逾期多 扣分")
    void overdue() {
        int s = CreditScoreEvaluator.score(new BigDecimal("0.5"),
                new BigDecimal("100000"), 2, 5);
        // 50% 及时率 -> 30pts, 100k -> ~17pts, 2 次合作 -> 3pts, 逾期5次 -> -25 = 25
        assertThat(s).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("结果钳制 0-100")
    void clamp() {
        int s = CreditScoreEvaluator.score(BigDecimal.ONE, new BigDecimal("999999999999"), 999, 0);
        assertThat(s).isLessThanOrEqualTo(100);
        int s2 = CreditScoreEvaluator.score(BigDecimal.ZERO, BigDecimal.ZERO, 0, 999);
        assertThat(s2).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("null 参数安全降级")
    void nullSafe() {
        int s = CreditScoreEvaluator.score(null, null, 0, 0);
        assertThat(s).isGreaterThanOrEqualTo(0);
        assertThat(s).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("及时率主导")
    void timelyDominant() {
        int s1 = CreditScoreEvaluator.score(BigDecimal.ONE, BigDecimal.ZERO, 0, 0);
        int s2 = CreditScoreEvaluator.score(new BigDecimal("0.5"), BigDecimal.ZERO, 0, 0);
        int s3 = CreditScoreEvaluator.score(BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        assertThat(s1).isGreaterThan(s2).isGreaterThan(s3);
    }
}
