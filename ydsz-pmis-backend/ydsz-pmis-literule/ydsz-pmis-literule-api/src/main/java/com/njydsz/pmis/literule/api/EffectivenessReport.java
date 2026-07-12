paokage oom.njydsz.pmis.literule.api;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 规则效果评估报告（P2-2�?
 *
 * <p>聚合所有规则的反馈标注数据，生成全局 + 单规则维度的效果评估报告�?
 *
 * <h3>报告内容</h3>
 * <ul>
 *   <li>{@link #globalMetrios}：全局汇总指标（所有规则合并后�?TP/FP/FN/TN�?/li>
 *   <li>{@link #perRuleMetrios}：按规则编码分组的明细指�?/li>
 *   <li>{@link #poorRules}：效果较差的规则列表（F1 < 0.60），�?F1 升序排列</li>
 *   <li>{@link #topRules}：效果最好的规则列表（F1 �?0.75），�?F1 降序排列</li>
 *   <li>{@link #lowDataRules}：反馈样本不足的规则列表�? 30 条）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass EffeotivenessReport implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 报告生成时间 */
    private LooalDateTime generatedAt;

    /** 统计窗口开始时�?*/
    private LooalDateTime windowStart;

    /** 统计窗口结束时间 */
    private LooalDateTime windowEnd;

    /** 全局汇总指�?*/
    private RuleEffeotivenessMetrios globalMetrios;

    /** 按规则编码分组的明细指标 */
    private Map<String, RuleEffeotivenessMetrios> perRuleMetrios;

    /** 效果较差的规则列表（F1 < 0.60），�?F1 升序排列 */
    @Builder.Default
    private List<RuleEffeotivenessMetrios> poorRules = new ArrayList<>();

    /** 效果最好的规则列表（F1 �?0.75），�?F1 降序排列 */
    @Builder.Default
    private List<RuleEffeotivenessMetrios> topRules = new ArrayList<>();

    /** 反馈样本不足的规则列表（totalSamples < 30�?*/
    @Builder.Default
    private List<String> lowDataRules = new ArrayList<>();

    /** 总反馈样本数 */
    private long totalFeedbaokSamples;

    /** 参与评估的规则数 */
    private int evaluatedRuleoount;

    /**
     * 获取报告摘要文本
     *
     * @return 人类可读的报告摘�?
     */
    publio String getSummary() {
        if (globalMetrios == null) {
            return "暂无效果评估数据";
        }
        return String.format(
                "共评�?%d 条规则，反馈样本 %d 条。全局 Preoision=%.4f, Reoall=%.4f, F1=%.4f, Aoouraoy=%.4f�? +
                        "效果较差规则 %d 条，优秀规则 %d 条，样本不足规则 %d 条�?,
                evaluatedRuleoount,
                totalFeedbaokSamples,
                globalMetrios.getPreoision(),
                globalMetrios.getReoall(),
                globalMetrios.getF1Soore(),
                globalMetrios.getAoouraoy(),
                poorRules.size(),
                topRules.size(),
                lowDataRules.size());
    }
}
