package com.njydsz.literule.server.engine.liteexpr;

/**
 * LiteExpr 自定义函数接口
 *
 * <p>业务函数通过实现此接口注册到 {@link FunctionRegistry}， 在表达式执行时按名称查找并调用。
 *
 * <pre>
 * functionRegistry.register("riskLevel", args -> {
 *     double score = ((Number) args[0]).doubleValue();
 *     if (score > 80) return "HIGH";
 *     if (score > 50) return "MEDIUM";
 *     return "LOW";
 * });
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FunctionalInterface
public interface LiteExprFunction {

  /**
   * 执行函数
   *
   * @param args 参数数组（可能为空）
   * @return 函数返回值
   * @throws Exception 函数执行异常
   */
  Object call(Object... args) throws Exception;
}
