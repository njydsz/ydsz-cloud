package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.enums.closure.ClosureType;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目结项准入校验器
 *
 * <p>正式结项（FORMAL）准入：
 * <ul>
 *   <li>回款比例 ≥ 95%</li>
 *   <li>CPI ≥ 0.95</li>
 *   <li>进度 ≥ 100%</li>
 *   <li>成本归集完成（无悬挂的 WBS 任务/工时）</li>
 *   <li>所有交付物验收通过</li>
 * </ul>
 *
 * <p>预结项（PRE_CLOSURE）准入：
 * <ul>
 *   <li>交付物验收通过</li>
 *   <li>进度 ≥ 80%</li>
 *   <li>CPI ≥ 0.85</li>
 *   <li>回款比例 ≥ 60%</li>
 * </ul>
 *
 * <p>强制结项（FORCED）：无强制准入（需走特批流程）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class ClosureAdmissionValidator {

    /** 正式结项回款比例阈值 */
    private static final BigDecimal FORMAL_RECEIVED_RATIO = new BigDecimal("0.95");
    /** 正式结项 CPI 阈值 */
    private static final BigDecimal FORMAL_CPI = new BigDecimal("0.95");
    /** 正式结项进度阈值 */
    private static final BigDecimal FORMAL_PROGRESS = new BigDecimal("100");

    /** 预结项回款比例阈值 */
    private static final BigDecimal PRE_RECEIVED_RATIO = new BigDecimal("0.60");
    /** 预结项 CPI 阈值 */
    private static final BigDecimal PRE_CPI = new BigDecimal("0.85");
    /** 预结项进度阈值 */
    private static final BigDecimal PRE_PROGRESS = new BigDecimal("80");

    /**
     * 校验项目结项准入
     *
     * @param type 结项类型
     * @param m    准入指标
     * @return 校验结果
     */
    public static AdmissionCheck check(ClosureType type, ClosureMetrics m) {
        if (type == null) {
            return AdmissionCheck.fail(List.of("结项类型不能为空"));
        }
        if (m == null) {
            return AdmissionCheck.fail(List.of("准入指标不能为空"));
        }
        if (type == ClosureType.FORCED) {
            return AdmissionCheck.ok(List.of("FORCED 类型不校验准入指标"), true);
        }
        List<String> fails = new ArrayList<>();

        BigDecimal receivedRatio = m.receivedRatio();
        BigDecimal cpi = m.cpi();
        BigDecimal progress = m.progressPct();
        BigDecimal grossMargin = m.grossMargin();
        BigDecimal totalCost = m.totalCost();
        boolean allDeliveredAccepted = m.allDeliveredAccepted();
        boolean costClosed = m.costClosed();

        if (type == ClosureType.FORMAL) {
            if (receivedRatio == null || receivedRatio.compareTo(FORMAL_RECEIVED_RATIO) < 0) {
                fails.add("回款比例 " + fmt(receivedRatio) + " < 95%");
            }
            if (cpi == null || cpi.compareTo(FORMAL_CPI) < 0) {
                fails.add("CPI " + fmt(cpi) + " < 0.95");
            }
            if (progress == null || progress.compareTo(FORMAL_PROGRESS) < 0) {
                fails.add("进度 " + fmt(progress) + "% < 100%");
            }
            if (grossMargin != null && grossMargin.signum() < 0) {
                fails.add("毛利率为负: " + fmt(grossMargin));
            }
            if (totalCost == null) {
                fails.add("成本归集未完成");
            } else if (Boolean.FALSE.equals(costClosed)) {
                fails.add("仍有未关闭的成本归集项");
            }
            if (Boolean.FALSE.equals(allDeliveredAccepted)) {
                fails.add("仍有交付物未验收");
            }
        } else if (type == ClosureType.PRE_CLOSURE) {
            if (receivedRatio == null || receivedRatio.compareTo(PRE_RECEIVED_RATIO) < 0) {
                fails.add("回款比例 " + fmt(receivedRatio) + " < 60%");
            }
            if (cpi == null || cpi.compareTo(PRE_CPI) < 0) {
                fails.add("CPI " + fmt(cpi) + " < 0.85");
            }
            if (progress == null || progress.compareTo(PRE_PROGRESS) < 0) {
                fails.add("进度 " + fmt(progress) + "% < 80%");
            }
            if (Boolean.FALSE.equals(allDeliveredAccepted)) {
                fails.add("仍有交付物未验收");
            }
        }
        if (fails.isEmpty()) {
            // 准入通过时生成经验教训检查清单
            List<String> lessons = generateLessonsLearned(type, m);
            log.info("[ClosureAdmission] {} 准入通过，经验教训: {}", type, lessons);
            return AdmissionCheck.ok(lessons, false);
        }
        log.info("[ClosureAdmission] {} 准入失败: {}", type, fails);
        return AdmissionCheck.fail(fails);
    }

    /**
     * BigDecimal 格式化
     *
     * @param v 数值
     * @return 格式化字符串；null 返回 "N/A"
     */
    private static String fmt(BigDecimal v) {
        if (v == null) return "N/A";
        return v.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 结项通过时生成经验教训检查清单。
     * <p>根据结项指标自动识别项目执行中的亮点和待改进点，
     * 供结项复盘参考。
     *
     * @param type 结项类型
     * @param m    准入指标
     * @return 经验教训列表
     */
    private static List<String> generateLessonsLearned(ClosureType type, ClosureMetrics m) {
        List<String> lessons = new ArrayList<>();
        lessons.add("准入通过");

        // CPI 分析
        if (m.cpi() != null) {
            if (m.cpi().compareTo(new BigDecimal("1.05")) >= 0) {
                lessons.add("亮点：CPI=" + fmt(m.cpi()) + "，成本控制优秀，建议总结成本管理经验");
            } else if (m.cpi().compareTo(new BigDecimal("0.95")) < 0) {
                lessons.add("待改进：CPI=" + fmt(m.cpi()) + "，成本略有超支，建议分析超支原因");
            }
        }

        // 回款分析
        if (m.receivedRatio() != null) {
            if (m.receivedRatio().compareTo(new BigDecimal("1.0")) >= 0) {
                lessons.add("亮点：回款比例100%，客户付款及时");
            } else if (m.receivedRatio().compareTo(new BigDecimal("0.95")) < 0) {
                lessons.add("待改进：回款比例" + fmt(m.receivedRatio()) + "，建议加强回款管理");
            }
        }

        // 毛利率分析
        if (m.grossMargin() != null) {
            if (m.grossMargin().compareTo(new BigDecimal("0.30")) >= 0) {
                lessons.add("亮点：毛利率" + fmt(m.grossMargin()) + "，盈利能力良好");
            } else if (m.grossMargin().signum() < 0) {
                lessons.add("待改进：毛利率为负，需深入分析亏损原因并制定改进措施");
            } else if (m.grossMargin().compareTo(new BigDecimal("0.10")) < 0) {
                lessons.add("待改进：毛利率较低(" + fmt(m.grossMargin()) + ")，建议优化报价策略");
            }
        }

        // 交付验收分析
        if (Boolean.TRUE.equals(m.allDeliveredAccepted())) {
            lessons.add("亮点：所有交付物均已验收通过");
        }

        return lessons;
    }

    /**
     * 准入指标
     */
    public record ClosureMetrics(BigDecimal receivedRatio, BigDecimal cpi, BigDecimal progressPct,
                                 BigDecimal grossMargin, BigDecimal totalCost,
                                 boolean costClosed, boolean allDeliveredAccepted) { }

    /**
     * 校验结果
     */
    public record AdmissionCheck(boolean passed, List<String> messages, boolean specialApprovalRequired) {
        /**
         * 构造通过结果
         *
         * @param msgs    描述信息
         * @param special 是否需特批
         * @return 通过结果
         */
        public static AdmissionCheck ok(List<String> msgs, boolean special) {
            return new AdmissionCheck(true, msgs, special);
        }
        /**
         * 构造失败结果
         *
         * @param msgs 失败原因列表
         * @return 失败结果
         */
        public static AdmissionCheck fail(List<String> msgs) {
            return new AdmissionCheck(false, msgs, false);
        }
    }
}
