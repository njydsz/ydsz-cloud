package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;

/**
 * 合同风险评估引擎
 *
 * <p>多因子评估：
 * <ul>
 *   <li>合同金额（30%）：金额越大风险越高</li>
 *   <li>账期（25%）：账期越长风险越高</li>
 *   <li>客户/项目阶段（20%）</li>
 *   <li>付款条款（15%）</li>
 *   <li>税率/币种（10%）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class ContractRiskEvaluator {

    private static final BigDecimal HIGH_AMOUNT = new BigDecimal("5000000");
    private static final BigDecimal MEDIUM_AMOUNT = new BigDecimal("500000");

    public static RiskLevel evaluate(ContractDO c) {
        if (c == null) return RiskLevel.LOW;
        double score = 0.0;

        // 1) 合同金额
        if (c.getTotalAmount() != null) {
            if (c.getTotalAmount().compareTo(HIGH_AMOUNT) >= 0) score += 0.30;
            else if (c.getTotalAmount().compareTo(MEDIUM_AMOUNT) >= 0) score += 0.18;
            else score += 0.05;
        }

        // 2) 账期
        long days = 0;
        if (c.getEffectiveDate() != null && c.getExpireDate() != null) {
            days = ChronoUnit.DAYS.between(c.getEffectiveDate(), c.getExpireDate());
        }
        if (days > 365) score += 0.25;
        else if (days > 180) score += 0.15;
        else if (days > 90) score += 0.08;

        // 3) 合同类型 - T&M/外包 风险略高
        if ("T&M".equalsIgnoreCase(c.getContractType())) score += 0.10;
        if ("OUTSOURCING".equalsIgnoreCase(c.getContractType())) score += 0.08;

        // 4) 付款条款 - 月结长账期加分
        if (c.getPaymentTerms() != null) {
            String pt = c.getPaymentTerms().toLowerCase();
            if (pt.contains("90") || pt.contains("季结")) score += 0.10;
            else if (pt.contains("60") || pt.contains("60天")) score += 0.06;
        }

        // 5) 币种 - 非人民币
        if (c.getCurrency() != null && !"CNY".equalsIgnoreCase(c.getCurrency())) {
            score += 0.05;
        }

        RiskLevel level;
        if (score >= 0.6) level = RiskLevel.HIGH;
        else if (score >= 0.3) level = RiskLevel.MEDIUM;
        else level = RiskLevel.LOW;
        log.debug("[ContractRisk] 合同 {} 评分={} -> {}", c.getContractCode(), score, level);
        return level;
    }
}
