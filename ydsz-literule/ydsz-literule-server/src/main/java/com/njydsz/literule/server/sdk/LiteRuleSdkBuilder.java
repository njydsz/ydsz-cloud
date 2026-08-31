package com.njydsz.literule.server.sdk;

import com.njydsz.literule.domain.api.RuleEngine;
import com.njydsz.literule.domain.api.expression.ExpressionEngine;
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
 * @since 1.0.0
 * @author ydsz-team
 */
public class LiteRuleSdkBuilder {

  private RuleEngine ruleEngine;
  private ExpressionEngine evaluator;
  private String tenantId = "1";
  private String environment = "default";

  /** 设置自定义规则引擎
   * @param engine 参数说明
   * @return 返回值说明
   */
  public LiteRuleSdkBuilder ruleEngine(RuleEngine engine) {
    this.ruleEngine = engine;
    return this;
  }

  /** 设置自定义表达式求值器
   * @param evaluator 参数说明
   * @return 返回值说明
   */
  public LiteRuleSdkBuilder evaluator(ExpressionEngine evaluator) {
    this.evaluator = evaluator;
    return this;
  }

  /** 设置租户 ID
   * @param tenantId 参数说明
   * @return 返回值说明
   */
  public LiteRuleSdkBuilder tenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  /** 设置环境标识
   * @param environment 参数说明
   * @return 返回值说明
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
      * @return 返回值说明
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
