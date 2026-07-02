package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContractRiskEvaluator 合同风险评估器单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ContractRiskEvaluator 风险评估器测试")
class ContractRiskEvaluatorTest {

    @Test
    @DisplayName("null 合同返回 LOW")
    void nullContract() {
        assertThat(ContractRiskEvaluator.evaluate(null)).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("小金额短账期 = LOW")
    void lowRisk() {
        ContractDO c = new ContractDO();
        c.setTotalAmount(new BigDecimal("100000"));
        c.setEffectiveDate(LocalDate.of(2026, 1, 1));
        c.setExpireDate(LocalDate.of(2026, 3, 1));
        c.setContractType("FIXED_PRICE");
        c.setPaymentTerms("月结30天");
        c.setCurrency("CNY");
        assertThat(ContractRiskEvaluator.evaluate(c)).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("中等金额 = MEDIUM")
    void mediumRisk() {
        ContractDO c = new ContractDO();
        c.setTotalAmount(new BigDecimal("1000000"));
        c.setContractType("T&M");
        c.setPaymentTerms("NET_60");
        c.setCurrency("CNY");
        // 0.18 + 0 + 0.10 + 0.06 + 0 = 0.34 -> MEDIUM
        assertThat(ContractRiskEvaluator.evaluate(c)).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("大金额长账期外币 = HIGH")
    void highRisk() {
        ContractDO c = new ContractDO();
        c.setTotalAmount(new BigDecimal("10000000"));
        c.setEffectiveDate(LocalDate.of(2026, 1, 1));
        c.setExpireDate(LocalDate.of(2027, 6, 1));
        c.setContractType("OUTSOURCING");
        c.setPaymentTerms("季结90天");
        c.setCurrency("USD");
        // 0.30 + 0.25 + 0.08 + 0.10 + 0.05 = 0.78 -> HIGH
        assertThat(ContractRiskEvaluator.evaluate(c)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("FIXED_PRICE + 长账期 -> MEDIUM")
    void longTermFixed() {
        ContractDO c = new ContractDO();
        c.setTotalAmount(new BigDecimal("100000"));
        c.setEffectiveDate(LocalDate.of(2025, 1, 1));
        c.setExpireDate(LocalDate.of(2026, 6, 1));
        c.setContractType("FIXED_PRICE");
        c.setPaymentTerms("月结30天");
        c.setCurrency("CNY");
        // 0.05 + 0.25 + 0 + 0 + 0 = 0.30 -> MEDIUM
        assertThat(ContractRiskEvaluator.evaluate(c)).isEqualTo(RiskLevel.MEDIUM);
    }
}
