package com.njydsz.pmis.sales.server.engine;

import com.njydsz.pmis.sales.domain.entity.ContractDO;
import com.njydsz.pmis.sales.domain.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 合同风险评估引擎（增强版）
 *
 * <p>多因子评估：
 * <ul>
 *   <li>合同金额（25%）：金额越大风险越高</li>
 *   <li>账期（20%）：账期越长风险越高</li>
 *   <li>合同类型（8%）：T&M/外包风险略高</li>
 *   <li>付款条款（12%）：长账期/低预付比例风险高</li>
 *   <li>币种（5%）：非人民币汇率风险</li>
 *   <li>条款风险识别（20%）：自动扫描条款中的高风险关键词</li>
 *   <li>客户信用（10%）：低信用客户风险高</li>
 * </ul>
 *
 * <p>增强点：返回详细的风险因子列表，支持前端展示具体风险点。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
public class ContractRiskEvaluator {

    /** 高风险合同金额阈值（500 万） */
    private static final BigDecimal HIGH_AMOUNT = new BigDecimal("5000000");
    /** 中风险合同金额阈值（50 万） */
    private static final BigDecimal MEDIUM_AMOUNT = new BigDecimal("500000");

    /** 高风险条款关键词 */
    private static final String[] HIGH_RISK_KEYWORDS = {
        "无限责任", "连带责任", "违约金", "罚金", "自动续约", "独家锁定",
        "不可抗力免责", "单方终止", "价格调整", "汇率风险自担"
    };

    /** 中风险条款关键词 */
    private static final String[] MEDIUM_RISK_KEYWORDS = {
        "验收标准", "知识产权归属", "保密期限", "竞业限制", "分期付款",
        "里程碑", "质保金", "保证金"
    };

    /**
     * 评估合同风险等级。
     *
     * @param c 合同实体，为 null 返回 LOW
     * @return 风险等级（LOW/MEDIUM/HIGH）
     */
    public static RiskLevel evaluate(ContractDO c) {
        return evaluateWithDetails(c).level();
    }

