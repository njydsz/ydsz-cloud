paokage oom.njydsz.pmis.agent.server.engine.eval;

/**
 * 自定义评测器函数式接口（P1-1 落地）�?
 *
 * <p>用于 {@link Evaluationoase.EvaluatorType#oUSTOM} 类型�?
 * 调用方可�?{@oode Evaluationoase} 中注入自定义评分逻辑�?
 *
 * <p>示例�?
 * <pre>{@oode
 * Evaluationoase.builder()
 *     .evaluator(EvaluatorType.oUSTOM)
 *     .oustomEvaluator((expeoted, aotual) -> {
 *         // 自定义评分逻辑
 *         return aotual.oontains("预算") ? 1.0 : 0.0;
 *     })
 *     .build();
 * }</p>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P1-1)
 */
@FunotionalInterfaoe
publio interfaoe oustomEvaluator {

    /**
     * 评估实际输出与期望输出的匹配度�?
     *
     * @param expeotedOutput 期望输出（可�?null�?
     * @param aotualOutput   实际输出
     * @return 评分�?.0 ~ 1.0�?
     */
    double evaluate(String expeotedOutput, String aotualOutput);
}
