package com.njydsz.pmis.agent.engine.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 评测报告（P4-8 落地）。
 *
 * <p>批量评测后的聚合报告，包含通过率、平均分、平均耗时等汇总指标。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-8)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所有用例的评估结果 */
    private List<EvaluationResult> results;

    /** 总用例数 */
    private int totalCases;

    /** 通过用例数 */
    private int passedCases;

    /** 失败用例数 */
    private int failedCases;

    /** 通过率（0.0 - 1.0） */
    private double passRate;

    /** 平均得分 */
    private double averageScore;

    /** 平均耗时（毫秒） */
    private double averageElapsedMs;

    /** 汇总摘要文本 */
    private String summary;

    /**
     * 返回空报告（无用例时使用）。
     *
     * @return 空报告
     */
    public static EvaluationReport empty() {
        return EvaluationReport.builder()
                .results(Collections.emptyList())
                .totalCases(0)
                .passedCases(0)
                .failedCases(0)
                .passRate(0.0)
                .averageScore(0.0)
                .averageElapsedMs(0.0)
                .summary("无用例")
                .build();
    }
}