    /**
     * 评估合同风险等级并返回详细风险因子。
     *
     * @param c 合同实体
     * @return 风险评估结果，包含等级、总分和风险因子列表
     */
    public static RiskAssessment evaluateWithDetails(ContractDO c) {
        if (c == null) {
            return new RiskAssessment(RiskLevel.LOW, 0.0, List.of("合同实体为空"));
        }
        double score = 0.0;
        List<String> factors = new ArrayList<>();

        // 1) 合同金额（25%）
        if (c.getTotalAmount() != null) {
            if (c.getTotalAmount().compareTo(HIGH_AMOUNT) >= 0) {
                score += 0.25;
                factors.add("合同金额≥500万，高风险");
            } else if (c.getTotalAmount().compareTo(MEDIUM_AMOUNT) >= 0) {
                score += 0.15;
                factors.add("合同金额50万-500万，中风险");
            } else {
                score += 0.04;
            }
        }

        // 2) 账期（20%）
        long days = 0;
        if (c.getEffectiveDate() != null && c.getExpireDate() != null) {
            days = ChronoUnit.DAYS.between(c.getEffectiveDate(), c.getExpireDate());
        }
        if (days > 365) {
            score += 0.20;
            factors.add("账期超1年（" + days + "天），高风险");
        } else if (days > 180) {
            score += 0.12;
            factors.add("账期超半年（" + days + "天），中风险");
        } else if (days > 90) {
            score += 0.06;
        }

        // 3) 合同类型（8%）
        if ("T&M".equalsIgnoreCase(c.getContractType())) {
            score += 0.08;
            factors.add("T&M 合同类型，成本不可控风险");
        }
        if ("OUTSOURCING".equalsIgnoreCase(c.getContractType())) {
            score += 0.06;
            factors.add("外包合同类型，交付质量风险");
        }

        // 4) 付款条款（12%）
        if (c.getPaymentTerms() != null) {
            String pt = c.getPaymentTerms().toLowerCase();
            if (pt.contains("90") || pt.contains("季结")) {
                score += 0.12;
                factors.add("付款账期≥90天，资金压力风险");
            } else if (pt.contains("60") || pt.contains("60天")) {
                score += 0.07;
                factors.add("付款账期60天，中等资金压力");
            }
        }

        // 5) 币种（5%）
        if (c.getCurrency() != null && !"CNY".equalsIgnoreCase(c.getCurrency())) {
            score += 0.05;
            factors.add("非人民币币种(" + c.getCurrency() + ")，汇率风险");
        }

        // 6) 条款风险识别（20%）—— 扫描条款关键词
        String termsText = extractTermsText(c);
        if (termsText != null && !termsText.isBlank()) {
            int highRiskHits = 0;
            int mediumRiskHits = 0;
            for (String kw : HIGH_RISK_KEYWORDS) {
                if (termsText.contains(kw)) {
                    highRiskHits++;
                    factors.add("高风险条款: \"" + kw + "\"");
                }
            }
            for (String kw : MEDIUM_RISK_KEYWORDS) {
                if (termsText.contains(kw)) {
                    mediumRiskHits++;
                }
            }
            double clauseScore = Math.min(0.20, highRiskHits * 0.08 + mediumRiskHits * 0.03);
            score += clauseScore;
        }

        // 7) 客户信用（10%）—— 从 riskNotes 中提取信用等级标记
        String creditFromNotes = extractCreditFromNotes(c.getRiskNotes());
        if (creditFromNotes != null) {
            if ("D".equals(creditFromNotes)) {
                score += 0.10;
                factors.add("客户信用D级，违约风险高");
            } else if ("C".equals(creditFromNotes)) {
                score += 0.06;
                factors.add("客户信用C级，需关注");
            }
        }

        RiskLevel level;
        if (score >= 0.6) level = RiskLevel.HIGH;
        else if (score >= 0.3) level = RiskLevel.MEDIUM;
        else level = RiskLevel.LOW;

        log.debug("[ContractRisk] 合同 {} 评分={} -> {}，风险因子: {}",
                c.getContractCode(), score, level, factors);
        return new RiskAssessment(level, score, factors);
    }

    /**
     * 从合同实体中提取条款文本用于关键词扫描。
     *
     * @param c 合同实体
     * @return 拼接的条款文本
     */
    private static String extractTermsText(ContractDO c) {
        StringBuilder sb = new StringBuilder();
        if (c.getPaymentTerms() != null) sb.append(c.getPaymentTerms()).append(" ");
        if (c.getBillingCycle() != null) sb.append(c.getBillingCycle()).append(" ");
        if (c.getRiskNotes() != null) sb.append(c.getRiskNotes()).append(" ");
        if (c.getRemark() != null) sb.append(c.getRemark());
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 从风险说明中提取客户信用等级标记。
     * <p>格式约定：riskNotes 中包含 [CREDIT:X] 标记，X 为 A/B/C/D。
     *
     * @param riskNotes 风险说明字段
     * @return 信用等级（A/B/C/D），无标记返回 null
     */
    private static String extractCreditFromNotes(String riskNotes) {
        if (riskNotes == null || riskNotes.isBlank()) return null;
        int idx = riskNotes.indexOf("[CREDIT:");
        if (idx < 0 || idx + 8 >= riskNotes.length()) return null;
        char c = riskNotes.charAt(idx + 8);
        if (c == 'A' || c == 'B' || c == 'C' || c == 'D') return String.valueOf(c);
        return null;
    }

    /**
     * 风险评估结果
     *
     * @param level    风险等级
     * @param score    总风险得分（0-1）
     * @param factors  风险因子列表（可展示给用户）
     */
    public record RiskAssessment(RiskLevel level, double score, List<String> factors) {}
}
