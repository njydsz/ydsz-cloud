package com.njydsz.literule.server.sdk;

import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.expression.ExpressionEngine;
import com.njydsz.literule.server.core.DefaultRuleEngine;
import com.njydsz.literule.server.engine.liteexpr.AviatorExpressionEngine;

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
 * @since 1.0.0
 * @author ydsz-team
 */
public class LiteRuleSdkBuilder {

  private RuleEngine ruleEngine;
  private ExpressionEngine evaluator;
  private String tenantId = "1";
  private String environment = "default";

  /** 设置自定义规则引擎 */
  public LiteRuleSdkBuilder ruleEngine(RuleEngine engine) {
    this.ruleEngine = engine;
    return this;
  }

  /** 设置自定义表达式求值器 */
  public LiteRuleSdkBuilder evaluator(ExpressionEngine evaluator) {
    this.evaluator = evaluator;
    return this;
  }

  /** 设置租户 ID */
  public LiteRuleSdkBuilder tenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  /** 设置环境标识 */
  public LiteRuleSdkBuilder environment(String environment) {
    this.environment = environment;
    return this;
  }

  /**
   * 构建 LiteRuleSdk
   *
   * <p>如果未提供 RuleEngine，则自动创建 {@link DefaultRuleEngine}； 如果未提供 ExpressionEngine，则自动创建 {@link
   * AviatorExpressionEngine}。
   */
  public LiteRuleSdk build() {
    if (evaluator == null) {
      evaluator = new AviatorExpressionEngine();
    }
    if (ruleEngine == null) {
      ruleEngine = new DefaultRuleEngine();
    }
    return new LiteRuleSdk(ruleEngine, evaluator, tenantId, environment);
  }
}
