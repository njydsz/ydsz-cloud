package com.njydsz.literule.server.sdk;

import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.server.core.DefaultRuleEngine;
import com.njydsz.literule.server.engine.liteexpr.LiteExprEngine;

/**
 * LiteRuleSdk 构建器
 *
 * <p>链式构建 {@link LiteRuleSdk}，支持嵌入式（无 Spring）和 Spring 集成两种模式。
 *
 * <h3>嵌入式快速构建</h3>
 *
 * <pre>{@code
 * LiteRuleSdk sdk = LiteRuleSdk.builder()
 *     .tenantId("T001")
 *     .environment("prod")
 *     .build();
 * }</pre>
 *
 * <h3>自定义引擎</h3>
 *
 * <pre>{@code
 * LiteRuleSdk sdk = LiteRuleSdk.builder()
 *     .ruleEngine(myEngine)
 *     .evaluator(myEvaluator)
 *     .build();
 * }</pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class LiteRuleSdkBuilder {

  private RuleEngine ruleEngine;
  private ExpressionEngine evaluator;
  private String tenantId = "1";
  private String environment = "default";

  /** 设置自定义规则引擎
   * @param engine 规则引擎实现
   * @return 当前构建器（链式调用）
   */
  public LiteRuleSdkBuilder ruleEngine(RuleEngine engine) {
    this.ruleEngine = engine;
    return this;
  }

  /** 设置自定义表达式求值器
   * @param evaluator 表达式求值器实现
   * @return 当前构建器（链式调用）
   */
  public LiteRuleSdkBuilder evaluator(ExpressionEngine evaluator) {
    this.evaluator = evaluator;
    return this;
  }

  /** 设置租户 ID
   * @param tenantId 租户唯一标识
   * @return 当前构建器（链式调用）
   */
  public LiteRuleSdkBuilder tenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  /** 设置环境标识
   * @param environment 运行环境标识（如 prod/test）
   * @return 当前构建器（链式调用）
   */
  public LiteRuleSdkBuilder environment(String environment) {
    this.environment = environment;
    return this;
  }

  /**
   * 构建 LiteRuleSdk
   *
   * <p>如果未提供 RuleEngine，则自动创建 {@link DefaultRuleEngine}； 如果未提供 ExpressionEngine，则自动创建 {@link
   * LiteExprEngine}。
   * @return 构建完成的 LiteRuleSdk 实例
   */
  public LiteRuleSdk build() {
    if (evaluator == null) {
      evaluator = new LiteExprEngine();
    }
    if (ruleEngine == null) {
      ruleEngine = new DefaultRuleEngine();
    }
    return new LiteRuleSdk(ruleEngine, evaluator, tenantId, environment);
  }
}
