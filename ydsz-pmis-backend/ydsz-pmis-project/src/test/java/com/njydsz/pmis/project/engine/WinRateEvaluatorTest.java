package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.OpportunityDO;
import com.njydsz.pmis.project.enums.OpportunityStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("商机赢率评估器测试")
class WinRateEvaluatorTest {

    @Test
    @DisplayName("商机为 null 返回 0")
    void shouldReturnZeroWhenOpportunityIsNull() {
        BigDecimal rate = WinRateEvaluator.evaluate(null);
        assertEquals(BigDecimal.ZERO, rate);
    }

    @Test
    @DisplayName("已赢单商机返回 1.0")
    void shouldReturnOneWhenWon() {
        OpportunityDO opp = new OpportunityDO();
        opp.setStatus(OpportunityStatus.WON.getCode());
        opp.setOpportunityCode("OPP-001");
        opp.setOpportunityName("测试商机");

        BigDecimal rate = WinRateEvaluator.evaluate(opp);
        assertEquals(new BigDecimal("1.0000"), rate);
    }

    @Test
    @DisplayName("已输单商机返回 0")
    void shouldReturnZeroWhenLost() {
        OpportunityDO opp = new OpportunityDO();
        opp.setStatus(OpportunityStatus.LOST.getCode());

        BigDecimal rate = WinRateEvaluator.evaluate(opp);
        assertEquals(BigDecimal.ZERO, rate);
    }

    @Test
    @DisplayName("无效商机返回 0")
    void shouldReturnZeroWhenInvalid() {
        OpportunityDO opp = new OpportunityDO();
        opp.setStatus(OpportunityStatus.INVALID.getCode());

        BigDecimal rate = WinRateEvaluator.evaluate(opp);
        assertEquals(BigDecimal.ZERO, rate);
    }

    @Test
    @DisplayName("跟进中商机综合评估 - 无竞争对手、无历史合作")
    void shouldEvaluateFollowingOpportunity() {
        OpportunityDO opp = new OpportunityDO();
        opp.setStatus(OpportunityStatus.FOLLOWING.getCode());
        opp.setLevel("B");
        opp.setOpportunityCode("OPP-001");
        opp.setOpportunityName("测试商机");

        BigDecimal rate = WinRateEvaluator.evaluate(opp, "B", false);
        assertNotNull(rate);
        assertTrue(rate.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(rate.compareTo(BigDecimal.ONE) <= 0);
    }

    @Test
    @DisplayName("完整评估 - A级客户、有历史合作")
    void shouldEvaluateWithHighCreditAndHistory() {
        OpportunityDO opp = new OpportunityDO();
        opp.setStatus(OpportunityStatus.NEGOTIATING.getCode());
        opp.setLevel("A");
        opp.setOpportunityCode("OPP-001");
        opp.setOpportunityName("测试商机");

        BigDecimal rate = WinRateEvaluator.evaluate(opp, "A", true);
        assertNotNull(rate);
        assertTrue(rate.compareTo(new BigDecimal("0.5")) > 0, "A级客户+有历史合作的赢单率应较高");
    }

    @Test
    @DisplayName("完整评估 - D级客户、无历史合作、有竞争对手")
    void shouldEvaluateWithLowCreditAndCompetitors() {
        OpportunityDO opp = new OpportunityDO();
        opp.setStatus(OpportunityStatus.FOLLOWING.getCode());
        opp.setLevel("C");
        opp.setCompetitor("A公司,B公司,C公司");
        opp.setOpportunityCode("OPP-001");
        opp.setOpportunityName("测试商机");

        BigDecimal rate = WinRateEvaluator.evaluate(opp, "D", false);
        assertNotNull(rate);
        assertTrue(rate.compareTo(new BigDecimal("0.5")) < 0, "D级客户+无历史合作+多竞争对手的赢单率应较低");
    }

    @Test
    @DisplayName("完整评估 - null 参数使用默认值")
    void shouldUseDefaultsForNullParams() {
        OpportunityDO opp = new OpportunityDO();
        opp.setStatus(OpportunityStatus.FOLLOWING.getCode());
        opp.setLevel("B");
        opp.setOpportunityCode("OPP-001");
        opp.setOpportunityName("测试商机");

        BigDecimal rate = WinRateEvaluator.evaluate(opp, null, false);
        assertNotNull(rate);
        assertTrue(rate.compareTo(BigDecimal.ZERO) >= 0);
    }
}