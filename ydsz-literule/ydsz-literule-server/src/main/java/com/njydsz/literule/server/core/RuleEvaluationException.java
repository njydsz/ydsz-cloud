package com.njydsz.literule.server.core;

import com.njydsz.common.exception.custom.SysException;

/**
 * 规则评估异常（P2-1：统一异常封装 + 消息模板化）
 *
 * <p>替代规则引擎内部散落的 {@code RuntimeException} 和原始 {@code Exception}，提供结构化的错误分类和可读的消息模板。
 *
 * <h3>错误码定义</h3>
 *
 * <ul>
 *   <li>{@code RULE_TIMEOUT} — 规则评估超时
 *   <li>{@code RULE_EXPRESSION_ERROR} — 表达式编译/求值失败
 *   <li>{@code RULE_CIRCUIT_OPEN} — 规则被熔断器跳过
 *   <li>{@code RULE_EVALUATION_ERROR} — 规则执行异常
 * </ul>
 *
 * <h3>消息模板</h3>
 *
 * <p>使用 {@link String#format(String, Object...)} 格式化模板，参数按占位符顺序传入：
 *
 * <pre>
 * throw RuleEvaluationException.timeout("RULE_A", 500);
 * // → "规则 [RULE_A] 评估超时（限制 500ms）"
 *
 * throw RuleEvaluationException.expressionError("RULE_B", "amount >", "unexpected token");
 * // → "规则 [RULE_B] 表达式编译失败：'amount >'，原因：unexpected token"
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class RuleEvaluationException extends SysException {

  private static final long serialVersionUID = 1L;

  /** 错误码：规则评估超时 */
  public static final String CODE_TIMEOUT = "RULE_TIMEOUT";

  /** 错误码：表达式编译/求值失败 */
  public static final String CODE_EXPRESSION_ERROR = "RULE_EXPRESSION_ERROR";

  /** 错误码：规则被熔断器跳过 */
  public static final String CODE_CIRCUIT_OPEN = "RULE_CIRCUIT_OPEN";

  /** 错误码：规则执行异常 */
  public static final String CODE_EVALUATION_ERROR = "RULE_EVALUATION_ERROR";

  /** 规则编码 */
  private final String ruleCode;

  /** 错误码 */
  private final String errorCode;

  /**
   * 构造规则评估异常
   *
   * @param errorCode 错误码（{@code CODE_*} 常量）
   * @param ruleCode 规则编码
   * @param message 异常消息
   */
  public RuleEvaluationException(String errorCode, String ruleCode, String message) {
    super();
    this.errorCode = errorCode;
    this.ruleCode = ruleCode;
    setMessage(message);
  }

  /**
   * 构造规则评估异常（带原因）
   *
   * @param errorCode 错误码
   * @param ruleCode 规则编码
   * @param message 异常消息
   * @param cause 原始异常
   */
  public RuleEvaluationException(
      String errorCode, String ruleCode, String message, Throwable cause) {
    super();
    this.errorCode = errorCode;
    this.ruleCode = ruleCode;
    setMessage(message);
    initCause(cause);
  }

  public String getRuleCode() {
    return ruleCode;
  }

  public String getErrorCode() {
    return errorCode;
  }

  // ===== 工厂方法（消息模板化）=====

  /**
   * 规则评估超时
   *
   * @param ruleCode 规则编码
   * @param timeoutMs 超时限制（毫秒）
   * @return 异常实例
   */
  public static RuleEvaluationException timeout(String ruleCode, long timeoutMs) {
    return new RuleEvaluationException(
        CODE_TIMEOUT, ruleCode, String.format("规则 [%s] 评估超时（限制 %dms）", ruleCode, timeoutMs));
  }

  /**
   * 表达式编译失败
   *
   * @param ruleCode 规则编码
   * @param expression 表达式文本
   * @param reason 失败原因
   * @return 异常实例
   */
  public static RuleEvaluationException expressionError(
      String ruleCode, String expression, String reason) {
    return new RuleEvaluationException(
        CODE_EXPRESSION_ERROR,
        ruleCode,
        String.format("规则 [%s] 表达式编译失败：'%s'，原因：%s", ruleCode, expression, reason));
  }

  /**
   * 表达式求值失败（运行时）
   *
   * @param ruleCode 规则编码
   * @param expression 表达式文本
   * @param cause 原始异常
   * @return 异常实例
   */
  public static RuleEvaluationException evaluationError(
      String ruleCode, String expression, Throwable cause) {
    return new RuleEvaluationException(
        CODE_EVALUATION_ERROR,
        ruleCode,
        String.format("规则 [%s] 表达式求值失败：'%s'", ruleCode, expression),
        cause);
  }

  /**
   * 规则被熔断器跳过
   *
   * @param ruleCode 规则编码
   * @return 异常实例
   */
  public static RuleEvaluationException circuitOpen(String ruleCode) {
    return new RuleEvaluationException(
        CODE_CIRCUIT_OPEN, ruleCode, String.format("规则 [%s] 已被熔断器跳过", ruleCode));
  }

  /**
   * 通用评估异常
   *
   * @param ruleCode 规则编码
   * @param cause 原始异常
   * @return 异常实例
   */
  public static RuleEvaluationException evaluationError(String ruleCode, Throwable cause) {
    return new RuleEvaluationException(
        CODE_EVALUATION_ERROR,
        ruleCode,
        String.format("规则 [%s] 评估异常：%s", ruleCode, cause.getMessage()),
        cause);
  }
}
