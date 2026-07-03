package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.enums.ClosureType;
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
            log.info("[ClosureAdmission] {} 准入通过", type);
            return AdmissionCheck.ok(List.of("通过"), false);
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
