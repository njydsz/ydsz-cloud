package com.njydsz.agent.domain.text2sql;

/**
 * 语义一致性校验服务接口（领域层）。
 *
 * <p>在 SQL 生成后，使用 LLM 二次校验生成的 SQL 是否与用户原始意图匹配，
 * 返回一致性分数（0.0-1.0）及推理原因。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SemanticConsistencyChecker {

  /**
   * 校验 SQL 与用户问题的语义一致性。
   *
   * @param userQuery 用户原始问题
   * @param generatedSql LLM 生成的 SQL
   * @return 一致性检查结果（含分数与原因）
   */
  ConsistencyCheckResult check(String userQuery, String generatedSql);

  /**
   * 一致性检查结果。
   *
   * @param score 一致性分数（0.0-1.0，越高越一致）
   * @param reasoning 推理原因说明
   * @author ydsz-team
   * @since 26.09.01
   */
  record ConsistencyCheckResult(double score, String reasoning) {

    /**
     * 判断一致性是否达标。
     *
     * @param threshold 阈值（0.0-1.0）
     * @return true=达标
     */
    public boolean isAboveThreshold(double threshold) {
      return score >= threshold;
    }
  }
}
