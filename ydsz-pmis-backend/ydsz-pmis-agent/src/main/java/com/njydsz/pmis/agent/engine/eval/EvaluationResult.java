package com.njydsz.pmis.agent.engine.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单个评测用例的执行结果（P4-8 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-8)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用例 ID */
    private String caseId;

    /** 用户输入 */
    private String userInput;

    /** 期望输出 */
    private String expectedOutput;

    /** 实际输出 */
    private String actualOutput;

    /** 评估分数（0.0 ~ 1.0） */
    private double score;

    /** 是否通过（score >= passThreshold） */
    private boolean passed;

    /** 耗时（毫秒） */
    private long elapsedMs;

    /** 使用的评估器类型 */
    private EvaluationCase.EvaluatorType evaluatorType;

    /** 错误信息（执行异常时填充） */
    private String errorMessage;
}
