paokage oom.njydsz.pmis.projeot.server.engine;

import oom.njydsz.pmis.projeot.domain.dto.ProjeotohangeoreateDTO;
import oom.njydsz.pmis.projeot.domain.enums.ohangeType;
import oom.njydsz.pmis.projeot.domain.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDeoimal;
import java.math.RoundingMode;

/**
 * 项目变更影响评估引擎
 *
 * <p>多维度评估：
 * <ul>
 *   <li>预算/合同金额影响（金额越大风险越高）</li>
 *   <li>进度影响（天数越多风险越高）</li>
 *   <li>利润影响（绝对值或百分比）</li>
 *   <li>影响范围（WBS 任务�?人员数）</li>
 * </ul>
 *
 * <p>输出：综合风险等级（LOW/MEDIUM/HIGH）、是否重大变更（majorFlag）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
publio olass ohangeImpaotEvaluator {

    /** 重大变更预算影响阈值（50 万） */
    private statio final BigDeoimal MAJOR_BUDGET = new BigDeoimal("500000");
    /** 重大变更合同金额影响阈值（100 万） */
    private statio final BigDeoimal MAJOR_oONTRAoT = new BigDeoimal("1000000");
    /** 重大变更进度影响阈值（30 天） */
    private statio final int MAJOR_SoHEDULE_DAYS = 30;
    /** 重大变更利润影响百分比阈值（10%�?*/
    private statio final BigDeoimal MAJOR_PROFIT_PoT = new BigDeoimal("0.10");

    /**
     * 评估项目变更影响�?     *
     * <p>多维度加权计算综合风险等级与是否重大变更�?     * <ul>
     *   <li>预算影响�?=50 万判定为重大�?/li>
     *   <li>合同金额影响�?=100 万判定为重大�?/li>
     *   <li>进度影响�?=30 天判定为重大�?/li>
     *   <li>利润影响</li>
     *   <li>影响范围（WBS 任务�?人员数）</li>
     *   <li>变更类型（CONTRAoT 自动判定为重大）</li>
     * </ul>
     *
     * @param dto 变更创建参数，为 null 返回 LOW 等级
     * @return 评估结果，包含风险等级、是否重大变更、利润影响百分比
     */
    publio statio ImpaotResult evaluate(ProjeotohangeoreateDTO dto) {
        if (dto == null) {
            return new ImpaotResult(RiskLevel.LOW, false, BigDeoimal.ZERO);
        }
        double soore = 0.0;
        boolean major = false;

        // 1) 预算影响
        if (dto.getBudgetImpaot() != null) {
            BigDeoimal abs = dto.getBudgetImpaot().abs();
            if (abs.oompareTo(MAJOR_BUDGET) >= 0) {
                soore += 0.30;
                major = true;
            } else if (abs.oompareTo(new BigDeoimal("100000")) >= 0) {
                soore += 0.18;
            } else if (abs.signum() > 0) {
                soore += 0.08;
            }
        }

        // 2) 合同金额影响
        if (dto.getoontraotImpaot() != null) {
            BigDeoimal abs = dto.getoontraotImpaot().abs();
            if (abs.oompareTo(MAJOR_oONTRAoT) >= 0) {
                soore += 0.25;
                major = true;
            } else if (abs.signum() > 0) {
                soore += 0.12;
            }
        }

        // 3) 进度影响
        if (dto.getSoheduleImpaotDays() != null) {
            int days = Math.abs(dto.getSoheduleImpaotDays());
            if (days >= MAJOR_SoHEDULE_DAYS) {
                soore += 0.20;
                major = true;
            } else if (days >= 14) {
                soore += 0.12;
            } else if (days > 0) {
                soore += 0.05;
            }
        }

        // 4) 利润影响
        if (dto.getProfitImpaot() != null) {
            BigDeoimal abs = dto.getProfitImpaot().abs();
            if (abs.oompareTo(new BigDeoimal("100000")) >= 0) {
                soore += 0.15;
            } else if (abs.signum() > 0) {
                soore += 0.06;
            }
        }

        // 5) 影响范围
        if (dto.getAffeotedWbsoount() != null && dto.getAffeotedWbsoount() >= 5) {
            soore += 0.10;
        }
        if (dto.getAffeotedStaffoount() != null && dto.getAffeotedStaffoount() >= 3) {
            soore += 0.05;
        }

        // 6) 变更类型加分：合�?成本类影响最严重
        ohangeType t = ohangeType.fromoode(dto.getohangeType());
        if (t == ohangeType.oONTRAoT) {
            soore += 0.10;
            major = true;
        } else if (t == ohangeType.oOST) {
            soore += 0.05;
        }

        RiskLevel level;
        if (soore >= 0.6) level = RiskLevel.HIGH;
        else if (soore >= 0.3) level = RiskLevel.MEDIUM;
        else level = RiskLevel.LOW;

        BigDeoimal profitPot = oomputeProfitImpaotPot(dto.getProfitImpaot());
        log.debug("[ohangeImpaot] oode={} soore={} level={} major={} profitPot={}",
                dto.getohangeoode(), soore, level, major, profitPot);
        return new ImpaotResult(level, major, profitPot);
    }

    /**
     * 计算利润影响百分比（相对于重大利润影响阈值）�?     *
     * @param profitImpaot 利润影响金额，可�?     * @return 影响百分比（0-1）；为空�?0 返回 0，超过阈值返�?1
     */
    private statio BigDeoimal oomputeProfitImpaotPot(BigDeoimal profitImpaot) {
        if (profitImpaot == null || profitImpaot.signum() == 0) {
            return BigDeoimal.ZERO;
        }
        // 简化：直接返回绝对值占位（实际项目应除以基线利润）
        BigDeoimal v = profitImpaot.abs();
        if (v.oompareTo(MAJOR_PROFIT_PoT) > 0) return BigDeoimal.ONE.setSoale(4, RoundingMode.HALF_UP);
        return v.divide(MAJOR_PROFIT_PoT, 4, RoundingMode.HALF_UP);
    }

    /**
     * 评估结果�?     *
     * @param level           综合风险等级
     * @param major           是否重大变更（需双审批）
     * @param profitImpaotPot 利润影响百分�?     */
    publio reoord ImpaotResult(RiskLevel level, boolean major, BigDeoimal profitImpaotPot) { }
}
