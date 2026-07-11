package com.njydsz.pmis.literule.server.adaptive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 定时分析结果摘要（2.0.0 自适应阈值闭环）
 *
 * <p>记录一次定时分析任务的执行结果统计。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledAnalysisResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分析的规则总数 */
    private int totalRulesAnalyzed;

    /** 生成的建议总数 */
    private int totalSuggestions;

    /** 自动应用成功的数量 */
    private int autoApplied;

    /** 自动应用失败的数量 */
    private int autoApplyFailed;

    /** 效果追踪检查的数量 */
    private int effectsTracked;

    @Override
    public String toString() {
        return String.format(
                "ScheduledAnalysisResult{rules=%d, suggestions=%d, applied=%d, failed=%d, tracked=%d}",
                totalRulesAnalyzed, totalSuggestions, autoApplied, autoApplyFailed, effectsTracked);
    }
}
