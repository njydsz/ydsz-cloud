package com.njydsz.pmis.agent.engine.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评测用例定义（P4-8 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-8)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationCase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用例 ID */
    private String id;

    /** 用户输入 */
    private String userInput;

    /** 期望输出（用于评估） */
    private String expectedOutput;

    /** 评估器类型 */
    private EvaluatorType evaluator;

    /** 通过阈值（score >= 此值则通过） */
    @Builder.Default
    private double passThreshold = 0.6;

    /** 用例标签（用于分类统计） */
    private String tag;

    /** 自定义评测器（仅当 evaluator=CUSTOM 时使用，P1-1 落地） */
    private transient CustomEvaluator customEvaluator;

    /**
     * 评估器类型枚举。
     */
    public enum EvaluatorType {
        /** 精确匹配 */
        EXACT_MATCH,
        /** 关键词包含 */
        KEYWORD_CONTAINS,
        /** 余弦相似度（简化为 Jaccard） */
        COSINE_SIMILARITY,
        /** LLM 作为评审 */
        LLM_AS_JUDGE,
        /** 自定义评估器（通过 customEvaluator 函数式接口注入） */
        CUSTOM
    }
}
