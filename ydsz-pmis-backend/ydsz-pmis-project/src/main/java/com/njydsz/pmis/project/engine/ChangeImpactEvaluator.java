package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.dto.initiation.ProjectChangeCreateDTO;
import com.njydsz.pmis.project.enums.initiation.ChangeType;
import com.njydsz.pmis.project.enums.execution.RiskLevel;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 项目变更影响评估引擎
 *
 * <p>多维度评估：
 * <ul>
 *   <li>预算/合同金额影响（金额越大风险越高）</li>
 *   <li>进度影响（天数越多风险越高）</li>
 *   <li>利润影响（绝对值或百分比）</li>
 *   <li>影响范围（WBS 任务数/人员数）</li>
 * </ul>
 *
 * <p>输出：综合风险等级（LOW/MEDIUM/HIGH）、是否重大变更（majorFlag）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class ChangeImpactEvaluator {

    /** 重大变更预算影响阈值（50 万） */
    private static final BigDecimal MAJOR_BUDGET = new BigDecimal("500000");
    /** 重大变更合同金额影响阈值（100 万） */
    private static final BigDecimal MAJOR_CONTRACT = new BigDecimal("1000000");
    /** 重大变更进度影响阈值（30 天） */
    private static final int MAJOR_SCHEDULE_DAYS = 30;
    /** 重大变更利润影响百分比阈值（10%） */
    private static final BigDecimal MAJOR_PROFIT_PCT = new BigDecimal("0.10");

    /**
     * 评估项目变更影响。
     *
     * <p>多维度加权计算综合风险等级与是否重大变更：
     * <ul>
     *   <li>预算影响（>=50 万判定为重大）</li>
     *   <li>合同金额影响（>=100 万判定为重大）</li>
     *   <li>进度影响（>=30 天判定为重大）</li>
     *   <li>利润影响</li>
     *   <li>影响范围（WBS 任务数/人员数）</li>
     *   <li>变更类型（CONTRACT 自动判定为重大）</li>
     * </ul>
     *
     * @param dto 变更创建参数，为 null 返回 LOW 等级
     * @return 评估结果，包含风险等级、是否重大变更、利润影响百分比
     */
    public static ImpactResult evaluate(ProjectChangeCreateDTO dto) {
        if (dto == null) {
            return new ImpactResult(RiskLevel.LOW, false, BigDecimal.ZERO);
        }
        double score = 0.0;
        boolean major = false;

        // 1) 预算影响
        if (dto.getBudgetImpact() != null) {
            BigDecimal abs = dto.getBudgetImpact().abs();
            if (abs.compareTo(MAJOR_BUDGET) >= 0) {
                score += 0.30;
                major = true;
            } else if (abs.compareTo(new BigDecimal("100000")) >= 0) {
                score += 0.18;
            } else if (abs.signum() > 0) {
                score += 0.08;
            }
        }

        // 2) 合同金额影响
        if (dto.getContractImpact() != null) {
            BigDecimal abs = dto.getContractImpact().abs();
            if (abs.compareTo(MAJOR_CONTRACT) >= 0) {
                score += 0.25;
                major = true;
            } else if (abs.signum() > 0) {
                score += 0.12;
            }
        }

        // 3) 进度影响
        if (dto.getScheduleImpactDays() != null) {
            int days = Math.abs(dto.getScheduleImpactDays());
            if (days >= MAJOR_SCHEDULE_DAYS) {
                score += 0.20;
                major = true;
            } else if (days >= 14) {
                score += 0.12;
            } else if (days > 0) {
                score += 0.05;
            }
        }

        // 4) 利润影响
        if (dto.getProfitImpact() != null) {
            BigDecimal abs = dto.getProfitImpact().abs();
            if (abs.compareTo(new BigDecimal("100000")) >= 0) {
                score += 0.15;
            } else if (abs.signum() > 0) {
                score += 0.06;
            }
        }

        // 5) 影响范围
        if (dto.getAffectedWbsCount() != null && dto.getAffectedWbsCount() >= 5) {
            score += 0.10;
        }
        if (dto.getAffectedStaffCount() != null && dto.getAffectedStaffCount() >= 3) {
            score += 0.05;
        }

        // 6) 变更类型加分：合同/成本类影响最严重
        ChangeType t = ChangeType.fromCode(dto.getChangeType());
        if (t == ChangeType.CONTRACT) {
            score += 0.10;
            major = true;
        } else if (t == ChangeType.COST) {
            score += 0.05;
        }

        RiskLevel level;
        if (score >= 0.6) level = RiskLevel.HIGH;
        else if (score >= 0.3) level = RiskLevel.MEDIUM;
        else level = RiskLevel.LOW;

        BigDecimal profitPct = computeProfitImpactPct(dto.getProfitImpact());
        log.debug("[ChangeImpact] code={} score={} level={} major={} profitPct={}",
                dto.getChangeCode(), score, level, major, profitPct);
        return new ImpactResult(level, major, profitPct);
    }

    /**
     * 计算利润影响百分比（相对于重大利润影响阈值）。
     *
     * @param profitImpact 利润影响金额，可空
     * @return 影响百分比（0-1）；为空或 0 返回 0，超过阈值返回 1
     */
    private static BigDecimal computeProfitImpactPct(BigDecimal profitImpact) {
        if (profitImpact == null || profitImpact.signum() == 0) {
            return BigDecimal.ZERO;
        }
        // 简化：直接返回绝对值占位（实际项目应除以基线利润）
        BigDecimal v = profitImpact.abs();
        if (v.compareTo(MAJOR_PROFIT_PCT) > 0) return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        return v.divide(MAJOR_PROFIT_PCT, 4, RoundingMode.HALF_UP);
    }

    /**
     * 评估结果。
     *
     * @param level           综合风险等级
     * @param major           是否重大变更（需双审批）
     * @param profitImpactPct 利润影响百分比
     */
    public record ImpactResult(RiskLevel level, boolean major, BigDecimal profitImpactPct) { }
}
