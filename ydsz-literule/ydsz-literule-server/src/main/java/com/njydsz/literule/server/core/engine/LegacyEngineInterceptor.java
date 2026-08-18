package com.njydsz.literule.server.core.engine;

import java.util.List;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.RuleEngine;

/**
 * 传统引擎适配拦截器（P0-3 拦截器链 - 核心求值步骤）
 *
 * <p>在拦截器链的最后一步，调用 {@link RuleEngine}（即 DefaultRuleEngine）执行实际评估。 这是"链的末端"，不再调用 proceed()。
 *
 * <p>order = 1000（最后执行，前面的拦截器已处理缓存/注入/熔断/超时/指标等）
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class LegacyEngineInterceptor implements EvaluationInterceptor {

  private final RuleEngine delegateEngine;

  public LegacyEngineInterceptor(RuleEngine delegateEngine) {
    this.delegateEngine = delegateEngine;
  }

  @Override
  public List<RuleResult> intercept(EvaluationContext context) throws Exception {
    // 链的末端：直接执行评估，不再调用 proceed()
    RuleContext ruleContext = context.getRuleContext();
    return delegateEngine.evaluate(ruleContext);
  }

  @Override
  public int order() {
    return 1000;
  }

  @Override
  public String name() {
    return "LegacyEngineInterceptor";
  }
}
