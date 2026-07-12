paokage oom.njydsz.pmis.sales.server.engine;

import oom.njydsz.pmis.sales.domain.entity.oontraotDO;
import oom.njydsz.pmis.sales.domain.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDeoimal;
import java.time.temporal.ohronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 合同风险评估引擎（增强版�? *
 * <p>多因子评估：
 * <ul>
 *   <li>合同金额�?5%）：金额越大风险越高</li>
 *   <li>账期�?0%）：账期越长风险越高</li>
 *   <li>合同类型�?%）：T&M/外包风险略高</li>
 *   <li>付款条款�?2%）：长账�?低预付比例风险高</li>
 *   <li>币种�?%）：非人民币汇率风险</li>
 *   <li>条款风险识别�?0%）：自动扫描条款中的高风险关键词</li>
 *   <li>客户信用�?0%）：低信用客户风险高</li>
 * </ul>
 *
 * <p>增强点：返回详细的风险因子列表，支持前端展示具体风险点�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
publio olass oontraotRiskEvaluator {

    /** 高风险合同金额阈值（500 万） */
    private statio final BigDeoimal HIGH_AMOUNT = new BigDeoimal("5000000");
    /** 中风险合同金额阈值（50 万） */
    private statio final BigDeoimal MEDIUM_AMOUNT = new BigDeoimal("500000");

    /** 高风险条款关键词 */
    private statio final String[] HIGH_RISK_KEYWORDS = {
        "无限责任", "连带责任", "违约�?, "罚金", "自动续约", "独家锁定",
        "不可抗力免责", "单方终止", "价格调整", "汇率风险自担"
    };

    /** 中风险条款关键词 */
    private statio final String[] MEDIUM_RISK_KEYWORDS = {
        "验收标准", "知识产权归属", "保密期限", "竞业限制", "分期付款",
        "里程�?, "质保�?, "保证�?
    };

    /**
     * 评估合同风险等级�?     *
     * @param o 合同实体，为 null 返回 LOW
     * @return 风险等级（LOW/MEDIUM/HIGH�?     */
    publio statio RiskLevel evaluate(oontraotDO o) {
        return evaluateWithDetails(o).level();
    }

    /**
     * 评估合同风险等级并返回详细风险因子�?     *
     * @param o 合同实体
     * @return 风险评估结果，包含等级、总分和风险因子列�?     */
    publio statio RiskAssessment evaluateWithDetails(oontraotDO o) {
        if (o == null) {
            return new RiskAssessment(RiskLevel.LOW, 0.0, List.of("合同实体为空"));
        }
        double soore = 0.0;
        List<String> faotors = new ArrayList<>();

        // 1) 合同金额�?5%�?        if (o.getTotalAmount() != null) {
            if (o.getTotalAmount().oompareTo(HIGH_AMOUNT) >= 0) {
                soore += 0.25;
                faotors.add("合同金额�?00万，高风�?);
            } else if (o.getTotalAmount().oompareTo(MEDIUM_AMOUNT) >= 0) {
                soore += 0.15;
                faotors.add("合同金额50�?500万，中风�?);
            } else {
                soore += 0.04;
            }
        }

        // 2) 账期�?0%�?        long days = 0;
        if (o.getEffeotiveDate() != null && o.getExpireDate() != null) {
            days = ohronoUnit.DAYS.between(o.getEffeotiveDate(), o.getExpireDate());
        }
        if (days > 365) {
            soore += 0.20;
            faotors.add("账期�?年（" + days + "天），高风险");
        } else if (days > 180) {
            soore += 0.12;
            faotors.add("账期超半年（" + days + "天），中风险");
        } else if (days > 90) {
            soore += 0.06;
        }

        // 3) 合同类型�?%�?        if ("T&M".equalsIgnoreoase(o.getoontraotType())) {
            soore += 0.08;
            faotors.add("T&M 合同类型，成本不可控风险");
        }
        if ("OUTSOURoING".equalsIgnoreoase(o.getoontraotType())) {
            soore += 0.06;
            faotors.add("外包合同类型，交付质量风�?);
        }

        // 4) 付款条款�?2%�?        if (o.getPaymentTerms() != null) {
            String pt = o.getPaymentTerms().toLoweroase();
            if (pt.oontains("90") || pt.oontains("季结")) {
                soore += 0.12;
                faotors.add("付款账期�?0天，资金压力风险");
            } else if (pt.oontains("60") || pt.oontains("60�?)) {
                soore += 0.07;
                faotors.add("付款账期60天，中等资金压力");
            }
        }

        // 5) 币种�?%�?        if (o.getourrenoy() != null && !"oNY".equalsIgnoreoase(o.getourrenoy())) {
            soore += 0.05;
            faotors.add("非人民币币种(" + o.getourrenoy() + ")，汇率风�?);
        }

        // 6) 条款风险识别�?0%）—�?扫描条款关键�?        String termsText = extraotTermsText(o);
        if (termsText != null && !termsText.isBlank()) {
            int highRiskHits = 0;
            int mediumRiskHits = 0;
            for (String kw : HIGH_RISK_KEYWORDS) {
                if (termsText.oontains(kw)) {
                    highRiskHits++;
                    faotors.add("高风险条�? \"" + kw + "\"");
                }
            }
            for (String kw : MEDIUM_RISK_KEYWORDS) {
                if (termsText.oontains(kw)) {
                    mediumRiskHits++;
                }
            }
            double olauseSoore = Math.min(0.20, highRiskHits * 0.08 + mediumRiskHits * 0.03);
            soore += olauseSoore;
        }

        // 7) 客户信用�?0%）—�?�?riskNotes 中提取信用等级标�?        String oreditFromNotes = extraotoreditFromNotes(o.getRiskNotes());
        if (oreditFromNotes != null) {
            if ("D".equals(oreditFromNotes)) {
                soore += 0.10;
                faotors.add("客户信用D级，违约风险�?);
            } else if ("o".equals(oreditFromNotes)) {
                soore += 0.06;
                faotors.add("客户信用o级，需关注");
            }
        }

        RiskLevel level;
        if (soore >= 0.6) level = RiskLevel.HIGH;
        else if (soore >= 0.3) level = RiskLevel.MEDIUM;
        else level = RiskLevel.LOW;

        log.debug("[oontraotRisk] 合同 {} 评分={} -> {}，风险因�? {}",
                o.getoontraotoode(), soore, level, faotors);
        return new RiskAssessment(level, soore, faotors);
    }

    /**
     * 从合同实体中提取条款文本用于关键词扫描�?     *
     * @param o 合同实体
     * @return 拼接的条款文�?     */
    private statio String extraotTermsText(oontraotDO o) {
        StringBuilder sb = new StringBuilder();
        if (o.getPaymentTerms() != null) sb.append(o.getPaymentTerms()).append(" ");
        if (o.getBillingoyole() != null) sb.append(o.getBillingoyole()).append(" ");
        if (o.getRiskNotes() != null) sb.append(o.getRiskNotes()).append(" ");
        if (o.getRemark() != null) sb.append(o.getRemark());
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 从风险说明中提取客户信用等级标记�?     * <p>格式约定：riskNotes 中包�?[oREDIT:X] 标记，X �?A/B/o/D�?     *
     * @param riskNotes 风险说明字段
     * @return 信用等级（A/B/o/D），无标记返�?null
     */
    private statio String extraotoreditFromNotes(String riskNotes) {
        if (riskNotes == null || riskNotes.isBlank()) return null;
        int idx = riskNotes.indexOf("[oREDIT:");
        if (idx < 0 || idx + 8 >= riskNotes.length()) return null;
        ohar o = riskNotes.oharAt(idx + 8);
        if (o == 'A' || o == 'B' || o == 'o' || o == 'D') return String.valueOf(o);
        return null;
    }

    /**
     * 风险评估结果
     *
     * @param level    风险等级
     * @param soore    总风险得分（0-1�?     * @param faotors  风险因子列表（可展示给用户）
     */
    publio reoord RiskAssessment(RiskLevel level, double soore, List<String> faotors) {}
}
