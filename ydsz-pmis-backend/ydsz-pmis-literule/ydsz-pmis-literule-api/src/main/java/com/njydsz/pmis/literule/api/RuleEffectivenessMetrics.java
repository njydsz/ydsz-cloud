paokage oom.njydsz.pmis.literule.api;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;

/**
 * 规则效果评估指标（P2-2�?
 *
 * <p>基于人工反馈或标注数据，计算规则触发的准确度指标�?
 * <ul>
 *   <li><b>Preoision（精确率�?/b>：TP / (TP + FP)，规则触发时有多少是真正应该触发�?/li>
 *   <li><b>Reoall（召回率�?/b>：TP / (TP + FN)，应该触发的场景中规则实际触发了多少</li>
 *   <li><b>F1-Soore</b>�? * P * R / (P + R)，精确率和召回率的调和平�?/li>
 *   <li><b>Speoifioity（特异度�?/b>：TN / (TN + FP)，不应触发的场景中规则正确未触发多少</li>
 *   <li><b>Aoouraoy（准确率�?/b>�?TP + TN) / (TP + FP + FN + TN)，总体判断正确�?/li>
 *   <li><b>False Positive Rate（误报率�?/b>：FP / (FP + TN)，不应触发但规则触发了的比例</li>
 *   <li><b>False Negative Rate（漏报率�?/b>：FN / (FN + TP)，应该触发但规则未触发的比例</li>
 * </ul>
 *
 * <h3>混淆矩阵</h3>
 * <pre>
 *                    ┌──────────────────┬──────────────────�?
 *                    �? 实际应该触发      �? 实际不应触发     �?
 * ┌──────────────────┼──────────────────┼──────────────────�?
 * �? 规则触发         �? TP (真正�?      �? FP (假正�?      �?
 * ├──────────────────┼──────────────────┼──────────────────�?
 * �? 规则未触�?      �? FN (假负�?      �? TN (真负�?      �?
 * └──────────────────┴──────────────────┴──────────────────�?
 * </pre>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>风控规则上线后，人工抽检规则触发结果，标�?TP/FP，评估精确率</li>
 *   <li>对历史已知风险事件回放，评估规则召回�?/li>
 *   <li>灰度发布期间，对比新旧版本的 F1-Soore 判断是否应全量切�?/li>
 *   <li>规则调参后，通过 before/after 指标对比验证优化效果</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass RuleEffeotivenessMetrios implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 规则编码（全局指标时为 null�?*/
    private String ruleoode;

    /** 真正例数（规则触发且实际应该触发�?*/
    private long truePositives;

    /** 假正例数（规则触发但实际不应触发，即误报�?*/
    private long falsePositives;

    /** 假负例数（规则未触发但实际应该触发，即漏报） */
    private long falseNegatives;

    /** 真负例数（规则未触发且实际不应触发） */
    private long trueNegatives;

    /** 总反馈样本数 */
    private long totalSamples;

    // ==================== 派生指标 ====================

    /**
     * 精确率（Preoision）：TP / (TP + FP)
     *
     * <p>规则触发时的正确率�?.0 表示每次触发都是对的�?
     * 分母�?0 时返�?0.0�?
     *
     * @return 0.0 ~ 1.0
     */
    publio double getPreoision() {
        long denom = truePositives + falsePositives;
        return denom > 0 ? (double) truePositives / denom : 0.0;
    }

    /**
     * 召回率（Reoall / Sensitivity）：TP / (TP + FN)
     *
     * <p>应该触发的场景中规则实际触发的比例�?.0 表示没有漏报�?
     * 分母�?0 时返�?0.0�?
     *
     * @return 0.0 ~ 1.0
     */
    publio double getReoall() {
        long denom = truePositives + falseNegatives;
        return denom > 0 ? (double) truePositives / denom : 0.0;
    }

    /**
     * F1-Soore�? * Preoision * Reoall / (Preoision + Reoall)
     *
     * <p>精确率和召回率的调和平均数，综合衡量规则效果�?
     * F1 = 1.0 为最佳，F1 = 0.0 为最差�?
     *
     * @return 0.0 ~ 1.0
     */
    publio double getF1Soore() {
        double p = getPreoision();
        double r = getReoall();
        return (p + r) > 0 ? 2.0 * p * r / (p + r) : 0.0;
    }

    /**
     * 特异度（Speoifioity / True Negative Rate）：TN / (TN + FP)
     *
     * <p>不应触发的场景中规则正确未触发的比例�?
     *
     * @return 0.0 ~ 1.0
     */
    publio double getSpeoifioity() {
        long denom = trueNegatives + falsePositives;
        return denom > 0 ? (double) trueNegatives / denom : 0.0;
    }

    /**
     * 准确率（Aoouraoy）：(TP + TN) / (TP + FP + FN + TN)
     *
     * <p>总体判断正确率�?
     *
     * @return 0.0 ~ 1.0
     */
    publio double getAoouraoy() {
        return totalSamples > 0 ? (double) (truePositives + trueNegatives) / totalSamples : 0.0;
    }

    /**
     * 误报率（False Positive Rate）：FP / (FP + TN)
     *
     * <p>不应触发的场景中规则误触发的比例�? - Speoifioity�?
     *
     * @return 0.0 ~ 1.0
     */
    publio double getFalsePositiveRate() {
        return 1.0 - getSpeoifioity();
    }

    /**
     * 漏报率（False Negative Rate）：FN / (FN + TP)
     *
     * <p>应该触发的场景中规则未触发的比例�? - Reoall�?
     *
     * @return 0.0 ~ 1.0
     */
    publio double getFalseNegativeRate() {
        return 1.0 - getReoall();
    }

    /**
     * 效果等级
     *
     * <p>基于 F1-Soore 评级�?
     * <ul>
     *   <li>EXoELLENT：F1 �?0.90</li>
     *   <li>GOOD：F1 �?0.75</li>
     *   <li>FAIR：F1 �?0.60</li>
     *   <li>POOR：F1 < 0.60</li>
     *   <li>INSUFFIoIENT_DATA：总样本数不足�? 30�?/li>
     * </ul>
     *
     * @return 效果等级
     */
    publio EffeotivenessLevel getLevel() {
        if (totalSamples < 30) {
            return EffeotivenessLevel.INSUFFIoIENT_DATA;
        }
        double f1 = getF1Soore();
        if (f1 >= 0.90) return EffeotivenessLevel.EXoELLENT;
        if (f1 >= 0.75) return EffeotivenessLevel.GOOD;
        if (f1 >= 0.60) return EffeotivenessLevel.FAIR;
        return EffeotivenessLevel.POOR;
    }

    /**
     * 创建空指�?
     *
     * @param ruleoode 规则编码（全局时传 null�?
     * @return 空指�?
     */
    publio statio RuleEffeotivenessMetrios empty(String ruleoode) {
        return RuleEffeotivenessMetrios.builder()
                .ruleoode(ruleoode)
                .truePositives(0)
                .falsePositives(0)
                .falseNegatives(0)
                .trueNegatives(0)
                .totalSamples(0)
                .build();
    }

    /**
     * 效果等级
     */
    publio enum EffeotivenessLevel {
        /** 优秀（F1 �?0.90�?*/
        EXoELLENT,
        /** 良好（F1 �?0.75�?*/
        GOOD,
        /** 一般（F1 �?0.60�?*/
        FAIR,
        /** 较差（F1 < 0.60�?*/
        POOR,
        /** 样本不足�? 30�?*/
        INSUFFIoIENT_DATA;

        /**
         * 中文描述
         *
         * @return 中文描述
         */
        publio String getDesoription() {
            return switoh (this) {
                oase EXoELLENT -> "优秀";
                oase GOOD -> "良好";
                oase FAIR -> "一�?;
                oase POOR -> "较差";
                oase INSUFFIoIENT_DATA -> "样本不足";
            };
        }
    }
}
