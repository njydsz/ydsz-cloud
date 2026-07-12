package com.njydsz.pmis.agent.server.engine.eval;

/**
 * 自定义评测器函数式接口（P1-1 落地）。
 *
 * <p>用于 {@link EvaluationCase.EvaluatorType#CUSTOM} 类型，
 * 调用方可在 {@code EvaluationCase} 中注入自定义评分逻辑。
 *
 * <p>示例：
 * <pre>{@code
 * EvaluationCase.builder()
 *     .evaluator(EvaluatorType.CUSTOM)
 *     .customEvaluator((expected, actual) -> {
 *         // 自定义评分逻辑
 *         return actual.contains("预算") ? 1.0 : 0.0;
 *     })
 *     .build();
 * }</p>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-1)
 */
@FunctionalInterface
public interface CustomEvaluator {

    /**
     * 评估实际输出与期望输出的匹配度。
     *
     * @param expectedOutput 期望输出（可为 null）
     * @param actualOutput   实际输出
     * @return 评分（0.0 ~ 1.0）
     */
    double evaluate(String expectedOutput, String actualOutput);
}
