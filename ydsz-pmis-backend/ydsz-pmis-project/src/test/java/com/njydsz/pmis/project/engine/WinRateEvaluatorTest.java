package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.OpportunityDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WinRateEvaluator 赢率评估器测试")
class WinRateEvaluatorTest {

    @Test
    @DisplayName("WON + A 级 + A 客户 + 历史合作 = 1.0")
    void maxScore() {
        OpportunityDO o = base("WON", "A", null);
        BigDecimal r = WinRateEvaluator.evaluate(o, "A", true);
        assertThat(r).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("LOST/INVALID 必为 0")
    void lost() {
        assertThat(WinRateEvaluator.evaluate(base("LOST", "A", null), "A", true))
                .isEqualByComparingTo("0.0000");
        assertThat(WinRateEvaluator.evaluate(base("INVALID", "A", null), "A", true))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("FOLLOWING 阶段 0.30 权重")
    void following() {
        BigDecimal r = WinRateEvaluator.evaluate(base("FOLLOWING", "C", null), "D", false);
        // 0.30*0.30 + 0.40*0.15 + 0.10*0.20 + 0.70*0.15 + 0.40*0.20 = 0.09+0.06+0.02+0.105+0.08 = 0.355
        assertThat(r).isEqualByComparingTo("0.3550");
    }

    @Test
    @DisplayName("竞争家数影响竞争项")
    void competition() {
        OpportunityDO one = base("QUOTED", "B", "A公司");
        OpportunityDO three = base("QUOTED", "B", "A公司,B公司,C公司");
        BigDecimal r1 = WinRateEvaluator.evaluate(one, "B", false);
        BigDecimal r3 = WinRateEvaluator.evaluate(three, "B", false);
        assertThat(r3).isLessThan(r1);
    }

    @Test
    @DisplayName("null 商机返回 0")
    void nullOpp() {
        assertThat(WinRateEvaluator.evaluate(null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("评级范围 0-1")
    void clamp() {
        for (int i = 0; i < 20; i++) {
            BigDecimal r = WinRateEvaluator.evaluate(base("NEGOTIATING", "A", "x"), "A", true);
            assertThat(r).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        }
    }

    private OpportunityDO base(String status, String level, String competitor) {
        OpportunityDO o = new OpportunityDO();
        o.setStatus(status);
        o.setLevel(level);
        o.setCompetitor(competitor);
        return o;
    }
}
