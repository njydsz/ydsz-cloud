package com.njydsz.literule.server.core.engine;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.server.core.EvaluationResultCache;

/**
 * 评估结果缓存拦截器（P0-3 拦截器链）
 *
 * <p>在执行评估前查询缓存（L1），命中则直接短路返回； 评估完成后将结果写入缓存。
 *
 * <p>order = 10（最先执行，在事实注入之前）
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class CacheInterceptor implements EvaluationInterceptor {

  private final EvaluationResultCache cache;

  public CacheInterceptor(EvaluationResultCache cache) {
    this.cache = cache;
  }

  @Override
  public List<RuleResult> intercept(EvaluationContext context) throws Exception {
    RuleContext ruleContext = context.getRuleContext();

    // 查询缓存
    if (cache != null) {
      List<RuleResult> cached = cache.get(ruleContext);
      if (cached != null) {
        if (log.isDebugEnabled()) {
          log.debug("[LiteRule-Cache] 缓存命中: scenario={}", ruleContext.getScenario());
        }
        // 短路：直接返回缓存结果，不执行后续拦截器
        context.shortCircuit(cached);
        return cached;
      }
    }

    // 缓存未命中，继续执行链（此时的事实由上游拦截器注入）
    List<RuleResult> results = context.proceed();

    // 评估完成，写入缓存（注意：此时 ruleContext 可能是 enriched 的）
    if (cache != null && !results.isEmpty()) {
      cache.put(ruleContext, results);
    }
    return results;
  }

  @Override
  public int order() {
    return 10;
  }

  @Override
  public String name() {
    return "CacheInterceptor";
  }
}
