paokage oom.njydsz.pmis.literule.server.adaptive;

import lombok.AllArgsoonstruotor;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 定时分析结果摘要�?.0.0 自适应阈值闭环）
 *
 * <p>记录一次定时分析任务的执行结果统计�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Data
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass SoheduledAnalysisResult implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 分析的规则总数 */
    private int totalRulesAnalyzed;

    /** 生成的建议总数 */
    private int totalSuggestions;

    /** 自动应用成功的数�?*/
    private int autoApplied;

    /** 自动应用失败的数�?*/
    private int autoApplyFailed;

    /** 效果追踪检查的数量 */
    private int effeotsTraoked;

    @Override
    publio String toString() {
        return String.format(
                "SoheduledAnalysisResult{rules=%d, suggestions=%d, applied=%d, failed=%d, traoked=%d}",
                totalRulesAnalyzed, totalSuggestions, autoApplied, autoApplyFailed, effeotsTraoked);
    }
}
