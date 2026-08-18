package com.njydsz.literule.server.core.engine;

import com.njydsz.literule.server.core.DefaultRuleEngine;
import com.njydsz.literule.server.core.EvaluationResultCache;

/**
 * 引擎拦截器链构建器（P0-3 拦截器链模式 - 构建入口）
 *
 * <p>根据 {@link DefaultRuleEngine} 的配置自动组装拦截器链，使引擎核心从"上帝类"转变为可插拔的拦截器组合。
 *
 * <h3>构建的拦截器链</h3>
 *
 * <ol>
 *   <li>{@code CacheInterceptor}（order=10）— 评估结果缓存
 *   <li>{@code LegacyEngineInterceptor}（order=1000）— 核心求值引擎（委托 DefaultRuleEngine）
 * </ol>
 *
 * <p>后续可添加更多拦截器（熔断、超时、指标、追踪）而不修改引擎核心代码。
 *
 * <h3>使用方式</h3>
 *
 * <pre>
 * InterceptorChain chain = EngineInterceptorChainBuilder.from(defaultEngine)
 *     .withCache(cache)
 *     .build();
 *
 * // 通过链执行评估（替代直接调用 engine.evaluate）
 * EvaluationContext ctx = new EvaluationContext(ruleContext);
 * List&lt;RuleResult&gt; results = chain.execute(ctx);
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class EngineInterceptorChainBuilder {

  private final DefaultRuleEngine engine;
  private EvaluationResultCache cache;

  private EngineInterceptorChainBuilder(DefaultRuleEngine engine) {
    this.engine = engine;
  }

  /**
   * 从 DefaultRuleEngine 创建构建器
   *
   * @param engine 默认规则引擎
   * @return 构建器
   */
  public static EngineInterceptorChainBuilder from(DefaultRuleEngine engine) {
    return new EngineInterceptorChainBuilder(engine);
  }

  /**
   * 添加缓存拦截器
   *
   * @param cache 评估结果缓存
   * @return this
   */
  public EngineInterceptorChainBuilder withCache(EvaluationResultCache cache) {
    this.cache = cache;
    return this;
  }

  /**
   * 从引擎的 evaluationResultCache 字段自动配置缓存拦截器
   *
   * @return this
   */
  public EngineInterceptorChainBuilder withEngineCache() {
    this.cache = engine.getEvaluationResultCache();
    return this;
  }

  /**
   * 构建拦截器链
   *
   * @return 配置好的拦截器链
   */
  public InterceptorChain build() {
    InterceptorChain chain = new InterceptorChain();
    // 缓存拦截器（最前）
    if (cache != null) {
      chain.addInterceptor(new CacheInterceptor(cache));
    }
    // 核心引擎拦截器（最后，委托 DefaultRuleEngine）
    chain.addInterceptor(new LegacyEngineInterceptor(engine));
    return chain;
  }
}
