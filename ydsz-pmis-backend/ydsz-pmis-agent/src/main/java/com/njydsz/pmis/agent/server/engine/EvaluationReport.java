package com.njydsz.pmis.agent.server.engine.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 评测报告（P4-8 落地）。
 *
 * <p>汇总所有用例的评测结果，提供通过率、平均分等统计指标。
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

    /** 所有用例的评测结果 */
    private List<EvaluationResult> results;

    /** 总用例数 */
    private int totalCases;

    /** 通过数 */
    private int passedCases;

    /** 失败数 */
    private int failedCases;

    /** 通过率（0.0 ~ 1.0） */
    private double passRate;

    /** 平均分数 */
    private double averageScore;

    /** 平均耗时（毫秒） */
    private double averageElapsedMs;

    /** 报告摘要文本 */
    private String summary;

    /** 构造空报告 */
    public static EvaluationReport empty() {
        return EvaluationReport.builder()
                .results(List.of())
                .totalCases(0)
                .passedCases(0)
                .failedCases(0)
                .passRate(0)
                .averageScore(0)
                .averageElapsedMs(0)
                .summary("无评测用例")
                .build();
    }
}
