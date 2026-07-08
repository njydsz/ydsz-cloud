package com.njydsz.pmis.agent.engine.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评测结果（P4-8 落地）。
 *
 * <p>单个测试用例的评估结果，包含实际输出、得分、是否通过、耗时等信息。
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

    /** 得分（0.0 - 1.0） */
    private double score;

    /** 是否通过 */
    private boolean passed;

    /** 耗时（毫秒） */
    private long elapsedMs;

    /** 评估器类型 */
    private EvaluationCase.EvaluatorType evaluatorType;

    /** 错误信息（执行异常时填充） */
    private String errorMessage;
}
